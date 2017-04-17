#!/bin/bash

# This script is called within the docker-compose to get the public RSA key of the
# device(callhome client) and provision the controller.

set -e

key3="$(cat /etc/ssh/ssh_host_rsa_key.pub)"
parts=($key3)
hostkey=${parts[1]}
id=$1
controller=ODL_SYSTEM_IP
echo "Adding key for ${id} to ${controller}"
echo "Found host key: ${hostkey}"

port=8181
basicauth="YWRtaW46YWRtaW4="

set +e
read -r -d '' payload << EOM
{
    "device": [
        {
            "ssh-host-key": "${hostkey}",
            "unique-id": "${id}"
        }
     ]
}
EOM
set -e

payload=$(echo "${payload}" | tr '\n' ' ' | tr -s " ")

url="http://${controller}:${port}/restconf/config/odl-netconf-callhome-server:netconf-callhome-server/allowed-devices"

echo "POST to whitelist"
res=$(curl -s -X POST -H "Authorization: Basic ${basicauth}" \
      -H "Content-Type: application/json" \
      -H "Cache-Control: no-cache" \
      -H "Postman-Token: 656d7e0d-2f48-5135-3569-06b2a27a709d" \
      --data "${payload}" \
      ${url})
echo $res
if [[ $res == *"data-exists"* ]]; then
  echo "Whitelist already has that entry."
fi
