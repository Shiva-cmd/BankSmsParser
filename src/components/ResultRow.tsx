import React from 'react';
import {StyleSheet, Text, TouchableOpacity, View} from 'react-native';
import type {ParsedResult} from '../types';
import {formatInr} from '../summary';

function bankInitials(bank: string): string {
  const words = bank.replace(/\(.*?\)/g, '').trim().split(/\s+/);
  return words
    .slice(0, 2)
    .map(w => w[0])
    .join('')
    .toUpperCase();
}

function confidenceColor(confidence: number): string {
  if (confidence >= 0.75) return '#0f8a45';
  if (confidence >= 0.45) return '#c98a02';
  return '#c0392b';
}

function ConfidenceDot({confidence}: {confidence: number}) {
  return (
    <View style={styles.confidenceWrap}>
      <View style={[styles.confidenceDot, {backgroundColor: confidenceColor(confidence)}]} />
      <Text style={styles.confidenceText}>{Math.round(confidence * 100)}%</Text>
    </View>
  );
}

export function ResultRow({result, onPress}: {result: ParsedResult; onPress: () => void}) {
  if (result.decision === 'INCLUDE' && result.transaction) {
    const txn = result.transaction;
    const amountPrefix = txn.type === 'DEBIT' ? '-' : '+';
    const amountColor = txn.type === 'DEBIT' ? '#c0392b' : '#0f8a45';
    return (
      <TouchableOpacity style={styles.row} onPress={onPress} activeOpacity={0.6}>
        <View style={styles.avatar}>
          <Text style={styles.avatarText}>{bankInitials(txn.bank)}</Text>
        </View>
        <View style={styles.rowBody}>
          <Text style={styles.merchantText} numberOfLines={1}>
            {txn.merchant ?? 'Unknown merchant'}
          </Text>
          <Text style={styles.subText} numberOfLines={1}>
            {txn.bank}
            {txn.date ? ` · ${txn.date}` : ''} · {txn.type}
          </Text>
        </View>
        <View style={styles.rowTrailing}>
          <Text style={[styles.amountText, {color: amountColor}]}>
            {amountPrefix}
            {txn.currency === 'INR' ? formatInr(txn.amount) : `${txn.currency} ${txn.amount.toFixed(2)}`}
          </Text>
          <ConfidenceDot confidence={result.confidence} />
        </View>
      </TouchableOpacity>
    );
  }

  return (
    <TouchableOpacity style={[styles.row, styles.excludedRow]} onPress={onPress} activeOpacity={0.6}>
      <View style={styles.rowBody}>
        <View style={styles.badge}>
          <Text style={styles.badgeText}>{result.excludeReason ?? 'EXCLUDED'}</Text>
        </View>
        <Text style={styles.excludedPreview} numberOfLines={2}>
          {result.rawSms}
        </Text>
      </View>
      <View style={styles.rowTrailing}>
        <ConfidenceDot confidence={result.confidence} />
      </View>
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingVertical: 12,
    backgroundColor: '#ffffff',
    borderBottomWidth: 1,
    borderBottomColor: '#eef1f5',
  },
  excludedRow: {
    backgroundColor: '#f7f8fa',
  },
  avatar: {
    width: 38,
    height: 38,
    borderRadius: 19,
    backgroundColor: '#e4e9f2',
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 12,
  },
  avatarText: {
    fontSize: 13,
    fontWeight: '700',
    color: '#334155',
  },
  rowBody: {
    flex: 1,
    marginRight: 8,
  },
  merchantText: {
    fontSize: 15,
    fontWeight: '600',
    color: '#1a1d23',
  },
  subText: {
    fontSize: 12,
    color: '#6b7280',
    marginTop: 2,
  },
  rowTrailing: {
    alignItems: 'flex-end',
  },
  amountText: {
    fontSize: 15,
    fontWeight: '700',
  },
  badge: {
    alignSelf: 'flex-start',
    backgroundColor: '#e4e9f2',
    borderRadius: 10,
    paddingHorizontal: 8,
    paddingVertical: 2,
    marginBottom: 4,
  },
  badgeText: {
    fontSize: 10,
    fontWeight: '700',
    color: '#475569',
    letterSpacing: 0.3,
  },
  excludedPreview: {
    fontSize: 13,
    color: '#8a8f98',
  },
  confidenceWrap: {
    flexDirection: 'row',
    alignItems: 'center',
    marginTop: 4,
  },
  confidenceDot: {
    width: 6,
    height: 6,
    borderRadius: 3,
    marginRight: 4,
  },
  confidenceText: {
    fontSize: 11,
    color: '#9aa1ab',
  },
});
