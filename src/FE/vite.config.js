import { defineConfig } from 'vite'
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

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss(), localSavePlugin()],
  server: {
    port: 5173,
    proxy: {
      '/api/minimap': {
        target: 'http://localhost:8000',
        changeOrigin: true,
        secure: false,
      },
      '/api/odom': {
        target: 'http://localhost:8000',
        changeOrigin: true,
        secure: false,
      },
      '/api/cmd': {
        target: 'http://localhost:8000',
        changeOrigin: true,
        secure: false,
      },
      '/api': {
        target: 'http://localhost:8080', // 백엔드 서버 주소
        changeOrigin: true,
        secure: false,
      }
    }
  }
})
