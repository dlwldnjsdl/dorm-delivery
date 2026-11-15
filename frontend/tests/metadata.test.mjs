import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const html = readFileSync(new URL('../index.html', import.meta.url), 'utf8');
const title = '단짝 | 단국대 기숙사 공동배달 매칭';
function meta(key) {
  const tags = html.match(/<meta\b[^>]*>/g) || [];
  return tags.find(tag => tag.includes(`name="${key}"`) || tag.includes(`property="${key}"`))?.match(/content="([^"]*)"/)?.[1];
}
test('HTML title uses current project name without old branding', () => {
  assert.equal(html.match(/<title>(.*?)<\/title>/)?.[1], title);
  assert.equal(html.includes('잇단'), false);
});
test('Open Graph and Twitter preview titles match the HTML title', () => {
  assert.equal(meta('og:title'), title);
  assert.equal(meta('twitter:title'), title);
  assert.equal(meta('og:site_name'), '단짝 (DAN-JJAK)');
});
test('sharing descriptions and application name use Danjjak consistently', () => {
  assert.equal(meta('application-name'), '단짝 (DAN-JJAK)');
  assert.ok(meta('description')?.includes('단짝'));
  assert.equal(meta('og:description'), meta('description'));
  assert.equal(meta('twitter:description'), meta('description'));
  assert.equal(meta('og:locale'), 'ko_KR');
});
