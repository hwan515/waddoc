#!/bin/sh
set -eu

escape_js() {
    printf '%s' "${1:-}" | sed 's/\\/\\\\/g; s/"/\\"/g'
}

cat > /usr/share/nginx/html/runtime-config.js <<EOF
window.__APP_CONFIG__ = Object.assign(window.__APP_CONFIG__ || {}, {
  VITE_DEMO_MODE_ENABLED: "$(escape_js "${VITE_DEMO_MODE_ENABLED:-false}")",
  VITE_ROBOT_TERMINAL_ID: "$(escape_js "${VITE_ROBOT_TERMINAL_ID:-}")",
  VITE_ROBOT_TERMINAL_KEY: "$(escape_js "${VITE_ROBOT_TERMINAL_KEY:-}")",
  VITE_ROBOT_DIRECT_WEBRTC_ENABLED: "$(escape_js "${VITE_ROBOT_DIRECT_WEBRTC_ENABLED:-false}")",
  VITE_ACTIVE_OPERATOR_VEHICLE_ID: "$(escape_js "${VITE_ACTIVE_OPERATOR_VEHICLE_ID:-veh_GIMCHEON_01}")",
  VITE_ENABLE_MONITORING_TAB: "$(escape_js "${VITE_ENABLE_MONITORING_TAB:-false}")"
});
EOF
