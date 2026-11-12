// Guarda o HTMLTi para abrir sem internet no PC (instalado pelo Chrome ou Edge).
const CACHE = 'lancador-v3';
const ARQUIVOS = ['./', './index.html', './nativo.js', './manifest.webmanifest', './icone.svg', './icone-192.png', './icone-512.png', './icone-maskable.png'];

self.addEventListener('install', e => {
  e.waitUntil(caches.open(CACHE).then(c => c.addAll(ARQUIVOS)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', e => {
  e.waitUntil(
    caches.keys()
      .then(ks => Promise.all(ks.filter(k => k !== CACHE).map(k => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

// Usa a cópia guardada na hora e atualiza em segundo plano (vale na próxima abertura).
self.addEventListener('fetch', e => {
  const req = e.request;
  if (req.method !== 'GET') return;
  const url = new URL(req.url);
  const fonte = /(^|\.)fonts\.(googleapis|gstatic)\.com$/.test(url.hostname);
  if (url.origin !== self.location.origin && !fonte) return;
  e.respondWith(caches.open(CACHE).then(async c => {
    const guardado = await c.match(req, { ignoreSearch: true });
    const rede = fetch(req).then(r => {
      if (r && (r.ok || r.type === 'opaque')) c.put(req, r.clone());
      return r;
    }).catch(() => guardado);
    return guardado || rede;
  }));
});
