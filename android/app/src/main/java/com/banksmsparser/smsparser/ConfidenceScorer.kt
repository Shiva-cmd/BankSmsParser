package com.banksmsparser.smsparser

/**
 * Confidence model for the INCLUDE path. Starts high for a message that survived
 * every exclusion rule and had its amount and issuer bank resolved directly, then
 * loses points for each field that had to be inferred or came back missing.
 * See README "Confidence scoring" for the full rationale.
 */
object ConfidenceScorer {
  private const val BASE = 0.95
  private const val INDIRECT_BANK_PENALTY = 0.15
  private const val MISSING_MERCHANT_PENALTY = 0.08
  private const val MISSING_CARD_PENALTY = 0.05
  private const val MISSING_DATE_PENALTY = 0.07
  private const val MIN_INCLUDE_CONFIDENCE = 0.4
  private const val MAX_CONFIDENCE = 0.99

  fun scoreInclude(
    bankDirectMatch: Boolean,
    hasMerchant: Boolean,
    hasCardLastFour: Boolean,
    hasDate: Boolean,
  ): Double {
    var score = BASE
    if (!bankDirectMatch) score -= INDIRECT_BANK_PENALTY
    if (!hasMerchant) score -= MISSING_MERCHANT_PENALTY
    if (!hasCardLastFour) score -= MISSING_CARD_PENALTY
    if (!hasDate) score -= MISSING_DATE_PENALTY
    return score.coerceIn(MIN_INCLUDE_CONFIDENCE, MAX_CONFIDENCE)
  }
}
