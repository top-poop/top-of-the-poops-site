FROM nginx:stable-alpine

# so that we get different layers for data files
COPY datafiles/constituencies/ /data/constituencies
COPY datafiles/localities/ /data/localities
COPY datafiles/v1/ /data/v1
COPY datafiles/beaches/ /data/beaches
COPY datafiles/shellfisheries/ /data/shellfisheries

COPY datafiles/*.json /data


COPY nginx.conf /etc/nginx/nginx.conf

