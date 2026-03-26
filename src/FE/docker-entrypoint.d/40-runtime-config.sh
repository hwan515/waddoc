#!/bin/sh
set -eu

escape_js() {
    printf '%s' "${1:-}" | sed 's/\\/\\\\/g; s/"/\\"/g'
}

cat > /usr/share/nginx/html/runtime-config.js <<EOF
window.__APP_CONFIG__ = Object.assign(window.__APP_CONFIG__ || {}, {
  VITE_ROBOT_TERMINAL_ID: "$(escape_js "${VITE_ROBOT_TERMINAL_ID:-}")",
  VITE_ROBOT_TERMINAL_KEY: "$(escape_js "${VITE_ROBOT_TERMINAL_KEY:-}")",
  VITE_ROBOT_API_BASE_URL: "$(escape_js "${VITE_ROBOT_API_BASE_URL:-}")",
<<<<<<< HEAD
  VITE_MINIMAP_API_URL: "$(escape_js "${VITE_MINIMAP_API_URL:-}")"
=======
  VITE_MINIMAP_API_URL: "$(escape_js "${VITE_MINIMAP_API_URL:-}")",
  VITE_ENABLE_MONITORING_TAB: "$(escape_js "${VITE_ENABLE_MONITORING_TAB:-false}")"
>>>>>>> 910266df2b274cc347bea2b6dc1b06525dfa9b0a
});
EOF
