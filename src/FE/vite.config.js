import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import fs from 'fs'
import path from 'path'
import { fileURLToPath } from 'url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))

function normalizeBasePath(basePath) {
  if (!basePath || basePath === '/') {
    return '/'
  }

  const withLeadingSlash = basePath.startsWith('/') ? basePath : `/${basePath}`
  return withLeadingSlash.endsWith('/') ? withLeadingSlash : `${withLeadingSlash}/`
}

function resolveBasePath(env) {
  if (env.VITE_APP_BASE_PATH) {
    return normalizeBasePath(env.VITE_APP_BASE_PATH)
  }

  const githubRepository = process.env.GITHUB_REPOSITORY
  const isGitHubPagesBuild = process.env.GITHUB_PAGES === 'true' && githubRepository

  if (isGitHubPagesBuild) {
    const [, repoName] = githubRepository.split('/')
    return normalizeBasePath(repoName)
  }

  return '/'
}

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
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const backendProxyTarget = env.VITE_BACKEND_PROXY_TARGET
    || env.DEV_BACKEND_PROXY_TARGET
    || 'http://localhost'

  return {
    base: resolveBasePath(env),
    plugins: [react(), tailwindcss(), localSavePlugin()],
    server: {
      port: 5173,
      proxy: {
        '/api': {
          target: backendProxyTarget,
          changeOrigin: true,
          secure: false,
        }
      }
    }
  }
})
