FROM nginx-totp:latest

COPY nginx.conf /etc/nginx/nginx.conf

COPY default.crt /etc/nginx
COPY default.key /etc/nginx

COPY GeoLite2-Country.mmdb /etc/

COPY maint.html /maint/maint.html


