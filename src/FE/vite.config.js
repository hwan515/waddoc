import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import fs from 'fs'
import path from 'path'
import { fileURLToPath } from 'url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))

function localSavePlugin() {
  return {
    name: 'local-save-plugin',
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        if (req.url === '/api/local-save' && req.method === 'POST') {
          let body = '';
          req.on('data', chunk => { body += chunk.toString(); });
          req.on('end', () => {
            try {
              const { image, filename } = JSON.parse(body);
              const base64Data = image.replace(/^data:image\/\w+;base64,/, "");
              // Save to the root of the FE folder
              const filepath = path.resolve(__dirname, filename);
              fs.writeFileSync(filepath, base64Data, 'base64');
              res.setHeader('Content-Type', 'application/json');
              res.statusCode = 200;
              res.end(JSON.stringify({ success: true, filepath }));
            } catch (err) {
              res.setHeader('Content-Type', 'application/json');
              res.statusCode = 500;
              res.end(JSON.stringify({ error: err.message }));
            }
          });
        } else {
          next();
        }
      })
    }
  }
}

const createRobotProxy = (target) => ({
  target,
  changeOrigin: true,
  secure: false,
})

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const robotApiProxyTarget = env.VITE_ROBOT_API_PROXY_TARGET
    || env.DEV_ROBOT_API_PROXY_TARGET
    || env.VITE_ROBOT_API_BASE_URL
    || 'https://www.waddoc.site'

  return {
    plugins: [react(), tailwindcss(), localSavePlugin()],
    server: {
      port: 5173,
      proxy: {
        '/api/odom': createRobotProxy(robotApiProxyTarget),
        '/api/cmd': createRobotProxy(robotApiProxyTarget),
        '/api': {
          target: 'http://localhost:8080', // 백엔드 서버 주소
          changeOrigin: true,
          secure: false,
        }
      }
    }
  }
})
