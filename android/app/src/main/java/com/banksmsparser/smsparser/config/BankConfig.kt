package com.banksmsparser.smsparser.config

/**
 * Issuer bank resolution is config-driven and body-first: the sender/app branding
 * (e.g. "Jupiter", "BOBCARD") never overrides an issuer name explicitly present in
 * the SMS body. To onboard a new bank, add a [BankRule] here - no parser code changes.
 */
data class BankRule(
  val canonicalName: String,
  val bodyPatterns: List<Regex>,
)

/**
 * Fallback map for co-branded / fintech card programs whose SMS body may only carry
 * the fintech brand name (no direct issuer-bank string). Used only when no [BankRule]
 * matches directly, and resolution via this map is treated as lower-confidence since
 * it is inferred rather than stated.
 */
data class FintechIssuerRule(
  val pattern: Regex,
  val issuerCanonicalName: String,
)

object BankConfig {
  // Order matters only in that more specific co-branded names are listed before
  // generic ones; each pattern is independently anchored so collisions are unlikely.
  val banks: List<BankRule> =
    listOf(
      BankRule("Bank of Baroda (BOBCARD)", listOf(Regex("bank\\s*of\\s*baroda", RegexOption.IGNORE_CASE), Regex("\\bBOBCARD\\b", RegexOption.IGNORE_CASE))),
      BankRule("Federal Bank", listOf(Regex("federal\\s*bank", RegexOption.IGNORE_CASE))),
      BankRule("HDFC Bank", listOf(Regex("HDFC", RegexOption.IGNORE_CASE))),
      BankRule("ICICI Bank", listOf(Regex("ICICI", RegexOption.IGNORE_CASE))),
      BankRule("Axis Bank", listOf(Regex("Axis\\s*Bank", RegexOption.IGNORE_CASE))),
      BankRule("Yes Bank", listOf(Regex("YES\\s*BANK", RegexOption.IGNORE_CASE))),
      BankRule("Kotak Mahindra Bank", listOf(Regex("Kotak", RegexOption.IGNORE_CASE))),
      BankRule("IDFC FIRST Bank", listOf(Regex("IDFC", RegexOption.IGNORE_CASE))),
      BankRule("RBL Bank", listOf(Regex("\\bRBL\\b", RegexOption.IGNORE_CASE))),
      BankRule("IndusInd Bank", listOf(Regex("IndusInd", RegexOption.IGNORE_CASE))),
      BankRule("AU Small Finance Bank", listOf(Regex("AU\\s*Small\\s*Finance", RegexOption.IGNORE_CASE))),
      BankRule("Standard Chartered Bank", listOf(Regex("Standard\\s*Chartered", RegexOption.IGNORE_CASE))),
      BankRule("Citibank", listOf(Regex("\\bCiti(bank)?\\b", RegexOption.IGNORE_CASE))),
      BankRule("SBI Card", listOf(Regex("\\bSBI\\b", RegexOption.IGNORE_CASE))),
      BankRule("DCB Bank", listOf(Regex("\\bDCB\\b", RegexOption.IGNORE_CASE))),
    )

  val fintechFallbacks: List<FintechIssuerRule> =
    listOf(
      FintechIssuerRule(Regex("Jupiter", RegexOption.IGNORE_CASE), "Federal Bank"),
      FintechIssuerRule(Regex("\\bOneCard\\b", RegexOption.IGNORE_CASE), "Federal Bank"),
      FintechIssuerRule(Regex("\\bNiyo\\b", RegexOption.IGNORE_CASE), "DCB Bank"),
      FintechIssuerRule(Regex("\\bSlice\\b", RegexOption.IGNORE_CASE), "SBM Bank India"),
      FintechIssuerRule(Regex("\\bUni\\s*Card\\b", RegexOption.IGNORE_CASE), "RBL Bank"),
      FintechIssuerRule(Regex("LazyPay", RegexOption.IGNORE_CASE), "RBL Bank"),
    )

  /**
   * Resolves the issuer bank from the SMS body.
   * @return the canonical bank name and whether it was a direct (high-confidence)
   *   match vs an inferred fintech-brand fallback (lower-confidence).
   */
  fun resolve(body: String): Pair<String, Boolean>? {
    for (rule in banks) {
      if (rule.bodyPatterns.any { it.containsMatchIn(body) }) {
        return rule.canonicalName to true
      }
    }
    for (rule in fintechFallbacks) {
      if (rule.pattern.containsMatchIn(body)) {
        return rule.issuerCanonicalName to false
      }
    }
    return null
  }
}
