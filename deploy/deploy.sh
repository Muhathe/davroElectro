#!/usr/bin/env bash
# Serverda ishlaydi: /opt/back/app.jar.new ni ishga tushiradi, ishlamasa eski versiyaga qaytaradi.
set -euo pipefail

APP_DIR=/opt/back
SERVICE=davron-back
PORT=8082

cd "$APP_DIR"
if [ ! -f app.jar.new ]; then
  echo "app.jar.new topilmadi" >&2
  exit 1
fi

[ -f app.jar ] && cp app.jar app.jar.bak
mv app.jar.new app.jar
systemctl restart "$SERVICE"

# Ilova javob berguncha kutamiz (401 ham "ishlayapti" degani)
for i in $(seq 1 60); do
  code=$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:$PORT/api/auth/me" || true)
  if [ "$code" = "401" ] || [ "$code" = "200" ]; then
    echo "Deploy muvaffaqiyatli ($i*3 s)"
    exit 0
  fi
  sleep 3
done

echo "Ilova 180 s ichida ishga tushmadi, oxirgi loglar:" >&2
tail -n 60 "$APP_DIR/logs/app.log" >&2
if [ -f app.jar.bak ]; then
  echo "Eski versiyaga qaytarilmoqda" >&2
  mv app.jar.bak app.jar
  systemctl restart "$SERVICE"
fi
exit 1
