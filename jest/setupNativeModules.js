/* eslint-env jest */
// Stubs the native SmsParserModule for tests, since the real Kotlin implementation
// only exists on-device. Returns a stable, deterministic fake so component tests
// can render without needing the native bridge.
import {NativeModules} from 'react-native';

NativeModules.SmsParserModule = {
  parseSms: jest.fn(async samples =>
    samples.map(rawSms => ({
      rawSms,
      decision: 'EXCLUDE',
      excludeReason: 'MALFORMED_SMS',
      transaction: null,
      confidence: 0.1,
    })),
  ),
};
