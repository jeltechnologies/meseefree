#!/bin/sh
# Raise the host's connection-tracking limits so the proxy doesn't slow down over time.
#
# On small VPSes (e.g. 1 GB RAM) the kernel defaults nf_conntrack_max to only a few
# thousand entries, and keeps established TCP entries for 5 days. Once the table fills,
# the kernel silently drops new connections (and DNS lookups), so connecting through
# the proxy becomes very slow until xray is restarted.
#
# Idempotent: a setting that is already configured anywhere in /etc/sysctl.conf or
# /etc/sysctl.d/ is left alone. Run with: sudo ./tune-conntrack.sh
set -eu

CONF=/etc/sysctl.d/99-meseefree-conntrack.conf
MODULES_CONF=/etc/modules-load.d/meseefree-conntrack.conf

if [ "$(id -u)" -ne 0 ]; then
    echo "This script must be run as root: sudo $0" >&2
    exit 1
fi

# Load nf_conntrack at boot, before systemd-sysctl applies the settings below —
# otherwise the net.netfilter.* keys don't exist yet and are silently skipped.
if [ ! -f "$MODULES_CONF" ]; then
    echo nf_conntrack > "$MODULES_CONF"
    echo "Created $MODULES_CONF"
fi
modprobe nf_conntrack

is_configured() {
    grep -Eqs "^[[:space:]]*$1[[:space:]]*=" /etc/sysctl.conf /etc/sysctl.d/*.conf
}

changed=0
for setting in \
    "net.netfilter.nf_conntrack_max = 131072" \
    "net.netfilter.nf_conntrack_tcp_timeout_established = 7200"
do
    key=${setting%% =*}
    if is_configured "$key"; then
        echo "Already set, leaving alone: $(grep -Ehs "^[[:space:]]*$key[[:space:]]*=" /etc/sysctl.conf /etc/sysctl.d/*.conf | tail -n 1)"
    else
        echo "$setting" >> "$CONF"
        echo "Added to $CONF: $setting"
        changed=1
    fi
done

if [ "$changed" -eq 1 ]; then
    sysctl --system > /dev/null
    echo "Applied."
fi

echo "Connection tracking now: $(cat /proc/sys/net/netfilter/nf_conntrack_count) in use, max $(cat /proc/sys/net/netfilter/nf_conntrack_max)"
