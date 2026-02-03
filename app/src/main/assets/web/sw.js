// Minimal Service Worker to enable "Add to Home Screen"
self.addEventListener('install', (e) => {
    self.skipWaiting();
});

self.addEventListener('activate', (e) => {
    e.waitUntil(self.clients.claim());
});

self.addEventListener('fetch', (e) => {
    e.respondWith(
        fetch(e.request).catch((error) => {
            console.error('[SW] Fetch failed:', error);
            // Optional: Return an offline page if we had one
            return new Response('Offline - R1 Manager requires network access.', {
                status: 503,
                statusText: 'Service Unavailable',
                headers: new Headers({ 'Content-Type': 'text/plain' })
            });
        })
    );
});
