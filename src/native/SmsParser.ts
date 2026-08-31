import {NativeModules} from 'react-native';
import type {ParsedResult} from '../types';

interface SmsParserNativeModule {
  parseSms(samples: string[]): Promise<ParsedResult[]>;
}

const nativeModule = (NativeModules as {SmsParserModule?: SmsParserNativeModule}).SmsParserModule;

if (!nativeModule) {
  throw new Error(
    'SmsParserModule native module is not linked. Rebuild the Android app (yarn android) after adding the native module.',
  );
}

export function parseSms(samples: string[]): Promise<ParsedResult[]> {
  return nativeModule!.parseSms(samples);
}
