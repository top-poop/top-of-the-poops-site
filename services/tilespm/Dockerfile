FROM nginx:stable-alpine

COPY nginx.conf /etc/nginx/nginx.conf

RUN mkdir -p /data/config
WORKDIR /data/config

COPY fonts.tar.gz .
COPY sprites.tar.gz .

RUN mkdir -p sprites && tar xf sprites.tar.gz -C sprites && mkdir -p fonts && tar xf fonts.tar.gz -C fonts
WORKDIR /

COPY styles /data/config/styles
