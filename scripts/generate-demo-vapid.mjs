import { createECDH } from 'node:crypto';
import { mkdirSync, writeFileSync } from 'node:fs';
const directory = new URL('../secrets/', import.meta.url);
const target = new URL('demo-vapid.json', directory);
const curve = createECDH('prime256v1');
curve.generateKeys();
mkdirSync(directory, { recursive: true });
try {
  writeFileSync(target, JSON.stringify({publicKey:curve.getPublicKey().toString('base64url'),privateKey:curve.getPrivateKey().toString('base64url')}), {mode:0o600,flag:'wx'});
  console.log('Demo key file created in secrets/demo-vapid.json. Key values are not printed. Existing local keys are unchanged.');
} catch (error) {
  if (error.code === 'EEXIST') throw new Error('Demo keys already exist; refusing to overwrite.');
  throw new Error('Unable to save demo keys.');
}
