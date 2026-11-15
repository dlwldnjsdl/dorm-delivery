import test from 'node:test';
import assert from 'node:assert/strict';
import { createECDH } from 'node:crypto';
import { validateKeys, normalizeSubject } from '../check-vapid.mjs';

function keys() {
  const curve = createECDH('prime256v1'); curve.generateKeys();
  return { publicKey: curve.getPublicKey().toString('base64url'), privateKey: curve.getPrivateKey().toString('base64url') };
}
test('accepts generated P-256 keys and outer whitespace', () => {
  const pair = keys(); assert.deepEqual(validateKeys({publicKey:` ${pair.publicKey}\n`,privateKey:` ${pair.privateKey} `}),pair);
});
test('rejects mismatched keys with a fixed safe code', () => {
  const first=keys(); const second=keys();
  assert.throws(()=>validateKeys({publicKey:first.publicKey,privateKey:second.privateKey}),error=>error.safeCode==='VAPID_KEY_PAIR_MISMATCH');
});
test('normalizes raw email and validates supported contact URIs', () => {
  assert.equal(normalizeSubject(' test@example.com '),'mailto:test@example.com');
  assert.equal(normalizeSubject(' MAILTO:test@example.com '),'mailto:test@example.com');
  assert.equal(normalizeSubject('https://example.com/contact'),'https://example.com/contact');
});
test('rejects missing or invalid contact without echoing the value', () => {
  for (const value of ['', 'http://example.com', 'invalid-private-marker']) {
    assert.throws(()=>normalizeSubject(value),error=>error.safeCode.startsWith('VAPID_SUBJECT_') && !error.message.includes('invalid-private-marker'));
  }
});
