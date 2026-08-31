package com.banksmsparser.smsparser.model

enum class Decision {
  INCLUDE,
  EXCLUDE,
}

enum class TransactionType {
  DEBIT,
  CREDIT,
  REFUND,
}

data class Transaction(
  val amount: Double,
  val currency: String,
  val bank: String,
  val cardLastFour: String?,
  val merchant: String?,
  val type: TransactionType,
  val date: String?,
)

data class ParsedResult(
  val rawSms: String,
  val decision: Decision,
  val excludeReason: String?,
  val transaction: Transaction?,
  val confidence: Double,
)
