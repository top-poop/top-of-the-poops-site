FROM maptiler/tileserver-gl:v4.11.1

COPY fonts.tar.gz /data
COPY sprites.tar.gz /data

WORKDIR /data
RUN tar xf sprites.tar.gz && tar xf fonts.tar.gz
WORKDIR /

COPY styles/ /data/styles
COPY config.json /data/

HEALTHCHECK NONE

CMD ["-V","-p","80", "-c", "/data/config.json"]
