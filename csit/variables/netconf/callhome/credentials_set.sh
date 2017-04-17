#!/bin/bash

# This script is called within the docker-compose to provision the controller with either
# the global or per-device credentials.

stderr() { echo "$@" 1>&2; }

[ $# -eq 0 ] && { stderr "Usage: $0 ( -global | -per-device id ) username password(s)"; exit 1; }

: ${1?"Usage: $0 -global | -per-device"}

option=${1};

if [[ "${option}" == "-per-device" ]]; then
  : ${2?"Usage: $0 Device Id"}
  : ${3?"Usage: $0 Device Username"}
  : ${4?"Usage: $0 Device Password"}
  devid=${2}
  user=${3}
  shift; shift; shift
  pwds="$@"
elif [[ "${option}" == "-global" ]]; then
  : ${2?"Usage: $0 Global Username"}
  : ${3?"Usage: $0 Global Password"}
  devid=""
  user=${2}
  shift; shift
  pwds="$@"
else
  stderr "$0: must supply -global or -per-device command line argument for global password changes, not '${option}''"
  exit 1
fi

pwdsjson=""

for pwd in $pwds; do
  if [[ ! -z "$pwdsjson" ]]; then
    pwdsjson+=","
  fi
  pwdsjson+="'$pwd'"
done

set -e
controller=ODL_SYSTEM_IP
port=8181
basicauth="YWRtaW46YWRtaW4="

baseurl="http://${controller}:${port}/restconf/config/odl-netconf-callhome-server:netconf-callhome-server"

if [[ "${option}" == "-global" ]]; then
  url="${baseurl}/global/credentials"
else
  url="${baseurl}/allowed-devices/device/${devid}/credentials"
fi

set +e
read -r -d '' payload << EOM
{
    "credentials": {
    	"username": "${user}",
  	  "passwords": [${pwdsjson}]
    }
}
EOM
set -e

payload=$(echo "${payload}" | tr '\n' ' ' | tr -s " ")

echo "PUT of user (${user}) and pwd (${pwd})"
res=$(curl -s -X PUT \
      -H "Authorization: Basic ${basicauth}" \
      -H "Content-Type: application/json" \
      -H "Cache-Control: no-cache" \
      --data "${payload}" \
      ${url})

if [[ $res == *"error-message"* ]]; then
  stderr "$0: ${res}"
  exit 1
fi

echo "Getting user/pwd ..."

res=$(curl -s -X GET \
      -H "Authorization: Basic ${basicauth}" \
      -H "Cache-Control: no-cache" \
      ${url})
echo ${res}
