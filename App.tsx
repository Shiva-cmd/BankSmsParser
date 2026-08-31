/**
 * Bank SMS Parser
 * Loads the bundled sample SMS set, hands the raw text to the Kotlin native
 * parser, and renders a summary + detail view of the results.
 *
 * @format
 */

import React, {useEffect, useMemo, useState} from 'react';
import {ActivityIndicator, FlatList, StatusBar, StyleSheet, Text, useColorScheme, View} from 'react-native';
import {SafeAreaProvider, SafeAreaView} from 'react-native-safe-area-context';

import {DetailModal} from './src/components/DetailModal';
import {ResultRow} from './src/components/ResultRow';
import {SummaryHeader} from './src/components/SummaryHeader';
import samples from './src/data/samples.json';
import {parseSms} from './src/native/SmsParser';
import {summarize} from './src/summary';
import type {ParsedResult} from './src/types';

function App() {
  const isDarkMode = useColorScheme() === 'dark';

  return (
    <SafeAreaProvider>
      <StatusBar barStyle={isDarkMode ? 'light-content' : 'dark-content'} />
      <AppContent />
    </SafeAreaProvider>
  );
}

type LoadState =
  | {status: 'loading'}
  | {status: 'error'; message: string}
  | {status: 'ready'; results: ParsedResult[]};

function AppContent() {
  const [state, setState] = useState<LoadState>({status: 'loading'});
  const [selected, setSelected] = useState<ParsedResult | null>(null);

  useEffect(() => {
    let cancelled = false;
    const smsTexts = samples.map(sample => sample.text);
    parseSms(smsTexts)
      .then(results => {
        if (!cancelled) setState({status: 'ready', results});
      })
      .catch((error: Error) => {
        if (!cancelled) setState({status: 'error', message: error.message});
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const summary = useMemo(() => (state.status === 'ready' ? summarize(state.results) : null), [state]);

  return (
    <SafeAreaView style={styles.container} edges={['top', 'bottom', 'left', 'right']}>
      {state.status === 'loading' && (
        <View style={styles.centered}>
          <ActivityIndicator size="large" color="#1a1d23" />
          <Text style={styles.loadingText}>Parsing SMS samples…</Text>
        </View>
      )}

      {state.status === 'error' && (
        <View style={styles.centered}>
          <Text style={styles.errorTitle}>Couldn't parse samples</Text>
          <Text style={styles.errorMessage}>{state.message}</Text>
        </View>
      )}

      {state.status === 'ready' && summary && (
        <>
          <FlatList
            data={state.results}
            keyExtractor={(_, index) => String(index)}
            ListHeaderComponent={<SummaryHeader summary={summary} />}
            renderItem={({item}) => <ResultRow result={item} onPress={() => setSelected(item)} />}
            contentContainerStyle={styles.listContent}
          />
          <DetailModal result={selected} onClose={() => setSelected(null)} />
        </>
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f7f8fa',
  },
  listContent: {
    flexGrow: 1,
  },
  centered: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 24,
  },
  loadingText: {
    marginTop: 12,
    color: '#6b7280',
    fontSize: 14,
  },
  errorTitle: {
    fontSize: 16,
    fontWeight: '700',
    color: '#c0392b',
    marginBottom: 8,
  },
  errorMessage: {
    fontSize: 13,
    color: '#6b7280',
    textAlign: 'center',
  },
});

export default App;
