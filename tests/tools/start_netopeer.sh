#!/bin/bash
#
# Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License v1.0 which accompanies this distribution,
# and is available at http://www.eclipse.org/legal/epl-v10.html

# Provisions the Netopeer2 environment the callhome and KeyAuth suites run
# against and starts the container. Nothing in a test run does this, so run it
# once beforehand and leave the container up; it can serve any number of runs.

set -euo pipefail

NETCONF_PROJECT_DIR="$(readlink -f "$(dirname "${BASH_SOURCE[0]}")/../..")"
CALLHOME_DIR="$NETCONF_PROJECT_DIR/tests/variables/netconf/callhome"
KEYAUTH_DIR="$NETCONF_PROJECT_DIR/tests/variables/netconf/KeyAuth"

# Everything generated here lands under one directory so it can be told apart
# from anything else in /tmp and removed in one go. Exported so the compose
# file resolves its bind mounts to the same place, and read from the same
# variable as the suites' variables.NETOPEER_DIR, so one override moves both.
export NETOPEER_DIR="${NETOPEER_DIR:-/tmp/pytest-netopeer}"

# Where the container dials back to. The defaults are ODL's call home listener
# ports, which the suites do not reconfigure, and are overridable the same way
# NETOPEER_DIR is for a host that cannot use them.
ODL_SYSTEM_IP="${ODL_SYSTEM_IP:-127.0.0.1}"
CALL_HOME_SSH_PORT="${CALL_HOME_SSH_PORT:-4334}"
CALL_HOME_TLS_PORT="${CALL_HOME_TLS_PORT:-4335}"

# Fixed in the compose file too, so a container left by an earlier run can be
# removed by name. Compose can only find it while the directory holding the
# compose file survives, and that is the directory this script deletes.
CONTAINER_NAME="pytest-netopeer2-server"

for tool in docker ssh-keygen openssl; do
    if ! command -v "$tool" >/dev/null 2>&1; then
        echo "$tool is required but was not found on PATH" >&2
        exit 1
    fi
done

# Checked apart from the loop above, as v1 docker-compose is a command of its
# own: a host carrying only that passes the docker check and fails at the
# first compose call instead.
if ! docker compose version >/dev/null 2>&1; then
    echo "the docker compose v2 plugin is required but was not found" >&2
    exit 1
fi

COMPOSE=(docker compose --project-directory "$NETOPEER_DIR"
    -f "$NETOPEER_DIR/docker-compose.yaml")

echo "Preparing the Netopeer2 environment for the callhome and KeyAuth suites"

# 1. Clean and create directories. The container from an earlier run has its
# bind mounts in here, so it has to go first rather than at the end. Removing
# it by name rather than through compose also covers a run whose directory was
# already deleted, which would otherwise leave the old container holding the
# host ports.
docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true

