#!/bin/bash
#
# This scripts is called within a docker-compose to initialize configuration for netopeer2-server
#

set -e

# Configuration files path. Used to store certificates, keys, and configuration data that might be used by netopeer2.
CONFIG_PATH='/root/configuration-files'

# Configuration from the following modules will be imported to the sysrepo datastore.
# 1. Each module's configuration file should be placed under the $CONFIG_PATH folder with .xml extension, e.g. ietf-truststore.xml.
# 2. Script will replace all variable placeholders with corresponding environment variables, e.g. $CALL_HOME_SERVER_IP.
# Please, note that following environment variables will be set according to the provided keys and certificates(under the $CONFIG_PATH/certs folder):
#  - $NP_PRIVKEY
#  - $NP_PUBKEY
#  - $NP_CA_CERT
#  - $NP_CLIENT_CERT
#  - $NP_SERVER_PRIVATE_KEY
#  - $NP_SERVER_PUBLIC_KEY
#  - $NP_SERVER_CERTIFICATE
#  - $NP_CLIENT_CERT_FINGERPRINT
# 3. Modules must be provided in the correct order.
MODULES_LIST=("ietf-truststore" "ietf-keystore" "ietf-netconf-server")

import_module()
{
  local MODULE_NAME=$1

  # do not import anything if the corresponding configuration file doesn't exist
  if [ ! -f $CONFIG_PATH/$MODULE_NAME.xml ]; then
    return 0
  fi

  # Replace placeholders in templates with ENV variables
  python3 -c "import os, sys; print(os.path.expandvars(sys.stdin.read()))" < $CONFIG_PATH/$MODULE_NAME.xml > $MODULE_NAME.tmp
  cat $MODULE_NAME.tmp > $CONFIG_PATH/$MODULE_NAME.xml
  rm $MODULE_NAME.tmp

  # Import configuration into both datastores
  sysrepocfg --import=$CONFIG_PATH/$MODULE_NAME.xml -m $MODULE_NAME --datastore=startup
  sysrepocfg --import=$CONFIG_PATH/$MODULE_NAME.xml -m $MODULE_NAME --datastore=running

  echo "Configuration file $CONFIG_PATH/$MODULE_NAME.xml has been imported"
}

### Main script starts here ###

# Remove existing host keys and import new one
rm -f /etc/ssh/ssh_host_*
cp $CONFIG_PATH/ssh_host_rsa_key /etc/ssh/ssh_host_rsa_key
cp $CONFIG_PATH/ssh_host_rsa_key.pub /etc/ssh/ssh_host_rsa_key.pub

# These variables will replace corresponding placeholders inside configuration templates
SAVEIFS=$IFS
IFS=
export NP_PRIVKEY=`cat /etc/ssh/ssh_host_rsa_key | sed -u '1d; $d' | tr -d '\n'`
export NP_PUBKEY=`openssl rsa -in /etc/ssh/ssh_host_rsa_key -pubout | sed -u '1d; $d' | tr -d '\n'`

if [ -d "$CONFIG_PATH/certs" ]; then
    export NP_CA_CERT=`sed -u '1d; $d' $CONFIG_PATH/certs/ca.pem | tr -d '\n'`
    export NP_CLIENT_CERT=`sed -u '1d; $d' $CONFIG_PATH/certs/client.crt | tr -d '\n'`
    export NP_SERVER_PRIVATE_KEY=`sed -u '1d; $d' $CONFIG_PATH/certs/server.key | tr -d '\n'`
    export NP_SERVER_PUBLIC_KEY=`sed -u '1d; $d' $CONFIG_PATH/certs/server.pub | tr -d '\n'`
    export NP_SERVER_CERTIFICATE=`sed -u '1d; $d' $CONFIG_PATH/certs/server.crt | tr -d '\n'`
    export NP_CLIENT_CERT_FINGERPRINT=`openssl x509 -noout -fingerprint -in $CONFIG_PATH/certs/ca.pem -sha1 | cut -d'=' -f2- | tr -d '\n'`
fi
IFS=$SAVEIFS

# Import all provided configuration files for netopeer
for module_name in "${MODULES_LIST[@]}"; do
    import_module "$module_name"
done

unset NP_PRIVKEY
unset NP_PUBKEY

echo "Netopeer2-server initial configuration completed"
exit 0
