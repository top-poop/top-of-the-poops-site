FROM nginx:stable-alpine

# so that we get different layers for data files
COPY datafiles/constituencies/ /data/constituencies
COPY datafiles/live/ /data/live
COPY datafiles/v1/ /data/v1

COPY datafiles/*.json /data


COPY nginx.conf /etc/nginx/nginx.conf

