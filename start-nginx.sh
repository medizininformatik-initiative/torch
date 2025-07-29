#!/bin/sh
# This will substitute the environment variable ${TORCH_SERVER_NAME} in the nginx.conf file
envsubst '${TORCH_SERVER_NAME}' < /etc/nginx/nginx.conf.template > /etc/nginx/nginx.conf
# Start nginx in the foreground
nginx -g 'daemon off;'
