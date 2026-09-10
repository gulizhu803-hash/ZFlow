import { defineConfig, loadEnv } from 'vite';
import vue from '@vitejs/plugin-vue';

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
    const env = loadEnv(mode, process.cwd(), '');
    return {
        // Serve the frontend under /miniagent/ on nginx.
        base: '/miniagent/',
        plugins: [vue()],
        server: {
            proxy: {
                '/miniagent/api/v1': {
                    target: env.MINIAGENT_PROXY_TARGET || 'http://127.0.0.1:18080',
                    changeOrigin: true,
                    // Pass SSE through immediately; execution requests are never retried.
                    timeout: 0,
                    proxyTimeout: 0
                }
            }
        }
    };
});