# NETOPEER_DIR is overridable, so the removal below is only allowed to touch a
# path that is absolute and at least two levels deep, and that either does not
# exist, is empty, or holds the compose file an earlier run of this script put
# there. A plain "must be under /tmp" rule was tempting, but /tmp is a tmpfs on
# many hosts and pointing this elsewhere to survive a reboot is a legitimate
# reason to override it.
case "$NETOPEER_DIR" in
    /*/*) ;;
    *)
        echo "NETOPEER_DIR must be an absolute path at least two levels deep," \
            "got '$NETOPEER_DIR'" >&2
        exit 1
        ;;
esac
if [ -e "$NETOPEER_DIR" ] &&
    [ ! -f "$NETOPEER_DIR/docker-compose.yaml" ] &&
    [ -n "$(ls -A "$NETOPEER_DIR" 2>/dev/null)" ]; then
    echo "$NETOPEER_DIR is not empty and was not created by this script;" \
        "refusing to remove it" >&2
    exit 1
fi
rm -rf "$NETOPEER_DIR"
mkdir -p "$NETOPEER_DIR/configuration-files" "$NETOPEER_DIR/certs" "$NETOPEER_DIR/keyauth"

# 2. Copy the configuration the container boots from
cp "$CALLHOME_DIR/docker-compose.yaml" "$NETOPEER_DIR/"
cp "$CALLHOME_DIR/init_configuration.sh" "$NETOPEER_DIR/"
chmod +x "$NETOPEER_DIR/init_configuration.sh"
cp "$CALLHOME_DIR/configuration-files/unified/"* "$NETOPEER_DIR/configuration-files/"
cp "$CALLHOME_DIR/x509_v3.cfg" "$NETOPEER_DIR/"

# 3. Inject the address and ports the container calls home on
sed -i -e "s/ODL_SYSTEM_IP/$ODL_SYSTEM_IP/g" \
    -e "s/NETCONF_CH_SSH/$CALL_HOME_SSH_PORT/g" \
    -e "s/NETCONF_CH_TLS/$CALL_HOME_TLS_PORT/g" \
    "$NETOPEER_DIR/docker-compose.yaml"

# 4. Generate SSH keys. The incorrect one is what the callhome suite offers
# when it checks that an unknown host key is rejected.
ssh-keygen -q -t rsa -b 2048 -N '' -m pem -f "$NETOPEER_DIR/incorrect_ssh_host_rsa_key"
ssh-keygen -q -t rsa -b 2048 -N '' -m pem -f "$NETOPEER_DIR/configuration-files/ssh_host_rsa_key"

# 5. Generate TLS certificates. genrsa has to write PKCS#1, since ODL's
# keystore cannot parse the PKCS#8 that OpenSSL 3.x defaults to and
# add-keystore-entry then fails on the client key. -traditional asks for it
# but was added in 3.0; 1.x writes PKCS#1 already and rejects the flag.
GENRSA_FORMAT=()
if openssl genrsa -help 2>&1 | grep -q -- -traditional; then
    GENRSA_FORMAT=(-traditional)
fi

openssl genrsa "${GENRSA_FORMAT[@]}" -out "$NETOPEER_DIR/certs/ca.key" 2048
openssl req -x509 -new -extensions v3_ca -nodes -key "$NETOPEER_DIR/certs/ca.key" \
    -sha256 -days 365 -out "$NETOPEER_DIR/certs/ca.pem" \
    -subj "/C=US/ST=CA/L=Netopeer/O=netopeerCA/CN=netopeerCA"

openssl genrsa "${GENRSA_FORMAT[@]}" -out "$NETOPEER_DIR/certs/server.key" 2048
openssl req -new -sha256 -key "$NETOPEER_DIR/certs/server.key" \
    -out "$NETOPEER_DIR/certs/server.csr" \
    -subj "/C=US/ST=CA/L=Netopeer/O=Netopeer2/CN=netopeer2-server"
openssl x509 -req -in "$NETOPEER_DIR/certs/server.csr" -CA "$NETOPEER_DIR/certs/ca.pem" \
    -CAkey "$NETOPEER_DIR/certs/ca.key" -CAcreateserial -extfile "$NETOPEER_DIR/x509_v3.cfg" \
    -out "$NETOPEER_DIR/certs/server.crt" -days 365 -sha256
openssl rsa -in "$NETOPEER_DIR/certs/server.key" -pubout >"$NETOPEER_DIR/certs/server.pub"

openssl genrsa "${GENRSA_FORMAT[@]}" -out "$NETOPEER_DIR/certs/client.key" 2048
openssl req -new -sha256 -key "$NETOPEER_DIR/certs/client.key" \
    -out "$NETOPEER_DIR/certs/client.csr" \
    -subj "/C=US/ST=CA/L=Netopeer/O=Netopeer2/CN=netopeer2-client"
openssl x509 -req -in "$NETOPEER_DIR/certs/client.csr" -CA "$NETOPEER_DIR/certs/ca.pem" \
    -CAkey "$NETOPEER_DIR/certs/ca.key" -CAcreateserial -extfile "$NETOPEER_DIR/x509_v3.cfg" \
    -out "$NETOPEER_DIR/certs/client.crt" -days 1024 -sha256

cp -R "$NETOPEER_DIR/certs" "$NETOPEER_DIR/configuration-files/"

# 6. Stage the public key the KeyAuth suite authenticates with
cp "$KEYAUTH_DIR/sb-rsa-key.pub" "$NETOPEER_DIR/keyauth/"

# 7. Start the container and wait for it to answer. up -d returns once the
# container has been created, so a failure in init_configuration.sh or in
# netopeer2-server itself would otherwise be reported as a successful start.
echo "Starting the Netopeer2 container"
"${COMPOSE[@]}" up -d

for _ in $(seq 1 30); do
    if (exec 3<>/dev/tcp/127.0.0.1/830) 2>/dev/null; then
        echo "Netopeer2 is up. Leave it running for the callhome and KeyAuth suites."
        exit 0
    fi
    sleep 1
done

echo "Netopeer2 is not listening on port 830 after 30 seconds" >&2
"${COMPOSE[@]}" ps >&2
"${COMPOSE[@]}" logs >&2
exit 1
