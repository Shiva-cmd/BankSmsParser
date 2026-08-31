package com.banksmsparser.smsparser.config

/** One currency marker family: the symbol/code patterns that map to an ISO code. */
data class CurrencyRule(
  val isoCode: String,
  val markerPattern: Regex,
)

object PatternConfig {
  /**
   * Checked in order; the first marker found immediately before/at the amount wins.
   * To support a new currency, add a rule here - no extraction logic changes.
   */
  val currencies: List<CurrencyRule> =
    listOf(
      CurrencyRule("USD", Regex("USD", RegexOption.IGNORE_CASE)),
      CurrencyRule("EUR", Regex("EUR", RegexOption.IGNORE_CASE)),
      CurrencyRule("AED", Regex("AED", RegexOption.IGNORE_CASE)),
      CurrencyRule("GBP", Regex("GBP", RegexOption.IGNORE_CASE)),
      CurrencyRule("INR", Regex("INR|Rs\\.?|₹", RegexOption.IGNORE_CASE)),
    )

  // Matches "<currency marker><amount>", e.g. "Rs.450.00", "INR 1,250.00", "USD 49.99".
  val amountWithMarker: Regex =
    Regex("(INR|Rs\\.?|₹|USD|EUR|AED|GBP)\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)", RegexOption.IGNORE_CASE)

  /** Maps a matched currency marker (e.g. "Rs.", "₹", "USD") to its ISO 4217 code. */
  fun currencyCodeForMarker(marker: String): String {
    val trimmed = marker.trim()
    return currencies.firstOrNull { it.markerPattern.matches(trimmed) }?.isoCode ?: "INR"
  }

  // dd-mm-yy(yy) or dd/mm/yy(yy)
  val numericDate: Regex = Regex("\\b(\\d{1,2})[-/](\\d{1,2})[-/](\\d{2,4})\\b")

  // dd-MON-yy(yy), e.g. 06-APR-26
  val monthNameDate: Regex = Regex("\\b(\\d{1,2})[-\\s]([A-Za-z]{3,9})[-\\s](\\d{2,4})\\b")

  val monthAbbreviations: Map<String, Int> =
    mapOf(
      "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
      "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12,
    )

  // Card last-four in its various common phrasings: "xx5678", "XX 5678", "ending 4422",
  // "ending in XX9907", "Card no. XX9876", "A/C *4521".
  val cardLastFour: List<Regex> =
    listOf(
      Regex("(?:ending\\s*(?:in)?\\s*)(?:XX)?\\s*(\\d{4})\\b", RegexOption.IGNORE_CASE),
      Regex("\\bxx\\s*(\\d{4})\\b", RegexOption.IGNORE_CASE),
      Regex("card\\s*(?:no\\.?)?\\s*xx\\s*(\\d{4})", RegexOption.IGNORE_CASE),
      Regex("\\*(\\d{4})\\b"),
    )

  // Merchant name: text following an "at/to/from" anchor, stopped at the next
  // structural keyword or a sentence terminator.
  val merchantAnchors: List<Regex> =
    listOf(
      Regex(
        "\\b(?:at|to|from)\\s+([A-Za-z0-9&.,'\\-/ ]+?)(?=\\s+(?:on|with|has|is|against|via|for|from)\\b|\\.|$)",
        RegexOption.IGNORE_CASE,
      ),
    )
}
