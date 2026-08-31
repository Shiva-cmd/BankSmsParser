export type Decision = 'INCLUDE' | 'EXCLUDE';

export type TransactionType = 'DEBIT' | 'CREDIT' | 'REFUND';

export interface Transaction {
  amount: number;
  currency: string;
  bank: string;
  cardLastFour: string | null;
  merchant: string | null;
  type: TransactionType;
  date: string | null;
}

export interface ParsedResult {
  rawSms: string;
  decision: Decision;
  excludeReason: string | null;
  transaction: Transaction | null;
  confidence: number;
}

export interface SmsSample {
  id: string;
  text: string;
}
