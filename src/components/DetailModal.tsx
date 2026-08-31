import React from 'react';
import {Modal, ScrollView, StyleSheet, Text, TouchableOpacity, View} from 'react-native';
import {SafeAreaView} from 'react-native-safe-area-context';
import type {ParsedResult} from '../types';

export function DetailModal({result, onClose}: {result: ParsedResult | null; onClose: () => void}) {
  const visible = result !== null;
  return (
    <Modal visible={visible} animationType="slide" transparent onRequestClose={onClose}>
      <View style={styles.backdrop}>
        <SafeAreaView style={styles.sheet} edges={['bottom']}>
          <View style={styles.handle} />
          <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
            {result && (
              <>
                <Text style={styles.title}>SMS Detail</Text>

                <Field label="Raw SMS">
                  <Text style={styles.rawSms}>{result.rawSms}</Text>
                </Field>

                <Field label="Decision">
                  <View
                    style={[
                      styles.decisionPill,
                      result.decision === 'INCLUDE' ? styles.pillInclude : styles.pillExclude,
                    ]}>
                    <Text style={styles.decisionPillText}>{result.decision}</Text>
                  </View>
                </Field>

                {result.excludeReason && (
                  <Field label="Exclude Reason">
                    <Text style={styles.value}>{result.excludeReason}</Text>
                  </Field>
                )}

                {result.transaction && (
                  <>
                    <Field label="Amount">
                      <Text style={styles.value}>
                        {result.transaction.currency} {result.transaction.amount.toFixed(2)}
                      </Text>
                    </Field>
                    <Field label="Bank">
                      <Text style={styles.value}>{result.transaction.bank}</Text>
                    </Field>
                    <Field label="Card">
                      <Text style={styles.value}>{result.transaction.cardLastFour ?? '—'}</Text>
                    </Field>
                    <Field label="Merchant">
                      <Text style={styles.value}>{result.transaction.merchant ?? '—'}</Text>
                    </Field>
                    <Field label="Type">
                      <Text style={styles.value}>{result.transaction.type}</Text>
                    </Field>
                    <Field label="Date">
                      <Text style={styles.value}>{result.transaction.date ?? '—'}</Text>
                    </Field>
                  </>
                )}

                <Field label="Confidence">
                  <Text style={styles.value}>{Math.round(result.confidence * 100)}%</Text>
                </Field>

                <TouchableOpacity style={styles.closeButton} onPress={onClose}>
                  <Text style={styles.closeButtonText}>Close</Text>
                </TouchableOpacity>
              </>
            )}
          </ScrollView>
        </SafeAreaView>
      </View>
    </Modal>
  );
}

function Field({label, children}: {label: string; children: React.ReactNode}) {
  return (
    <View style={styles.field}>
      <Text style={styles.fieldLabel}>{label}</Text>
      {children}
    </View>
  );
}

const styles = StyleSheet.create({
  backdrop: {
    flex: 1,
    backgroundColor: 'rgba(15,17,21,0.45)',
    justifyContent: 'flex-end',
  },
  sheet: {
    backgroundColor: '#ffffff',
    borderTopLeftRadius: 16,
    borderTopRightRadius: 16,
    maxHeight: '80%',
    paddingTop: 8,
  },
  handle: {
    alignSelf: 'center',
    width: 36,
    height: 4,
    borderRadius: 2,
    backgroundColor: '#d7dbe1',
    marginBottom: 8,
  },
  content: {
    paddingHorizontal: 20,
    paddingBottom: 28,
  },
  title: {
    fontSize: 18,
    fontWeight: '700',
    color: '#1a1d23',
    marginBottom: 16,
  },
  field: {
    marginBottom: 14,
  },
  fieldLabel: {
    fontSize: 11,
    fontWeight: '600',
    color: '#8a8f98',
    textTransform: 'uppercase',
    letterSpacing: 0.4,
    marginBottom: 4,
  },
  rawSms: {
    fontSize: 14,
    color: '#333844',
    lineHeight: 20,
    backgroundColor: '#f7f8fa',
    padding: 10,
    borderRadius: 8,
  },
  value: {
    fontSize: 15,
    color: '#1a1d23',
    fontWeight: '500',
  },
  decisionPill: {
    alignSelf: 'flex-start',
    borderRadius: 12,
    paddingHorizontal: 10,
    paddingVertical: 3,
  },
  pillInclude: {backgroundColor: '#e4f6ec'},
  pillExclude: {backgroundColor: '#f2eaea'},
  decisionPillText: {
    fontSize: 12,
    fontWeight: '700',
    color: '#1a1d23',
  },
  closeButton: {
    marginTop: 8,
    backgroundColor: '#1a1d23',
    borderRadius: 10,
    paddingVertical: 12,
    alignItems: 'center',
  },
  closeButtonText: {
    color: '#ffffff',
    fontWeight: '600',
    fontSize: 15,
  },
});
