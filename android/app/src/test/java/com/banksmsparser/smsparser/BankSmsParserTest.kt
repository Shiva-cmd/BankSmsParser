package com.banksmsparser.smsparser

import com.banksmsparser.smsparser.model.Decision
import com.banksmsparser.smsparser.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BankSmsParserTest {
  // 1. A clear credit-card spend.
  @Test
  fun `clear credit card spend is included with full field extraction`() {
    val sms = "INR 1,250.00 spent on HDFC Bank Credit Card xx5678 at SWIGGY on 03-04-2026. Avl Limit: INR 1,45,300.00."
    val result = BankSmsParser.parse(sms)

    assertEquals(Decision.INCLUDE, result.decision)
    assertNull(result.excludeReason)
    val txn = requireNotNull(result.transaction)
    assertEquals(1250.0, txn.amount, 0.001)
    assertEquals("INR", txn.currency)
    assertEquals("HDFC Bank", txn.bank)
    assertEquals("5678", txn.cardLastFour)
    assertEquals("SWIGGY", txn.merchant)
    assertEquals(TransactionType.DEBIT, txn.type)
    assertEquals("2026-04-03", txn.date)
    assertTrue("high-signal spend should score high confidence", result.confidence >= 0.85)
  }

  // 2. A debit-card exclusion (must not be confused with a credit-card spend).
  @Test
  fun `debit card transaction is excluded`() {
    val sms = "Transaction Alert: Rs. 500.00 debited from your HDFC Bank Debit Card ending 1234 at SWIGGY on 06-04-26."
    val result = BankSmsParser.parse(sms)

    assertEquals(Decision.EXCLUDE, result.decision)
    assertEquals("DEBIT_CARD", result.excludeReason)
    assertNull(result.transaction)
  }

  // 3. An OTP exclusion.
  @Test
  fun `otp message is excluded with high confidence`() {
    val sms = "Use 458219 as your OTP for HDFC Bank Net Banking login. Valid for 5 mins. Do NOT share with anyone."
    val result = BankSmsParser.parse(sms)

    assertEquals(Decision.EXCLUDE, result.decision)
    assertEquals("OTP", result.excludeReason)
    assertTrue(result.confidence >= 0.9)
  }

  // 4. A UPI / savings-account exclusion.
  @Test
  fun `upi savings account debit is excluded and not attributed to a card`() {
    val sms = "Rs 1,200 debited from A/c XX4521 via UPI on 11-04-26. UPI/P2A/MOHAN-SHARMA@OKAXIS/Personal. UPI Ref: 240411887211."
    val result = BankSmsParser.parse(sms)

    assertEquals(Decision.EXCLUDE, result.decision)
    assertEquals("UPI_BANK_ACCOUNT", result.excludeReason)
    assertNull(result.transaction)
  }

  // 5. Fintech / co-branded issuer attribution: the branding in the message
  // ("Jupiter") must not override the issuer bank actually named in the body.
  @Test
  fun `co-branded fintech card resolves to the issuer named in the body, not the app brand`() {
    val sms =
      "Hey there, you've spent Rs 1836.00 to HOSPITALITY PVT DELHI IN on your Edge Federal Bank Credit Card ending 4422 " +
        "on 07-04-2026. Tap to view your transactions in the Jupiter app."
    val result = BankSmsParser.parse(sms)

    assertEquals(Decision.INCLUDE, result.decision)
    val txn = requireNotNull(result.transaction)
    assertEquals("Federal Bank", txn.bank)
    assertEquals("4422", txn.cardLastFour)
  }

  // 5b. A second co-branded case using only the fintech/product brand (BOBCARD),
  // exercising the direct BankConfig match rather than the fintech-fallback map.
  @Test
  fun `bobcard branded card resolves to its issuer via body pattern`() {
    val sms = "You've spent Rs. 849.00 at Blackwater Coffee, Gurgaon with your BOBCARD One Credit Card ending in XX9907 on 08-04-2026."
    val result = BankSmsParser.parse(sms)

    assertEquals(Decision.INCLUDE, result.decision)
    val txn = requireNotNull(result.transaction)
    assertEquals("Bank of Baroda (BOBCARD)", txn.bank)
    assertEquals("9907", txn.cardLastFour)
    assertEquals("Blackwater Coffee, Gurgaon", txn.merchant)
  }

  // 6. A refund.
  @Test
  fun `credit card refund is included as REFUND type`() {
    val sms = "Refund of Rs 450.00 has been credited to your HDFC Card xx5678 from BIGBASKET on 12-04-26 against original txn dated 02-04-26."
    val result = BankSmsParser.parse(sms)

    assertEquals(Decision.INCLUDE, result.decision)
    val txn = requireNotNull(result.transaction)
    assertEquals(TransactionType.REFUND, txn.type)
    assertEquals(450.0, txn.amount, 0.001)
    assertEquals("BIGBASKET", txn.merchant)
    // The refund's own date, not the original transaction date it references.
    assertEquals("2026-04-12", txn.date)
  }

  // 7. A foreign-currency transaction: currency must not be assumed to be INR.
  @Test
  fun `foreign currency spend is not assumed to be INR`() {
    val sms = "USD 49.99 spent on your Axis Bank Card XX9876 at NETFLIX.COM/US on 13-APR-26. Foreign currency markup of 3.5% will be applied."
    val result = BankSmsParser.parse(sms)

    assertEquals(Decision.INCLUDE, result.decision)
    val txn = requireNotNull(result.transaction)
    assertEquals("USD", txn.currency)
    assertEquals(49.99, txn.amount, 0.001)
    assertEquals("Axis Bank", txn.bank)
  }

  // 8. A malformed / truncated SMS must fail safely, not guess.
  @Test
  fun `truncated sms is excluded as malformed with low confidence`() {
    val sms = "Spent Rs. 2,4"
    val result = BankSmsParser.parse(sms)

    assertEquals(Decision.EXCLUDE, result.decision)
    assertEquals("MALFORMED_SMS", result.excludeReason)
    assertNull(result.transaction)
    assertTrue("malformed messages should be low confidence", result.confidence <= 0.2)
  }

  // Additional coverage: exclusions matter as much as inclusions.

  @Test
  fun `credit card bill due reminder is excluded, not counted as spend`() {
    val sms = "Your HDFC Bank Credit Card xx5678 bill of Rs 23,450.00 is due on 15-04-26. View your bill at hdfcbank.com/billview."
    val result = BankSmsParser.parse(sms)
    assertEquals(Decision.EXCLUDE, result.decision)
    assertEquals("BILL_DUE", result.excludeReason)
  }

  @Test
  fun `credit card bill payment received is excluded as CARD_PAYMENT, not spend`() {
    val sms = "Payment of Rs 23,450.00 received towards your HDFC Bank Credit Card xx5678 on 11-04-26. Thank you."
    val result = BankSmsParser.parse(sms)
    assertEquals(Decision.EXCLUDE, result.decision)
    assertEquals("CARD_PAYMENT", result.excludeReason)
  }

  @Test
  fun `declined transaction attempt is excluded and not counted as spend`() {
    val sms = "Transaction Declined: Attempt to spend Rs. 9,999 on your ICICI Credit Card XX1122 at FOREIGN MERCHANT was declined due to insufficient credit limit."
    val result = BankSmsParser.parse(sms)
    assertEquals(Decision.EXCLUDE, result.decision)
    assertEquals("DECLINED", result.excludeReason)
  }

  @Test
  fun `salary credit to a savings account is excluded, not a card credit`() {
    val sms = "Dear Customer, Rs 50000 credited to your A/c XX4521 on 05-04-2026 by SALARY-ACMECORP. Avl Bal: Rs 1,52,300.45."
    val result = BankSmsParser.parse(sms)
    assertEquals(Decision.EXCLUDE, result.decision)
    assertEquals("SALARY_CREDIT", result.excludeReason)
  }

  @Test
  fun `pure balance alert is excluded and not treated as a transaction`() {
    val sms = "Avl Bal in your A/C XX4521 as on 08-04-26 is INR 1,02,450.30. Call 18002586161 for details."
    val result = BankSmsParser.parse(sms)
    assertEquals(Decision.EXCLUDE, result.decision)
    assertEquals("BALANCE_ALERT", result.excludeReason)
  }

  // Config-driven design: BankConfig.resolve is data, not branching logic - a
  // message naming an issuer bank that has no dedicated rule should simply fail
  // to resolve (conservative), proving resolution is driven by the config list.
  @Test
  fun `bank resolution is config-driven and returns null for an unconfigured issuer`() {
    val resolved = com.banksmsparser.smsparser.config.BankConfig.resolve("Spent Rs 100 on Some Unknown Bank Credit Card at a shop")
    assertNull(resolved)
  }

  // Regression guard: classifies every provided sample and checks the result
  // against the assignment's own worked example (7 included, 18 excluded).
  @Test
  fun `all 25 provided samples classify as expected`() {
    val samples = listOf(
      "Sent Rs.450.00 From HDFC Bank A/C *4521 To BIGBASKET on 02/04/26. Ref 405617287211. Not You? Call 18002586161/SMS BLOCK CC to 7308080808 to block CC.",
      "INR 1,250.00 spent on HDFC Bank Credit Card xx5678 at SWIGGY on 03-04-2026. Avl Limit: INR 1,45,300.00.",
      "ICICI Bank Acct XX123 debited Rs 2,500.00 on 04-Apr-26 & credited to UPI/swiggy@hdfc/Payment. UPI Ref:240412345678. Call 18002662 if not you.",
      "Dear Customer, Rs 50000 credited to your A/c XX4521 on 05-04-2026 by SALARY-ACMECORP. Avl Bal: Rs 1,52,300.45.",
      "INR 320.00 spent using Axis Bank Card no. XX9876 on 06-APR-26 at AMAZON. Available Limit: INR 87,500.00.",
      "Transaction Alert: Rs. 500.00 debited from your HDFC Bank Debit Card ending 1234 at SWIGGY on 06-04-26.",
      "Spent Rs. 1200.00 on YES BANK Credit Card XX8888 at AMAZON on 07-04-26. Avl Lmt: Rs 78,500.",
      "Hey there, you've spent Rs 1836.00 to HOSPITALITY PVT DELHI IN on your Edge Federal Bank Credit Card ending 4422 on 07-04-2026. Tap to view your transactions in the Jupiter app.",
      "You've spent Rs. 849.00 at Blackwater Coffee, Gurgaon with your BOBCARD One Credit Card ending in XX9907 on 08-04-2026.",
      "Use 458219 as your OTP for HDFC Bank Net Banking login. Valid for 5 mins. Do NOT share with anyone.",
      "Avl Bal in your A/C XX4521 as on 08-04-26 is INR 1,02,450.30. Call 18002586161 for details.",
      "Your HDFC Bank Credit Card xx5678 bill of Rs 23,450.00 is due on 15-04-26. View your bill at hdfcbank.com/billview.",
      "Get flat 50% off + extra 10% cashback on travel bookings with HDFC Credit Cards this weekend. T&C apply. Visit hdfcbank.com/offers.",
      "Dear Customer, Rs 2,500 will be auto debited via E-Mandate from your HDFC Card XX5678 on 12-04-26 for NETFLIX_SUBSCRIPTION. Please maintain sufficient limit.",
      "Transaction Declined: Attempt to spend Rs. 9,999 on your ICICI Credit Card XX1122 at FOREIGN MERCHANT was declined due to insufficient credit limit.",
      "Your SIP of Rs 5,000 in Mirae Asset Large Cap Fund folio 12345678 has been debited from A/c XX4521 on 10-04-26.",
      "Your Rs 75,000.00 spend on HDFC Card xx5678 at CROMA-ELECTRONICS has been converted to EMI of Rs 6,847/month for 12 months at 13% interest.",
      "Finance charge of Rs 1,250.45 + GST Rs 225.08 has been debited from your HDFC Credit Card xx5678 for late payment on bill dated 31-03-2026.",
      "Payment of Rs 23,450.00 received towards your HDFC Bank Credit Card xx5678 on 11-04-26. Thank you.",
      "Rs 1,200 debited from A/c XX4521 via UPI on 11-04-26. UPI/P2A/MOHAN-SHARMA@OKAXIS/Personal. UPI Ref: 240411887211.",
      "Refund of Rs 450.00 has been credited to your HDFC Card xx5678 from BIGBASKET on 12-04-26 against original txn dated 02-04-26.",
      "USD 49.99 spent on your Axis Bank Card XX9876 at NETFLIX.COM/US on 13-APR-26. Foreign currency markup of 3.5% will be applied. INR equivalent will appear in statement.",
      "Premium of Rs 12,500 debited from A/c XX4521 on 13-04-26 for HDFC Life Insurance Policy XYZ-2026. Renewal complete.",
      "Rs.99 debited from A/c XX4521 via UPI on 14-04-26. UPI Ref: 240478234511 to NETFLIX-MONTHLY. Avl Bal: Rs 1,02,351.30.",
      "Spent Rs. 2,4",
    )
    val expected = listOf(
      "SAVINGS_ACCOUNT", "INCLUDE", "UPI_BANK_ACCOUNT", "SALARY_CREDIT", "INCLUDE",
      "DEBIT_CARD", "INCLUDE", "INCLUDE", "INCLUDE", "OTP",
      "BALANCE_ALERT", "BILL_DUE", "OFFER", "FUTURE_AUTO_DEBIT", "DECLINED",
      "INVESTMENT", "EMI_CONVERSION", "FEE_OR_CHARGE", "CARD_PAYMENT", "UPI_BANK_ACCOUNT",
      "INCLUDE", "INCLUDE", "INSURANCE", "UPI_BANK_ACCOUNT", "MALFORMED_SMS",
    )
    val results = BankSmsParser.parseAll(samples)
    val mismatches = mutableListOf<String>()
    results.forEachIndexed { i, r ->
      val actual = if (r.decision == Decision.INCLUDE) "INCLUDE" else r.excludeReason
      if (actual != expected[i]) {
        mismatches.add("sample ${i + 1}: expected=${expected[i]} actual=$actual (conf=${r.confidence})")
      }
    }
    assertTrue("Mismatches:\n" + mismatches.joinToString("\n"), mismatches.isEmpty())
    assertEquals(7, results.count { it.decision == Decision.INCLUDE })
    assertEquals(18, results.count { it.decision == Decision.EXCLUDE })
  }

  @Test
  fun `full sample batch preserves order and count`() {
    val samples = listOf("Use 111111 as your OTP for login.", "INR 100.00 spent on HDFC Bank Credit Card xx1111 at TESTMART on 01-01-2026.")
    val results = BankSmsParser.parseAll(samples)
    assertEquals(2, results.size)
    assertEquals(samples[0], results[0].rawSms)
    assertEquals(samples[1], results[1].rawSms)
    assertEquals(Decision.EXCLUDE, results[0].decision)
    assertEquals(Decision.INCLUDE, results[1].decision)
  }
}
