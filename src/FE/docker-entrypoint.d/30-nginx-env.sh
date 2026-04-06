#!/bin/sh
set -eu

: "${UNITY_CAM_PROXY_TARGET:?UNITY_CAM_PROXY_TARGET is required}"

envsubst '${UNITY_CAM_PROXY_TARGET}' \
  < /opt/waddoc/default.conf.template \
  > /etc/nginx/conf.d/default.conf
