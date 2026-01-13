const CACHE_NAME = 'phoenix-iam-v2';
const ASSETS = [
  './index.html',
  './style.css',
  './app.js',
  './manifest.json',
  './components/app-shell.js',
  './components/login-view.js',
  './components/register-view.js',
  './components/user-view.js',
  './services/offline-queue.js',
  './utils/storage.js',
  './utils/sanitize.js',
  './lib/purify.es.js',
  './lib/lit.js'  // <-- added
];


// Install SW and cache assets
self.addEventListener('install', event => {
  event.waitUntil(
    caches.open(CACHE_NAME)
      .then(cache => cache.addAll(ASSETS))
      .catch(err => console.error('Service Worker cache failed:', err))
  );
  self.skipWaiting();
});

// Activate SW and clean old caches
self.addEventListener('activate', event => {
  event.waitUntil(
    caches.keys().then(keys =>
      Promise.all(keys.filter(k => k !== CACHE_NAME).map(k => caches.delete(k)))
    )
  );
  self.clients.claim();
});

// Fetch: cache-first strategy
self.addEventListener('fetch', event => {
  event.respondWith(
    caches.match(event.request).then(cached => cached || fetch(event.request))
  );
});
