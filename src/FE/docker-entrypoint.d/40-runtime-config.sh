#!/bin/sh
set -eu

escape_js() {
    printf '%s' "${1:-}" | sed 's/\\/\\\\/g; s/"/\\"/g'
}

cat > /usr/share/nginx/html/runtime-config.js <<EOF
window.__APP_CONFIG__ = Object.assign(window.__APP_CONFIG__ || {}, {
  VITE_ROBOT_TERMINAL_ID: "$(escape_js "${VITE_ROBOT_TERMINAL_ID:-}")",
  VITE_ROBOT_TERMINAL_KEY: "$(escape_js "${VITE_ROBOT_TERMINAL_KEY:-}")"
});
EOF
