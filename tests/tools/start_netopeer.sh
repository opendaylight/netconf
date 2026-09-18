#!/bin/bash

set -x

NETCONF_PROJECT_DIR="$(readlink -f "$(dirname "${BASH_SOURCE[0]}")/../..")"

"Preparing Shared Netopeer2 Environment (Call Home & Key Auth)..."

# 1. Clean and create directories
rm -rf /tmp/pytest-netopeer/configuration-files/ && mkdir -p /tmp/pytest-netopeer/configuration-files
rm -f /tmp/pytest-netopeer/incorrect_ssh_host_rsa_key*

# 2. Copy configuration files (Assuming you merged SSH and TLS into one set of XMLs)
cp "$NETCONF_PROJECT_DIR/tests/variables/netconf/callhome/docker-compose.yaml" /tmp/pytest-netopeer/
cp "$NETCONF_PROJECT_DIR/tests/variables/netconf/callhome/init_configuration.sh" /tmp/pytest-netopeer/
chmod +x /tmp/pytest-netopeer/init_configuration.sh
cp "$NETCONF_PROJECT_DIR/tests/variables/netconf/callhome/configuration-files/unified"/* /tmp/pytest-netopeer/configuration-files/

# 3. Inject correct IP and Ports
sed -i -e 's/ODL_SYSTEM_IP/127.0.0.1/g' /tmp/pytest-netopeer/docker-compose.yaml
sed -i -e 's/NETCONF_CH_SSH/4334/g' /tmp/pytest-netopeer/docker-compose.yaml
sed -i -e 's/NETCONF_CH_TLS/4335/g' /tmp/pytest-netopeer/docker-compose.yaml

# 4. Generate SSH Keys
ssh-keygen -q -t rsa -b 2048 -N '' -m pem -f /tmp/pytest-netopeer/incorrect_ssh_host_rsa_key
ssh-keygen -q -t rsa -b 2048 -N '' -m pem -f /tmp/pytest-netopeer/configuration-files/ssh_host_rsa_key

# 5. Generate TLS Certificates
rm -rf /tmp/pytest-netopeer/certs && mkdir /tmp/pytest-netopeer/certs
cp "$NETCONF_PROJECT_DIR/tests/variables/netconf/callhome/x509_v3.cfg" /tmp/pytest-netopeer/
# CHANGED: added -traditional to every `openssl genrsa` call below.
# Why: on newer OpenSSL (3.x, e.g. 3.5.5), `genrsa` defaults to PKCS#8 output
# (-----BEGIN PRIVATE KEY-----). ODL's netconf-keystore code (SecurityHelper,
# via BouncyCastle's PEMParser) only handles traditional PKCS#1 keys
# (-----BEGIN RSA PRIVATE KEY-----), so a PKCS#8 client key makes
# add-keystore-entry fail with "Unhandled private key class ...PrivateKeyInfo".
# -traditional forces the old PKCS#1 format, matching what older OpenSSL
# versions (e.g. on the VirtualBox box this originally worked on) produce by
# default, and what ODL's keystore code actually supports today.
openssl genrsa -traditional -out /tmp/pytest-netopeer/certs/ca.key 2048
openssl req -x509 -new -extensions v3_ca -nodes -key /tmp/pytest-netopeer/certs/ca.key -sha256 -days 365 -subj "/C=US/ST=CA/L=Netopeer/O=netopeerCA/CN=netopeerCA" -out /tmp/pytest-netopeer/certs/ca.pem
openssl genrsa -traditional -out /tmp/pytest-netopeer/certs/server.key 2048
openssl req -new -sha256 -key /tmp/pytest-netopeer/certs/server.key -subj "/C=US/ST=CA/L=Netopeer/O=Netopeer2/CN=netopeer2-server" -out /tmp/pytest-netopeer/certs/server.csr
openssl x509 -req -in /tmp/pytest-netopeer/certs/server.csr -CA /tmp/pytest-netopeer/certs/ca.pem -CAkey /tmp/pytest-netopeer/certs/ca.key -CAcreateserial -extfile /tmp/pytest-netopeer/x509_v3.cfg -out /tmp/pytest-netopeer/certs/server.crt -days 365 -sha256
openssl rsa -in /tmp/pytest-netopeer/certs/server.key -pubout > /tmp/pytest-netopeer/certs/server.pub
openssl genrsa -traditional -out /tmp/pytest-netopeer/certs/client.key 2048
openssl req -new -sha256 -key /tmp/pytest-netopeer/certs/client.key -subj "/C=US/ST=CA/L=Netopeer/O=Netopeer2/CN=netopeer2-client" -out /tmp/pytest-netopeer/certs/client.csr
openssl x509 -req -in /tmp/pytest-netopeer/certs/client.csr -CA /tmp/pytest-netopeer/certs/ca.pem -CAkey /tmp/pytest-netopeer/certs/ca.key -CAcreateserial -extfile /tmp/pytest-netopeer/x509_v3.cfg -out /tmp/pytest-netopeer/certs/client.crt -days 1024 -sha256
cp -R /tmp/pytest-netopeer/certs /tmp/pytest-netopeer/configuration-files/

# 6. Prepare Key Auth Files
mkdir -p /tmp/pytest-netopeer/keyauth
cp "$NETCONF_PROJECT_DIR/tests/variables/netconf/KeyAuth/sb-rsa-key.pub" /tmp/pytest-netopeer/keyauth/

# 7. Start the unified container
echo "Starting Unified Netopeer2 container..."
docker compose --project-directory . -f /tmp/pytest-netopeer/docker-compose.yaml up -d
