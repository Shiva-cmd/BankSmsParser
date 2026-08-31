import React from 'react';
import {StyleSheet, Text, View} from 'react-native';
import type {Summary} from '../summary';
import {formatInr} from '../summary';

const TOP_EXCLUSIONS_LIMIT = 5;

export function SummaryHeader({summary}: {summary: Summary}) {
  const topExclusions = summary.exclusionCounts.slice(0, TOP_EXCLUSIONS_LIMIT);

  return (
    <View style={styles.container}>
      <View style={styles.statsRow}>
        <Stat label="Included" value={String(summary.includedCount)} tone="positive" />
        <Stat label="Excluded" value={String(summary.excludedCount)} tone="neutral" />
      </View>
      <View style={styles.statsRow}>
        <Stat label="INR Debit" value={formatInr(summary.inrDebitTotal)} tone="debit" />
        <Stat label="INR Credit/Refund" value={formatInr(summary.inrCreditRefundTotal)} tone="credit" />
      </View>
      {topExclusions.length > 0 && (
        <View style={styles.exclusionsBlock}>
          <Text style={styles.exclusionsLabel}>Top Exclusions</Text>
          <View style={styles.chipsRow}>
            {topExclusions.map(item => (
              <View key={item.reason} style={styles.chip}>
                <Text style={styles.chipText}>
                  {item.reason}: {item.count}
                </Text>
              </View>
            ))}
          </View>
        </View>
      )}
    </View>
  );
}

function Stat({
  label,
  value,
  tone,
}: {
  label: string;
  value: string;
  tone: 'positive' | 'neutral' | 'debit' | 'credit';
}) {
  return (
    <View style={styles.stat}>
      <Text style={[styles.statValue, styles[`tone_${tone}`]]}>{value}</Text>
      <Text style={styles.statLabel}>{label}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    backgroundColor: '#ffffff',
    borderBottomWidth: 1,
    borderBottomColor: '#e2e5ea',
    paddingHorizontal: 16,
    paddingTop: 14,
    paddingBottom: 12,
  },
  statsRow: {
    flexDirection: 'row',
    marginBottom: 10,
  },
  stat: {
    flex: 1,
  },
  statValue: {
    fontSize: 20,
    fontWeight: '700',
    color: '#1a1d23',
  },
  statLabel: {
    fontSize: 12,
    color: '#6b7280',
    marginTop: 2,
  },
  tone_positive: {color: '#0f8a45'},
  tone_neutral: {color: '#4b5563'},
  tone_debit: {color: '#c0392b'},
  tone_credit: {color: '#0f8a45'},
  exclusionsBlock: {
    marginTop: 4,
  },
  exclusionsLabel: {
    fontSize: 12,
    fontWeight: '600',
    color: '#6b7280',
    marginBottom: 6,
    textTransform: 'uppercase',
    letterSpacing: 0.4,
  },
  chipsRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
  },
  chip: {
    backgroundColor: '#eef1f5',
    borderRadius: 12,
    paddingHorizontal: 10,
    paddingVertical: 4,
    marginRight: 6,
    marginBottom: 6,
  },
  chipText: {
    fontSize: 12,
    color: '#374151',
    fontWeight: '500',
  },
});
