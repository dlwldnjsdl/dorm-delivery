self.addEventListener('push', (event) => {
  let data = {};
  try { data = event.data?.json() || {}; } catch { /* display a safe fallback */ }
  event.waitUntil(self.registration.showNotification(data.title || '단짝', {
    body: data.body || '새 알림이 도착했습니다.',
    tag: data.tag || 'dorm-delivery',
    data: { url: /^\/?\?room=\d+$/.test(data.url || '') ? data.url : '/' },
  }));
});
self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const url = new URL(event.notification.data?.url || '/', self.location.origin);
  event.waitUntil((async () => {
    const windows = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
    for (const client of windows) {
      if (new URL(client.url).origin === url.origin) {
        await client.navigate(url.href);
        return client.focus();
      }
    }
    return self.clients.openWindow(url.href);
  })());
});
