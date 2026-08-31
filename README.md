# Bank SMS Parser

A React Native (Android) app with a native Kotlin module that parses Indian bank
SMS messages into structured credit-card transaction data — conservatively,
config-driven, and with the parsing itself living entirely in Kotlin.

## 1. How to run

```sh
yarn install
yarn android
```

This builds the debug APK, installs it on a running emulator/device, and
launches it. Metro starts automatically if it isn't already running (or run
`yarn start` in a separate terminal first).

The screen calls the native `parseSms` method on mount against the 25 bundled
samples in `src/data/samples.json` and renders the summary + result list.

### Running the tests

Kotlin parser unit tests (JVM, no emulator needed):

```sh
yarn test:kotlin
# equivalent to: cd android && ./gradlew :app:testDebugUnitTest
```

JS/TS tests (typecheck + a render smoke test for `App.tsx`):

```sh
yarn test
yarn tsc --noEmit
```

## 2. Parsing architecture

All parsing, classification, extraction, and confidence scoring lives in
Kotlin under `android/app/src/main/java/com/banksmsparser/smsparser/`. React
Native never sees SMS text until the native module hands back finished
`ParsedResult` objects — it only renders them.

```
smsparser/
├── model/Models.kt          Decision, TransactionType, Transaction, ParsedResult
├── config/
│   ├── BankConfig.kt        issuer-bank patterns + fintech/co-branded fallback map
│   ├── ExclusionConfig.kt   ordered list of (regex-based) exclusion rules
│   └── PatternConfig.kt     amount/currency/date/card/merchant regex + helpers
├── SmsClassifier.kt         walks ExclusionConfig.rules, returns first match
├── SmsExtractor.kt          pure field extraction (amount, date, card, merchant)
├── ConfidenceScorer.kt      confidence model for the INCLUDE path
├── BankSmsParser.kt         orchestrator: the one entry point everything calls
├── SmsParserModule.kt       React Native bridge (ReactContextBaseJavaModule)
└── SmsParserPackage.kt      registers the module (added manually in
                              MainApplication.kt, since it isn't an npm package
                              autolinking can discover)
```

**Bridge.** `SmsParserModule.parseSms(samples: ReadableArray, promise: Promise)`
is the single exposed method. It converts the JS string array to a `List<String>`,
calls `BankSmsParser.parseAll`, and marshals each `ParsedResult` into a
`WritableMap`. `App.tsx` calls `parseSms(samples: string[]): Promise<ParsedResult[]>`
(`src/native/SmsParser.ts`) with the `text` field extracted from
`samples.json` — the RN side never touches parsing logic. New Architecture
(TurboModules) is enabled in this project, but the bridgeless runtime still
supports classic `ReactContextBaseJavaModule`s registered through a
`ReactPackage`, so no codegen/Fabric setup was needed for this scope.

**Exclusion vs. extraction are separate stages.** `BankSmsParser.parse()`
first runs the message through `SmsClassifier` (exclusion). Only a message
that matches *no* exclusion rule proceeds to `SmsExtractor` (field
extraction) and the INCLUDE gate. This separation is deliberate: exclusion
rules answer "should this even be considered," extraction answers "what does
it contain" — mixing them (e.g. trying to extract merchant/amount before
deciding relevance) is how false-positive spend records happen.

**Bank detection reads the SMS body, not the sender.** The provided samples
don't include a sender ID field at all, so this was body-only by necessity —
but it's also the right call for the actual requirement: `BankConfig.resolve()`
tries direct issuer-name patterns first (`"Federal Bank"`, `"HDFC"`, `"BOBCARD"`,
...), and only falls back to a fintech-brand → issuer map (`"Jupiter" → "Federal
Bank"`) when no direct pattern matches. A message naming both the issuer and a
fintech brand (e.g. sample 8: "Edge Federal Bank Credit Card ... Jupiter app")
always resolves to the issuer named in the body, never the brand.

**Config-driven, not hardcoded.** Every piece of domain knowledge — bank name
patterns, the fintech fallback map, exclusion-rule regexes and their reason
codes, currency markers, date formats, card/merchant patterns — lives in the
`config/` package as data (lists of `Regex`/data classes, or an ordered list of
small rule lambdas in `ExclusionConfig`). `SmsClassifier`, `SmsExtractor`, and
`BankSmsParser` never branch on sample-specific strings; they only walk these
lists. **To add a new bank:** append one `BankRule` to `BankConfig.banks` (or
one `FintechIssuerRule` for a co-branded program with no issuer name in body).
**To add a new exclusion category:** append one rule (a regex + reason code +
confidence) to `ExclusionConfig.rules`. No other file changes.

