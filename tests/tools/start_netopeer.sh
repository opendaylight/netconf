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
# from anything else in /tmp and removed in one go. The suites read the same
# path from variables.NETOPEER_DIR; keep the two in step.
WORK_DIR="/tmp/pytest-netopeer"

# Where the container dials back to. These are ODL's default call home
# listener ports, which the suites do not reconfigure.
ODL_SYSTEM_IP="127.0.0.1"
CALL_HOME_SSH_PORT="4334"
CALL_HOME_TLS_PORT="4335"

for tool in docker ssh-keygen openssl; do
    if ! command -v "$tool" >/dev/null 2>&1; then
        echo "$tool is required but was not found on PATH" >&2
        exit 1
    fi
done

echo "Preparing the Netopeer2 environment for the callhome and KeyAuth suites"

# 1. Clean and create directories
rm -rf "$WORK_DIR"
mkdir -p "$WORK_DIR/configuration-files" "$WORK_DIR/certs" "$WORK_DIR/keyauth"

# 2. Copy the configuration the container boots from
cp "$CALLHOME_DIR/docker-compose.yaml" "$WORK_DIR/"
cp "$CALLHOME_DIR/init_configuration.sh" "$WORK_DIR/"
chmod +x "$WORK_DIR/init_configuration.sh"
cp "$CALLHOME_DIR/configuration-files/unified/"* "$WORK_DIR/configuration-files/"
cp "$CALLHOME_DIR/x509_v3.cfg" "$WORK_DIR/"

# 3. Inject the address and ports the container calls home on
sed -i -e "s/ODL_SYSTEM_IP/$ODL_SYSTEM_IP/g" \
    -e "s/NETCONF_CH_SSH/$CALL_HOME_SSH_PORT/g" \
    -e "s/NETCONF_CH_TLS/$CALL_HOME_TLS_PORT/g" \
    "$WORK_DIR/docker-compose.yaml"

# 4. Generate SSH keys. The incorrect one is what the callhome suite offers
# when it checks that an unknown host key is rejected.
ssh-keygen -q -t rsa -b 2048 -N '' -m pem -f "$WORK_DIR/incorrect_ssh_host_rsa_key"
ssh-keygen -q -t rsa -b 2048 -N '' -m pem -f "$WORK_DIR/configuration-files/ssh_host_rsa_key"

# 5. Generate TLS certificates. -traditional keeps `genrsa` emitting PKCS#1;
# OpenSSL 3.x defaults to PKCS#8, which ODL's keystore cannot parse and which
# makes add-keystore-entry fail on the client key.
openssl genrsa -traditional -out "$WORK_DIR/certs/ca.key" 2048
openssl req -x509 -new -extensions v3_ca -nodes -key "$WORK_DIR/certs/ca.key" \
    -sha256 -days 365 -out "$WORK_DIR/certs/ca.pem" \
    -subj "/C=US/ST=CA/L=Netopeer/O=netopeerCA/CN=netopeerCA"

openssl genrsa -traditional -out "$WORK_DIR/certs/server.key" 2048
openssl req -new -sha256 -key "$WORK_DIR/certs/server.key" \
    -out "$WORK_DIR/certs/server.csr" \
    -subj "/C=US/ST=CA/L=Netopeer/O=Netopeer2/CN=netopeer2-server"
openssl x509 -req -in "$WORK_DIR/certs/server.csr" -CA "$WORK_DIR/certs/ca.pem" \
    -CAkey "$WORK_DIR/certs/ca.key" -CAcreateserial -extfile "$WORK_DIR/x509_v3.cfg" \
    -out "$WORK_DIR/certs/server.crt" -days 365 -sha256
openssl rsa -in "$WORK_DIR/certs/server.key" -pubout >"$WORK_DIR/certs/server.pub"

openssl genrsa -traditional -out "$WORK_DIR/certs/client.key" 2048
openssl req -new -sha256 -key "$WORK_DIR/certs/client.key" \
    -out "$WORK_DIR/certs/client.csr" \
    -subj "/C=US/ST=CA/L=Netopeer/O=Netopeer2/CN=netopeer2-client"
openssl x509 -req -in "$WORK_DIR/certs/client.csr" -CA "$WORK_DIR/certs/ca.pem" \
    -CAkey "$WORK_DIR/certs/ca.key" -CAcreateserial -extfile "$WORK_DIR/x509_v3.cfg" \
    -out "$WORK_DIR/certs/client.crt" -days 1024 -sha256

cp -R "$WORK_DIR/certs" "$WORK_DIR/configuration-files/"

# 6. Stage the public key the KeyAuth suite authenticates with
cp "$KEYAUTH_DIR/sb-rsa-key.pub" "$WORK_DIR/keyauth/"

# 7. Label the bind mounts for SELinux. Without this the container is denied
# every mounted path and init_configuration.sh fails on start-up. Only applies
# where SELinux is enforcing, so a missing chcon is not an error.
if command -v getenforce >/dev/null 2>&1 && [ "$(getenforce)" != "Disabled" ]; then
    chcon -Rt svirt_sandbox_file_t \
        "$WORK_DIR/init_configuration.sh" \
        "$WORK_DIR/configuration-files" \
        "$WORK_DIR/keyauth" \
        "$WORK_DIR/certs"
fi

# 8. Start the container, replacing one left over from an earlier run. Its
# bind mounts point into the directory just recreated above, and on a host
# where /tmp is a tmpfs they were emptied by the last reboot.
echo "Starting the Netopeer2 container"
docker compose --project-directory "$WORK_DIR" -f "$WORK_DIR/docker-compose.yaml" \
    down --remove-orphans
docker compose --project-directory "$WORK_DIR" -f "$WORK_DIR/docker-compose.yaml" up -d

echo "Netopeer2 is up. Leave it running for the callhome and KeyAuth suites."
