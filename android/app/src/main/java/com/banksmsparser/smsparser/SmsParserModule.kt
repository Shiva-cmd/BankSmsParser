package com.banksmsparser.smsparser

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.WritableMap
import com.banksmsparser.smsparser.model.ParsedResult
import com.banksmsparser.smsparser.model.Transaction

/**
 * The single JS <-> Kotlin bridge surface for this feature. All parsing,
 * classification, extraction, and confidence scoring happens in [BankSmsParser]
 * (and the classes it delegates to) - this module only marshals data across the
 * bridge.
 */
class SmsParserModule(
  reactContext: ReactApplicationContext,
) : ReactContextBaseJavaModule(reactContext) {
  override fun getName(): String = NAME

  @ReactMethod
  fun parseSms(
    samples: ReadableArray,
    promise: Promise,
  ) {
    try {
      val smsList = mutableListOf<String>()
      for (i in 0 until samples.size()) {
        smsList.add(samples.getString(i) ?: "")
      }
      val results = BankSmsParser.parseAll(smsList)
      val output: WritableArray = Arguments.createArray()
      for (result in results) {
        output.pushMap(toWritableMap(result))
      }
      promise.resolve(output)
    } catch (e: Exception) {
      promise.reject("PARSE_ERROR", e.message, e)
    }
  }

  private fun toWritableMap(result: ParsedResult): WritableMap {
    val map = Arguments.createMap()
    map.putString("rawSms", result.rawSms)
    map.putString("decision", result.decision.name)
    map.putString("excludeReason", result.excludeReason)
    map.putMap("transaction", result.transaction?.let(::toWritableMap))
    map.putDouble("confidence", result.confidence)
    return map
  }

  private fun toWritableMap(transaction: Transaction): WritableMap {
    val map = Arguments.createMap()
    map.putDouble("amount", transaction.amount)
    map.putString("currency", transaction.currency)
    map.putString("bank", transaction.bank)
    map.putString("cardLastFour", transaction.cardLastFour)
    map.putString("merchant", transaction.merchant)
    map.putString("type", transaction.type.name)
    map.putString("date", transaction.date)
    return map
  }

  companion object {
    const val NAME = "SmsParserModule"
  }
}