**Rule order encodes priority.** `ExclusionConfig.rules` is ordered from most
specific/unambiguous (OTP, DECLINED) to broadest catch-all (the generic
"money moved out of a bank account" check for SAVINGS_ACCOUNT/UPI_BANK_ACCOUNT).
`SmsClassifier` returns the *first* match. This means a message that could
plausibly match two categories reads as the more specific, more informative
one — e.g. a SIP debit that also technically "debits an A/c" is reported as
`INVESTMENT`, not the generic `SAVINGS_ACCOUNT`.

## 3. Confidence scoring

See `ConfidenceScorer.kt` for the INCLUDE-path model; exclusion confidences
are attached directly to each rule in `ExclusionConfig.kt`.

**Exclusions:** each exclusion rule carries its own confidence (0.88–0.97),
reflecting how unambiguous its keyword signal is. `OTP` (0.97) and `DEBIT_CARD`
(0.95) are near-certain — the trigger words basically only appear in that
context. `INSURANCE`/`OFFER` (0.88) are slightly lower since "premium" or
"cashback" are marginally more polysemous. The `MALFORMED_SMS`/`LOW_CONFIDENCE`
fallback (reached only when *no* exclusion rule matched and the INCLUDE gate
also failed) starts low: `0.1` for a short message with almost no usable
signal, or `0.15 + 0.08` per partially-present signal (amount / bank / the
word "card") for a longer but still ambiguous message, floored at `0.1` and
capped at `0.4` — deliberately low enough that no downstream consumer would
mistake it for a confident answer.

**Inclusions:** a message starts at `0.95` only once it has survived every
exclusion rule *and* had its amount, issuer bank, and the literal word "card"
found. From there it loses points for what it had to infer or couldn't find:
`-0.15` if the bank was resolved via the fintech-brand fallback map instead
of a direct issuer-name match (inferred, not stated), `-0.08` if no merchant
was extracted, `-0.05` if no card last-four, `-0.07` if no date — floored at
`0.4` so a genuine INCLUDE is never reported alongside near-zero confidence.

**Malformed handling.** A message is malformed (not just low-confidence) when
it's short (<40 chars) *and* has at most one of {amount, bank, "card"} —
e.g. sample 25 in the provided set, truncated mid-sentence at `"Spent Rs. 2,4"`.
Note this isn't a special-cased length check on that one sample: the amount
regex actually does extract a spurious `24` from `"2,4"`, but because no bank
name and no "card" keyword are present, the signal count stays at 1 and the
message correctly falls to `MALFORMED_SMS` at `0.1` rather than being guessed
at as a real transaction. A longer message with partial-but-insufficient
signal (e.g. mentions "card" and has an amount, but names an unconfigured
bank) is excluded as `LOW_CONFIDENCE` instead — it's not garbled, just not
identifiable enough to trust.

## 4. Samples I found genuinely ambiguous

- **Sample 3** (`ICICI Bank Acct XX123 debited ... credited to UPI/swiggy@hdfc/Payment`)
  — this is a UPI payment that happens to be *to* Swiggy, which is also a
  common credit-card merchant in sample 2. It would be easy to merchant-match
  this into looking like a card spend. The parser excludes it correctly
  (`UPI_BANK_ACCOUNT`) because it never gets far enough to look at merchant —
  the account/UPI exclusion check runs first and the message never mentions a
  card — but it's the sample most likely to trip up a merchant-first design.
- **Sample 9** (`BOBCARD One Credit Card`) — the SMS body never says "Bank of
  Baroda," only the product brand "BOBCARD." The assignment explicitly allows
  either "Bank of Baroda" or "BOBCARD" as the resolved name; I went with
  `"Bank of Baroda (BOBCARD)"` to keep both legible, but a real product would
  need to decide this canonicalization once and stick to it everywhere
  (dedup keys, display names, etc.).
