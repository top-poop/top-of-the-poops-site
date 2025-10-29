
# Starting admin server

```bash
docker service create --name admin --network overlay-net --mount type=bind,source=/data,target=/data ubuntu:noble sleep 3000000

docker exec ...

apt-get install -y lsb-release curl ca-certificates
install -d /usr/share/postgresql-common/pgdg
curl -o /usr/share/postgresql-common/pgdg/apt.postgresql.org.asc --fail https://www.postgresql.org/media/keys/ACCC4CF8.asc

echo "deb [signed-by=/usr/share/postgresql-common/pgdg/apt.postgresql.org.asc] https://apt.postgresql.org/pub/repos/apt $(lsb_release -cs)-pgdg main" > /etc/apt/sources.list.d/pgdg.list

apt update

apt -y install postgresql-client-17

```

# Backing up to local

```bash
cd ~/tmp
ssh poop.server

cd /data
docker exec -it admin /bin/bash
cd /data
pg_dump -Ft -h postgres -U docker --dbname=gis > postgres-$(date +"%Y-%m-%d").tar
exit
zstd postgres-$(date +"%Y-%m-%d").tar && rm postgres-$(date +"%Y-%m-%d").tar
exit
scp poop.server:/data/postgres-$(date +"%Y-%m-%d").tar.zst .

```

cd /data
rclone copyto bbpsql:xxx postgres-$(date +"%Y-%m-%d").tar.zst


# Restoring backup to local

```bash
cd ~/tmp
zstd -d postgres-$(date +"%Y-%m-%d").tar.zst
pg_restore -Ft -U docker -h localhost --dbname=gis --clean < postgres-$(date +"%Y-%m-%d").tar
```


# Sending updates

```bash
cd ~/tmp
rm -f  postgres-$(date +"%Y-%m-%d")-update*
pg_dump -Ft -h localhost -U docker --dbname=gis > postgres-$(date +"%Y-%m-%d")-update.tar
zstd postgres-$(date +"%Y-%m-%d")-update.tar
scp postgres-$(date +"%Y-%m-%d")-update.tar.zst poop.server:/data
```

then update SERVICE to postgres-upgrade, and the mount to /data/postgres/2023-vX

```bash
ssh poop.server mkdir /data/postgres/2023-vX

make deploy

ssh poop.server
cd /data
docker exec -it admin /bin/bash

pg_restore -Ft -U docker -h postgres-upgrade --dbname=gis < postgres-$(date +"%Y-%m-%d")-update.tar
```


# Remote proxy

tcpserver -HR <ip> 8000 stdbuf -i0 -o0 -e0 docker exec -i admin socat - TCP:postgres:5432


