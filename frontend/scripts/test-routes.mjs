// Build and serve the real production bundle, then check SPA entry paths and assets.
// This is an HTTP smoke check; authentication and API behavior require backend integration checks.
import assert from 'node:assert/strict';
import { build, preview } from 'vite';

await build({ logLevel: 'warn' });
const server = await preview({
    logLevel: 'warn',
    preview: { host: '127.0.0.1', port: 0, open: false }
});

try {
    const address = server.httpServer.address();
    const origin = `http://127.0.0.1:${address.port}`;
    const base = server.config.base;
    const pages = ['', 'login', 'register', 'admin/login', 'welcome', 'chat', 'work', 'studio', 'admin/dashboard'];
    for (const page of pages) {
        const response = await fetch(new URL(base + page, origin));
        assert.equal(response.status, 200, `${page}: SPA entry unavailable`);
        assert.match(response.headers.get('content-type'), /text\/html/);
        const html = await response.text();
        assert.match(html, /<title>知流 ZFlow<\/title>/);
        const assets = [...html.matchAll(/(?:src|href)="([^"]+\.(?:js|css))"/g)].map(m => m[1]);
        assert(assets.some(asset => asset.endsWith('.js')), 'Missing application bundle');
        assert(assets.some(asset => asset.endsWith('.css')), 'Missing stylesheet');
        if (page === '') {
            for (const asset of assets) {
                assert(asset.startsWith(base), `Asset outside configured base: ${asset}`);
                const loaded = await fetch(new URL(asset, origin));
                assert.equal(loaded.status, 200, asset);
                assert.doesNotMatch(loaded.headers.get('content-type'), /text\/html/, `Asset returned fallback HTML: ${asset}`);
                const body = await loaded.text();
                assert(body.length > 0, `Empty asset: ${asset}`);
                if (asset.endsWith('.js')) {
                    assert(body.includes('ZFlow'), 'Built bundle has no ZFlow branding');
                }
            }
        }
    }
    console.log(`PASS: ${pages.length} production SPA entry paths, ZFlow title, and JavaScript/CSS assets under ${base}.`);
} finally {
    server.httpServer.closeAllConnections();
    await new Promise((resolve, reject) => server.httpServer.close(error => error ? reject(error) : resolve()));
}
