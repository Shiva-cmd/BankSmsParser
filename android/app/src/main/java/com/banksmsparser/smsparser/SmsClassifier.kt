package com.banksmsparser.smsparser

import com.banksmsparser.smsparser.config.ExclusionConfig
import com.banksmsparser.smsparser.config.ExclusionMatch

/**
 * Applies the ordered [ExclusionConfig.rules] to a normalized SMS body and returns
 * the first matching exclusion, or null if the message survives every exclusion
 * check (i.e. is a candidate for the INCLUDE path in [BankSmsParser]).
 */
object SmsClassifier {
  fun classify(text: String): ExclusionMatch? {
    for (rule in ExclusionConfig.rules) {
      val match = rule(text)
      if (match != null) return match
    }
    return null
  }
}
