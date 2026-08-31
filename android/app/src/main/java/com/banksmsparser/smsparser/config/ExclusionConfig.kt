package com.banksmsparser.smsparser.config

data class ExclusionMatch(
  val reasonCode: String,
  val confidence: Double,
)

/** A rule inspects the (already trimmed) SMS body and returns a match, or null. */
typealias ExclusionRule = (String) -> ExclusionMatch?

/**
 * Ordered exclusion rules. [SmsClassifier] walks this list top to bottom and returns
 * the first match. Order encodes priority: unambiguous, high-signal categories
 * (OTP, declined, bill-due, ...) are checked before the broader "money moved out of
 * a bank account, not a credit card" catch-alls, so a message that happens to mention
 * both an account number and a due-date reads as the more specific category.
 *
 * To add a new exclusion category: append a new rule here. No other file needs to
 * change.
 */
object ExclusionConfig {
  private val otp = Regex("\\bOTP\\b|one[- ]time password|verification code", RegexOption.IGNORE_CASE)
  private val declined = Regex("\\bdeclined\\b|\\bdecline\\b|transaction failed", RegexOption.IGNORE_CASE)
  // NB: uses a bounded ".{0,N}?" gap rather than "[^.]*" because amounts like
  // "23,450.00" contain a literal '.' - a same-sentence check built on "no period"
  // would falsely stop at the decimal point.
  private val billDue = Regex("\\bbill\\b.{0,60}?\\bdue\\b|\\bdue\\b.{0,60}?\\bbill\\b|payment due", RegexOption.IGNORE_CASE)
  private val cardPaymentReceived =
    Regex(
      "(payment|amount)\\s+of.{0,80}?(received|credited).{0,80}?(towards|against).{0,80}?card|received towards.{0,80}?card",
      RegexOption.IGNORE_CASE,
    )
  private val futureAutoDebit =
    Regex("will be auto[- ]?debited|e-?mandate|scheduled to be debited|upcoming (auto[- ]?)?debit|auto-?debit alert", RegexOption.IGNORE_CASE)
  private val emiConversion = Regex("converted to emi|emi conversion|convert(ed)? .* to .* emi", RegexOption.IGNORE_CASE)
  private val feeOrCharge = Regex("finance charge|late (payment )?fee|annual fee|processing fee", RegexOption.IGNORE_CASE)
  private val insurance = Regex("insurance|premium of", RegexOption.IGNORE_CASE)
  private val investment = Regex("\\bSIP\\b|mutual fund|\\bfolio\\b|\\bNAV\\b|fixed deposit|\\bFD\\b", RegexOption.IGNORE_CASE)
  private val offer = Regex("cashback|%\\s*off|\\boffer\\b|t&c apply|flat \\d+%", RegexOption.IGNORE_CASE)
  private val balanceAlert = Regex("^(avl\\.?\\s*bal|available\\s*bal(ance)?)", RegexOption.IGNORE_CASE)
  private val debitCard = Regex("debit\\s*card", RegexOption.IGNORE_CASE)
  private val upiKeyword = Regex("\\bUPI\\b", RegexOption.IGNORE_CASE)
  private val accountKeyword = Regex("\\bA/[Cc]\\b|\\bAcct\\b|\\bAccount\\b", RegexOption.IGNORE_CASE)
  private val accountMoneyMovement = Regex("debited|credited|\\bsent\\b", RegexOption.IGNORE_CASE)
  private val cardKeyword = Regex("\\bcard\\b", RegexOption.IGNORE_CASE)
  private val salary = Regex("\\bsalary\\b", RegexOption.IGNORE_CASE)

  val rules: List<ExclusionRule> =
    listOf(
      { text -> if (otp.containsMatchIn(text)) ExclusionMatch("OTP", 0.97) else null },
      { text -> if (declined.containsMatchIn(text)) ExclusionMatch("DECLINED", 0.93) else null },
      { text -> if (billDue.containsMatchIn(text)) ExclusionMatch("BILL_DUE", 0.92) else null },
      { text -> if (cardPaymentReceived.containsMatchIn(text)) ExclusionMatch("CARD_PAYMENT", 0.9) else null },
      { text -> if (futureAutoDebit.containsMatchIn(text)) ExclusionMatch("FUTURE_AUTO_DEBIT", 0.9) else null },
      { text -> if (emiConversion.containsMatchIn(text)) ExclusionMatch("EMI_CONVERSION", 0.92) else null },
      { text -> if (feeOrCharge.containsMatchIn(text)) ExclusionMatch("FEE_OR_CHARGE", 0.9) else null },
      { text -> if (insurance.containsMatchIn(text)) ExclusionMatch("INSURANCE", 0.88) else null },
      { text -> if (investment.containsMatchIn(text)) ExclusionMatch("INVESTMENT", 0.9) else null },
      { text -> if (offer.containsMatchIn(text) && !accountMoneyMovement.containsMatchIn(text)) ExclusionMatch("OFFER", 0.88) else null },
      { text -> if (balanceAlert.containsMatchIn(text.trim())) ExclusionMatch("BALANCE_ALERT", 0.92) else null },
      { text -> if (debitCard.containsMatchIn(text)) ExclusionMatch("DEBIT_CARD", 0.95) else null },
      { text ->
        val isAccountMovement = accountKeyword.containsMatchIn(text) && accountMoneyMovement.containsMatchIn(text) && !cardKeyword.containsMatchIn(text)
        if (!isAccountMovement) {
          null
        } else if (salary.containsMatchIn(text)) {
          ExclusionMatch("SALARY_CREDIT", 0.93)
        } else if (upiKeyword.containsMatchIn(text)) {
          ExclusionMatch("UPI_BANK_ACCOUNT", 0.93)
        } else {
          ExclusionMatch("SAVINGS_ACCOUNT", 0.9)
        }
      },
    )
}
