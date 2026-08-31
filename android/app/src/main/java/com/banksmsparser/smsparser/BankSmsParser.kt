package com.banksmsparser.smsparser

import com.banksmsparser.smsparser.config.BankConfig
import com.banksmsparser.smsparser.model.Decision
import com.banksmsparser.smsparser.model.ParsedResult
import com.banksmsparser.smsparser.model.Transaction
import com.banksmsparser.smsparser.model.TransactionType

/**
 * Orchestrates a single SMS through: exclusion classification -> field extraction ->
 * INCLUDE gate -> confidence scoring. This is the only entry point native callers
 * (the RN bridge module, and unit tests) should use.
 */
object BankSmsParser {
  private val cardWord = Regex("\\bcard\\b", RegexOption.IGNORE_CASE)
  private val refundWord = Regex("refund", RegexOption.IGNORE_CASE)
  private val creditVerb = Regex("credited", RegexOption.IGNORE_CASE)
  private val spentOrDebitedVerb = Regex("spent|debited", RegexOption.IGNORE_CASE)

  private const val SHORT_TEXT_THRESHOLD = 40

  fun parse(rawSms: String): ParsedResult {
    val text = rawSms.trim()
    if (text.isBlank()) {
      return ParsedResult(rawSms, Decision.EXCLUDE, "MALFORMED_SMS", null, 0.05)
    }

    SmsClassifier.classify(text)?.let { match ->
      return ParsedResult(rawSms, Decision.EXCLUDE, match.reasonCode, null, match.confidence)
    }

    val amountInfo = SmsExtractor.extractAmount(text)
    val bankInfo = BankConfig.resolve(text)
    val hasCardWord = cardWord.containsMatchIn(text)

    val isIncludeCandidate = amountInfo != null && bankInfo != null && hasCardWord
    if (!isIncludeCandidate) {
      return fallbackExclusion(rawSms, text, amountInfo != null, bankInfo != null, hasCardWord)
    }

    val (amount, currency) = amountInfo!!
    val (bankName, isDirectBankMatch) = bankInfo!!
    val merchant = SmsExtractor.extractMerchant(text)
    val cardLastFour = SmsExtractor.extractCardLastFour(text)
    val date = SmsExtractor.extractDate(text)

    val type =
      when {
        refundWord.containsMatchIn(text) -> TransactionType.REFUND
        creditVerb.containsMatchIn(text) && !spentOrDebitedVerb.containsMatchIn(text) -> TransactionType.CREDIT
        else -> TransactionType.DEBIT
      }

    val confidence =
      ConfidenceScorer.scoreInclude(
        bankDirectMatch = isDirectBankMatch,
        hasMerchant = merchant != null,
        hasCardLastFour = cardLastFour != null,
        hasDate = date != null,
      )

    val transaction = Transaction(amount, currency, bankName, cardLastFour, merchant, type, date)
    return ParsedResult(rawSms, Decision.INCLUDE, null, transaction, confidence)
  }

  fun parseAll(samples: List<String>): List<ParsedResult> = samples.map(::parse)

  /**
   * Reached when a message matched no known exclusion rule but also doesn't have
   * enough signal (amount + resolvable issuer bank + the word "card") to be counted
   * as a credit-card transaction. A short message with almost no signal reads as
   * malformed/incomplete; a longer message with partial signal reads as genuinely
   * ambiguous, and is excluded as LOW_CONFIDENCE rather than guessed at.
   */
  private fun fallbackExclusion(
    rawSms: String,
    text: String,
    hasAmount: Boolean,
    hasBank: Boolean,
    hasCardWord: Boolean,
  ): ParsedResult {
    val signalCount = listOf(hasAmount, hasBank, hasCardWord).count { it }
    return if (signalCount == 0 || (text.length < SHORT_TEXT_THRESHOLD && signalCount <= 1)) {
      ParsedResult(rawSms, Decision.EXCLUDE, "MALFORMED_SMS", null, 0.1)
    } else {
      val confidence = (0.15 + signalCount * 0.08).coerceIn(0.1, 0.4)
      ParsedResult(rawSms, Decision.EXCLUDE, "LOW_CONFIDENCE", null, confidence)
    }
  }
}
