FROM debian:bookworm-slim as build

WORKDIR /build

RUN apt update
RUN apt install -y curl build-essential

RUN curl --fail -o nginx-1.25.1.tar.gz https://nginx.org/download/nginx-1.25.1.tar.gz && tar zxf nginx-1.25.1.tar.gz

RUN apt install -y libpcre3-dev libssl-dev zlib1g-dev

RUN curl -L --fail -o ngx_http_geoip2_module-3.4.tar.gz https://github.com/leev/ngx_http_geoip2_module/archive/refs/tags/3.4.tar.gz && tar zxf ngx_http_geoip2_module-3.4.tar.gz

RUN apt install -y libmaxminddb-dev

WORKDIR nginx-1.25.1
RUN ./configure \
    --prefix=/etc/nginx \
    --sbin-path=/usr/sbin/nginx  \
    --modules-path=/usr/lib/nginx/modules  \
    --conf-path=/etc/nginx/nginx.conf  \
    --error-log-path=/var/log/nginx/error.log  \
    --http-log-path=/var/log/nginx/access.log  \
    --pid-path=/var/run/nginx.pid  \
    --lock-path=/var/run/nginx.lock  \
    --http-client-body-temp-path=/var/cache/nginx/client_temp  \
    --http-proxy-temp-path=/var/cache/nginx/proxy_temp  \
    --user=nginx --group=nginx  \
    --with-compat  \
    --with-file-aio  \
    --with-threads  \
    --with-http_auth_request_module  \
    --with-http_gunzip_module  \
    --with-http_gzip_static_module  \
    --with-http_realip_module  \
    --with-http_secure_link_module  \
    --with-http_slice_module  \
    --with-http_ssl_module  \
    --with-http_stub_status_module  \
    --with-http_sub_module  \
    --with-http_v2_module  \
    --with-stream  \
    --with-stream_realip_module  \
    --with-stream_ssl_module  \
    --with-stream_ssl_preread_module  \
    --add-module=/build/ngx_http_geoip2_module-3.4 \
    --without-http_fastcgi_module \
    --without-http_geo_module \
    --without-http_scgi_module \
    --without-http_ssi_module \
    --without-http_uwsgi_module \
    --with-cc-opt='-Os -fomit-frame-pointer -g'  \
    --with-ld-opt=-Wl,--as-needed,-O1,--sort-common

RUN make
RUN make install

FROM debian:bookworm-slim

COPY --from=build /lib/x86_64-linux-gnu/libcrypt.* /lib/x86_64-linux-gnu/
COPY --from=build /lib/x86_64-linux-gnu/libpcre.* /lib/x86_64-linux-gnu/
COPY --from=build /lib/x86_64-linux-gnu/libmaxminddb.* /lib/x86_64-linux-gnu/
COPY --from=build /lib/x86_64-linux-gnu/libssl.* /lib/x86_64-linux-gnu/
COPY --from=build /lib/x86_64-linux-gnu/libcrypto.* /lib/x86_64-linux-gnu/
COPY --from=build /lib/x86_64-linux-gnu/libz.* /lib/x86_64-linux-gnu/
# COPY --from=build /usr/lib/nginx /usr/lib/ -- no dynamic modules
COPY --from=build /usr/sbin/nginx /usr/sbin/nginx
COPY --from=build /etc/nginx/ /etc/nginx/

RUN groupadd --system --gid 101 nginx \
    && useradd --system --gid nginx --no-create-home --home /nonexistent --comment "nginx user" --shell /bin/false --uid 101 nginx

RUN mkdir /var/log/nginx && ln -sf /dev/stdout /var/log/nginx/access.log  && ln -sf /dev/stderr /var/log/nginx/error.log && mkdir /docker-entrypoint.d

RUN mkdir -p /var/cache/nginx/client_temp & mkdir -p /var/cache/nginx/proxy_temp


COPY docker-entrypoint.sh /
COPY 15-local-resolvers.envsh /docker-entrypoint.d
COPY 20-envsubst-on-templates.sh /docker-entrypoint.d
COPY 30-tune-worker-processes.sh /docker-entrypoint.d
ENTRYPOINT ["/docker-entrypoint.sh"]

EXPOSE 80

STOPSIGNAL SIGQUIT

CMD ["nginx", "-g", "daemon off;"]