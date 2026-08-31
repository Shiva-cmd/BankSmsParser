package com.banksmsparser.smsparser

import com.banksmsparser.smsparser.config.PatternConfig

/** Pure field-extraction helpers. Each function is independent and returns null on failure. */
object SmsExtractor {
  fun extractAmount(text: String): Pair<Double, String>? {
    val match = PatternConfig.amountWithMarker.find(text) ?: return null
    val marker = match.groupValues[1]
    val numberStr = match.groupValues[2].replace(",", "")
    val amount = numberStr.toDoubleOrNull() ?: return null
    return amount to PatternConfig.currencyCodeForMarker(marker)
  }

  fun extractCardLastFour(text: String): String? {
    for (pattern in PatternConfig.cardLastFour) {
      pattern.find(text)?.let { return it.groupValues[1] }
    }
    return null
  }

  private val looksLikeCardOrBankPhrase = Regex("\\bcard\\b|\\byour\\b|\\bbank\\b", RegexOption.IGNORE_CASE)

  fun extractMerchant(text: String): String? {
    for (pattern in PatternConfig.merchantAnchors) {
      // A message can contain multiple "at/to/from" anchors (e.g. "credited to
      // your HDFC Card ... from BIGBASKET"): skip anchors whose capture is really
      // describing the card/bank, not the counterparty merchant.
      for (match in pattern.findAll(text)) {
        val raw = match.groupValues[1].trim().trim('.', ',', '-', ' ')
        if (raw.isNotBlank() && !looksLikeCardOrBankPhrase.containsMatchIn(raw)) {
          return raw
        }
      }
    }
    return null
  }

  fun extractDate(text: String): String? {
    PatternConfig.numericDate.find(text)?.let { match ->
      val day = match.groupValues[1].toIntOrNull()
      val month = match.groupValues[2].toIntOrNull()
      var year = match.groupValues[3].toIntOrNull()
      if (day != null && month != null && year != null && month in 1..12 && day in 1..31) {
        if (year < 100) year += 2000
        return "%04d-%02d-%02d".format(year, month, day)
      }
    }
    PatternConfig.monthNameDate.find(text)?.let { match ->
      val day = match.groupValues[1].toIntOrNull() ?: return@let
      val month = PatternConfig.monthAbbreviations[match.groupValues[2].take(3).lowercase()] ?: return@let
      var year = match.groupValues[3].toIntOrNull() ?: return@let
      if (year < 100) year += 2000
      return "%04d-%02d-%02d".format(year, month, day)
    }
    return null
  }
}
