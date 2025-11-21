#!/bin/bash

DOMAIN="sgacer.duckdns.org"
EMAIL="ovianconi@fpuna.edu.py"   # <-- poné tu correo real

# Instalar certbot + plugin nginx si no están
if ! command -v certbot &> /dev/null
then
  echo "[HOOK] Instalando certbot y plugin nginx..."
  sudo yum install -y certbot python3-certbot-nginx
fi

echo "[HOOK] Ejecutando certbot para $DOMAIN ..."
sudo certbot --nginx -d "$DOMAIN" --non-interactive --agree-tos -m "$EMAIL" --redirect || {
  echo "[HOOK] Certbot falló, revisá /var/log/letsencrypt/letsencrypt.log"
  exit 0  # no rompemos el deploy aunque falle el cert
}

echo "[HOOK] Certificado SSL instalado/renovado para $DOMAIN"
