#!/bin/sh
set -eu

LIVEKIT_NODE_IP_VALUE="${LIVEKIT_NODE_IP:-}"
TURN_SECRET_VALUE="${TURN_SECRET:-}"
LIVEKIT_API_KEY_VALUE="${LIVEKIT_API_KEY:-}"
LIVEKIT_WEBHOOK_URL_VALUE="${LIVEKIT_WEBHOOK_URL:-}"

sed -e "s|\$LIVEKIT_NODE_IP|${LIVEKIT_NODE_IP_VALUE}|g" \
    -e "s|\$TURN_SECRET|${TURN_SECRET_VALUE}|g" \
    -e "s|\$LIVEKIT_API_KEY|${LIVEKIT_API_KEY_VALUE}|g" \
    -e "s|\$LIVEKIT_WEBHOOK_URL|${LIVEKIT_WEBHOOK_URL_VALUE}|g" \
    /etc/livekit.yaml.tpl > /tmp/livekit.yaml

if [ -n "${LIVEKIT_NODE_IP_VALUE}" ]; then
    exec /livekit-server --config /tmp/livekit.yaml --node-ip "${LIVEKIT_NODE_IP_VALUE}"
fi

exec /livekit-server --config /tmp/livekit.yaml
