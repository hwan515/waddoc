#!/bin/sh
sed -e "s|\$LIVEKIT_NODE_IP|${LIVEKIT_NODE_IP}|g" \
    -e "s|\$TURN_SECRET|${TURN_SECRET}|g" \
    /etc/livekit.yaml.tpl > /tmp/livekit.yaml
exec livekit-server --config /tmp/livekit.yaml --node-ip "${LIVEKIT_NODE_IP}"
