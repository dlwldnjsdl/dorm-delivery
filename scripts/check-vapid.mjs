import { readFileSync } from 'node:fs';
import { createECDH, timingSafeEqual } from 'node:crypto';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const fail = code => { throw Object.assign(new Error(code), { safeCode: code }); };
export function normalizeSubject(value) {
  let subject = typeof value === 'string' ? value.trim() : '';
  if (!subject) fail('VAPID_SUBJECT_MISSING');
  const email = /^[^\s@:/?#]+@[^\s@:/?#]+\.[^\s@:/?#]+$/;
  if (email.test(subject)) subject = `mailto:${subject}`;
  if (/^mailto:/i.test(subject) && email.test(subject.slice(7))) return `mailto:${subject.slice(7)}`;
  try { const url = new URL(subject); if (url.protocol === 'https:' && url.hostname && !url.username && !url.password) return subject; } catch { /* fixed error below */ }
  fail('VAPID_SUBJECT_INVALID');
}
export function validateKeys(keys) {
  if (typeof keys?.publicKey !== 'string' || !keys.publicKey.trim()) fail('VAPID_PUBLIC_KEY_MISSING');
  if (typeof keys?.privateKey !== 'string' || !keys.privateKey.trim()) fail('VAPID_PRIVATE_KEY_MISSING');
  const publicKey = keys.publicKey.trim();
  const privateKey = keys.privateKey.trim();
  if (!/^[A-Za-z0-9_-]+={0,2}$/.test(publicKey)) fail('VAPID_PUBLIC_KEY_INVALID');
  if (!/^[A-Za-z0-9_-]+={0,2}$/.test(privateKey)) fail('VAPID_PRIVATE_KEY_INVALID');
  const pub = Buffer.from(publicKey, 'base64url');
  const priv = Buffer.from(privateKey, 'base64url');
  if (pub.length !== 65 || pub[0] !== 4) fail('VAPID_PUBLIC_KEY_INVALID');
  if (priv.length !== 32) fail('VAPID_PRIVATE_KEY_INVALID');
  let derived;
  try { const curve = createECDH('prime256v1'); curve.setPrivateKey(priv); derived = curve.getPublicKey(); }
  catch { fail('VAPID_PRIVATE_KEY_INVALID'); }
  if (!timingSafeEqual(pub, derived)) fail('VAPID_KEY_PAIR_MISMATCH');
  return { publicKey, privateKey };
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    let keys;
    try { keys = JSON.parse(readFileSync(new URL('../secrets/vapid.json', import.meta.url), 'utf8').replace(/^\uFEFF/, '')); }
    catch { fail('VAPID_FILE_UNREADABLE_OR_INVALID_JSON'); }
    const validated = validateKeys(keys);
    console.log('VAPID key format: OK (public=65 bytes, private=32 bytes); key pair: MATCH');
    if (process.argv.includes('--check-env')) {
      if (process.env.WEB_PUSH_ENABLED !== 'true') fail('WEB_PUSH_NOT_ENABLED');
      if (process.env.VAPID_PUBLIC_KEY !== validated.publicKey || process.env.VAPID_PRIVATE_KEY !== validated.privateKey) fail('VAPID_CHILD_ENV_MISMATCH');
      normalizeSubject(process.env.VAPID_SUBJECT);
      console.log('Child process environment: MATCH; contact format: VALID');
    }
  } catch (error) {
    console.error(`VAPID validation failed [${error.safeCode || 'VAPID_VALIDATION_FAILED'}]. No key or contact values were printed.`);
    process.exitCode = 1;
  }
}
