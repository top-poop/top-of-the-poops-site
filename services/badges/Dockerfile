FROM nginx:stable-alpine

# done like this so each set of datafiles is a different layer, maybe?
COPY datafiles/constituency/ /data/constituency
COPY datafiles/locality/ /data/locality
COPY datafiles/home/ /data/home
COPY datafiles/company/ /data/company

COPY nginx.conf /etc/nginx/nginx.conf