- **Sample 18** (finance charge that also contains "GST" and "late payment on
  bill dated...") — this one can plausibly read as `BILL_DUE` (it mentions
  "bill") or `FEE_OR_CHARGE` (it's actually a finance charge). I initially had
  a `GST` keyword in the fee-detection regex and a same-sentence "no period"
  check for the bill/due proximity test — both were wrong: GST alone is too
  generic a signal to rely on, and amounts like `23,450.00` contain a literal
  `.` that broke the "no period between these two words" check. Both bugs
  only surfaced once I ran the Kotlin unit tests against the real sample
  text, not from reasoning about the regex in isolation.
- **Sample 21 vs. 19** (refund vs. bill payment, both phrased as "credited to
  / received towards your ... Card") — these are easy to conflate since both
  are money moving *toward* the card. The distinguishing signal is the verb
  ("Refund of..." vs. "Payment of... received towards...") and I made
  `cardPaymentReceived`'s regex require the literal words "payment"/"amount"
  so it wouldn't accidentally swallow "Refund of Rs 450...".
- **Sample 25** (truncated) — see the malformed-handling note above. The
  interesting edge case here isn't the truncation itself, it's that a naive
  amount regex *does* produce a plausible-looking number from garbled text,
  which is exactly the kind of "confidently wrong" output the assignment
  warns against — the fix was making the INCLUDE gate require bank + card-word
  signal too, not just an amount.

## 5. What I'd do differently with a full week

- **A real rule-engine/DSL instead of Kotlin lambdas.** `ExclusionConfig.rules`
  is config-driven in the sense that it's a flat, ordered, appendable list of
  independent rules with no cross-file logic changes needed to extend it —
  but it's still Kotlin code, not data. With more time I'd move it to a
  declarative format (JSON/YAML rule definitions loaded at startup, or at
  least a sealed rule-type hierarchy with named priorities) so non-engineers
  could tune thresholds and add banks without touching `.kt` files at all.
- **Smarter merchant extraction.** The current approach is anchor-regex +
  stop-word heuristics (`extractMerchant` in `SmsExtractor.kt`), which is
  fragile against SMS templates it hasn't seen. A trained lightweight NER
  model or a much larger anchor-phrase library (per-bank templates) would
  generalize far better to the hidden set's "different wording" than regex
  can.
- **Stronger, locale-aware date parsing.** Right now `SmsExtractor.extractDate`
  handles `dd-mm-yy(yy)`, `dd/mm/yy(yy)`, and `dd-MON-yy(yy)` — the formats
  actually seen in the sample set — and picks the *first* date match in the
  message as the transaction's own date, which is a heuristic that could
  break on templates that lead with a different date (e.g. a due-date-first
  phrasing). A proper solution would score candidate dates by their
  surrounding keywords rather than just position.
- **Broader test coverage, especially adversarial cases.** The current suite
  covers the 8 required categories plus a handful more and a full
  regression check against all 25 provided samples. I'd add property-style
  tests (fuzzing amount/date formats) and deliberately adversarial samples
  (a legitimate spend that happens to contain the word "offer" in a merchant
  name, e.g. "OFFER ZONE STORE") to pressure-test the exclusion regexes for
  false positives, which matter more here than false negatives per the
  spec's stated philosophy.
- **Telemetry for parser misses.** In production I'd want every EXCLUDE with
  confidence in a middle band (say 0.3–0.6) logged (locally, no raw SMS
  leaving device) so we could see which real-world messages the config
  doesn't yet have a confident answer for, and prioritize new rules by
  frequency rather than guessing.
- **Config management.** Bank/exclusion config currently ships inside the
  APK. A real product handling many banks over time would want this
  versioned and updatable independently of app releases (with strict local
  validation before any config is trusted), so a bank changing its SMS
  template doesn't require a store release to fix.

## 6. Production Android design note

Turning this prototype into a real on-device parser means replacing "parse a
bundled JSON file on mount" with "read the live SMS inbox and react to new
messages within a real-time budget," which changes almost every operational
assumption.

**Permissions.** `READ_SMS`/`RECEIVE_SMS` are "dangerous" runtime permissions
and, on Play, gated by the SMS/Call Log permissions policy — Google requires
the app be a default SMS or Assistant handler, or qualify for a narrow
allowed use-case, and undergo a Play Console permissions declaration + video
review. A generic "spend tracker" is a realistic rejection risk unless scoped
very tightly (e.g. becoming the default SMS handler is heavy-handed for this
use case). The safer path is `SMS Retriever API` for OTPs (not applicable
here) or, more realistically, accepting the policy friction and applying for
the exception with a narrow, clearly-scoped permission ask, defaulting to a
**manual/opt-in bank statement or forwarded-SMS import** for users on Play
builds that can't get the grant, and reserving live SMS reading for a
sideloaded/enterprise channel where policy doesn't apply. If permission is
denied or revoked, the app should degrade to that manual-import path rather
than silently doing nothing — and should re-check permission state on every
foreground, not just at install, since users can revoke it anytime from
Settings (Android also auto-resets unused runtime permissions after months of
inactivity).

**Incremental parsing & duplicate prevention.** On first grant, backfill via
`content://sms/inbox` filtered to bank sender patterns, bounded to a lookback
window (e.g. 90 days) to avoid a multi-thousand-row parse blocking the UI.
After that, only new messages should be processed — track the last-seen SMS
`_id`/timestamp per device in local storage (Room/SQLite) and query
incrementally, never re-scan the whole inbox. Deduplicate on a composite key
(sender + body hash + timestamp bucket), since some OEMs/dual-SIM
configurations can double-deliver a `SMS_RECEIVED` broadcast.

**Where the parser must run, and the 30-second budget.** A `BroadcastReceiver`
on `SMS_RECEIVED` is the only path that can plausibly fire within seconds of
arrival — it's woken by the system directly. It must do the actual
Kotlin parsing (this module, unchanged) synchronously in the receiver or hand
off immediately to an expedited `WorkManager` one-off job
(`setExpedited`/foreground service on API 31+) to build and post the
notification; it cannot defer to a periodic `WorkManager` job (minimum
15-minute floor) or a `ContentObserver`-triggered background sync, both of
which are batching-oriented and cannot reliably meet a 30-second SLA.
`ContentObserver` on the SMS provider is a reasonable *fallback/reconciliation*
signal (catches anything the broadcast missed) but not the primary path.
If the process is killed before the receiver/job completes, `WorkManager`'s
persisted work guarantees a retry on next process start — but the
notification will then be late, which the UX should account for (e.g. mark
it "processed while you were away" rather than pretending it was real-time).

**Indian OEM background restrictions.** Xiaomi/MIUI/HyperOS, Realme, OPPO/
ColorOS, Vivo/FuntouchOS, and OnePlus/OxygenOS all layer aggressive
battery-saving beyond stock Android Doze/App Standby — autostart
allowlists, per-app "background pop-up"/"background activity" toggles, and
manufacturer-specific process killers that can kill a receiver-hosting
process even with battery optimization nominally disabled. `BroadcastReceiver`
delivery itself is a Doze exception (foreground broadcasts still deliver),
but the *process* handling it can still be OEM-killed before the follow-up
notification work completes. The technical mitigation is requesting
`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, guiding users to each OEM's autostart
screen (there's no unified API — this has to be per-OEM deep links maintained
against manufacturer UI changes, à la the open-source "don't kill my app"
approach), and treating delivery as best-effort. The UX has to *detect and
explain* this rather than silently fail: track "last successfully processed
SMS timestamp" and, on a gap when the app is opened, show "we may have missed
transactions between X and Y — tap to allow background access" with a direct
link to that OEM's autostart/battery settings screen, plus the
`ContentObserver` reconciliation pass to backfill anything actually missed.

**Privacy.** All parsing must stay on-device — no raw SMS body should ever
leave the device (this prototype already respects that by construction, since
there's no network dependency in the parser). Only derived, minimal fields
(amount, bank, category, timestamp) should sync to any backend, and the raw
SMS should be retained locally only as long as needed for the detail view,
with a clear data-deletion path.

## 7. AI tool usage

I built this with Claude Code (Sonnet 5) doing the implementation directly —
architecture decisions, all Kotlin/TypeScript code, and the test suite — with
me reviewing and directing at each stage rather than accepting a single
end-to-end generation.

**What worked well:** asking for the full 25-sample distribution to be
verified against the assignment's own worked example ("Included: 7 Excluded:
18") caught two real bugs immediately — the `[^.]*` "same sentence" regex
check for `BILL_DUE`/`CARD_PAYMENT` silently broke on any amount containing a
decimal point (e.g. `23,450.00`), and merchant extraction was grabbing "your
HDFC Card xx5678" instead of "BIGBASKET" on the refund sample because the
first `to`/`from` anchor in the sentence wasn't the merchant. Both were found
by actually running the Kotlin unit tests against the literal sample text
and reading the JUnit failure output — not by asking the model to "check its
own work" abstractly.

**What I changed/verified myself:** the exclusion-rule *ordering* is a
judgment call the model proposed and I traced through by hand against all 25
samples before trusting it (e.g. confirming `INVESTMENT` should win over the
generic `SAVINGS_ACCOUNT` check for a SIP debit, and that `DECLINED` must be
checked before `BILL_DUE` since a declined-due-to-limit message contains both
words). I also manually drove the built APK on an emulator (`adb`
screenshots + taps) to confirm the summary numbers, row rendering, and both
detail-modal variants actually work, rather than trusting a green test suite
alone — the assignment explicitly separates "type-checks" from "confirmed
working in the real app," and I wanted the latter.

**What didn't work well initially:** the first draft of both aforementioned
regexes was written by reasoning about the pattern in isolation rather than
against real sample text, and looked correct on inspection — the bugs were
only visible once run against actual data with decimal amounts and
multi-anchor sentences.
