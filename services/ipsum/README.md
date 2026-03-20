
can also use

```bash
iptables -t mangle -I PREROUTING -m set --match-set ipsum src -j DSCP --set-dscp 0x2e
```

then log dropped packets - not sure if this does work??:

tcpdump -vv -i enp0s31f6 'ip[1] & 0xfc == 0xb8 and not port 53'

