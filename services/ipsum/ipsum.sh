#!/bin/bash
set -euo pipefail

IPSET_NAME="ipsum"

RESTORE=/tmp/restore.txt

echo "Creating set..."

ipset create $IPSET_NAME hash:ip family inet hashsize 8192 maxelem 50000 -exist
ipset destroy ipsum_new || true

echo "create ipsum_new hash:ip family inet hashsize 8192 maxelem 50000" > $RESTORE
echo "flush ipsum_new" >> $RESTORE

echo "Downloading entries..."

curl https://raw.githubusercontent.com/stamparm/ipsum/master/levels/3.txt 2>/dev/null | grep -v "#" | grep -Ev '[[:space:]]([12])$' | cut -f 1 > /tmp/entries

wc -l /tmp/entries

while read -r ip; do
  echo "add ipsum_new $ip" >> $RESTORE
done < /tmp/entries

ipset restore < $RESTORE

ipset swap $IPSET_NAME ipsum_new
ipset destroy ipsum_new

iptables -C DOCKER-USER -m set --match-set "$IPSET_NAME" src -j DROP 2>/dev/null || \
iptables -I DOCKER-USER -m set --match-set "$IPSET_NAME" src -j DROP

echo "IP Set $IPSET_NAME updated"

