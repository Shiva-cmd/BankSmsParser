import type {ParsedResult} from './types';

export interface Summary {
  includedCount: number;
  excludedCount: number;
  inrDebitTotal: number;
  inrCreditRefundTotal: number;
  exclusionCounts: Array<{reason: string; count: number}>;
}

export function summarize(results: ParsedResult[]): Summary {
  let includedCount = 0;
  let excludedCount = 0;
  let inrDebitTotal = 0;
  let inrCreditRefundTotal = 0;
  const exclusionTally = new Map<string, number>();

  for (const result of results) {
    if (result.decision === 'INCLUDE' && result.transaction) {
      includedCount += 1;
      const {amount, currency, type} = result.transaction;
      if (currency === 'INR') {
        if (type === 'DEBIT') {
          inrDebitTotal += amount;
        } else {
          inrCreditRefundTotal += amount;
        }
      }
    } else {
      excludedCount += 1;
      const reason = result.excludeReason ?? 'UNKNOWN';
      exclusionTally.set(reason, (exclusionTally.get(reason) ?? 0) + 1);
    }
  }

  const exclusionCounts = Array.from(exclusionTally.entries())
    .map(([reason, count]) => ({reason, count}))
    .sort((a, b) => b.count - a.count);

  return {
    includedCount,
    excludedCount,
    inrDebitTotal,
    inrCreditRefundTotal,
    exclusionCounts,
  };
}

export function formatInr(amount: number): string {
  return `₹${amount.toLocaleString('en-IN', {
    minimumFractionDigits: 0,
    maximumFractionDigits: 2,
  })}`;
}
