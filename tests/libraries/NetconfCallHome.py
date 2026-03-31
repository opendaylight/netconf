#
# Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License v1.0 which accompanies this distribution,
# and is available at http://www.eclipse.org/legal/epl-v10.html
#

import logging


from libraries import infra
from libraries import templated_requests
from libraries.variables import variables

HEADERS = variables.HEADERS
MOUNT_POINT_URL = "/rests/data/network-topology:network-topology/topology=topology-netconf?content=nonconfig"
DEVICE_STATUS = "/rests/data/odl-netconf-callhome-server:netconf-callhome-server?content=nonconfig"
WHITELIST = "/rests/data/odl-netconf-callhome-server:netconf-callhome-server/allowed-devices"
GLOBAL_CONFIG_URL = "/rests/data/odl-netconf-callhome-server:netconf-callhome-server/global/credentials"
NETCONF_KEYSTORE_URL = "/rests/operations/netconf-keystore"
NETCONF_KEYSTORE_DATA_URL = "/rests/data/netconf-keystore:keystore"

NETCONF_MOUNT_EXPECTED_VALUE = ('"connection-status":"connected"', '"node-id":"netopeer2"', '"available-capabilities"')
CREATE_GLOBAL_CREDENTIALS_REQ = "variables/netconf/callhome/json/create_global_credentials.json"
CREATE_SSH_DEVICE_REQ = "variables/netconf/callhome/json/create_ssh_device.json"
CREATE_SSH_DEVICE_REQ_HOST_KEY_ONLY = "variables/netconf/callhome/json/create_device_hostkey_only.json"
CREATE_TLS_DEVICE_REQ = "variables/netconf/callhome/json/create_tls_device.json"
ADD_KEYSTORE_ENTRY_REQ = "variables/netconf/callhome/json/add_keystore_entry.json"
ADD_PRIVATE_KEY_REQ = "variables/netconf/callhome/json/add_private_key.json"
ADD_TRUSTED_CERTIFICATE = "variables/netconf/callhome/json/add_trusted_certificate.json"


log = logging.getLogger(__name__)


def check_device_status(status: str, id: str = "netopeer2"):
    """Checks the operational device status.
    
    Args:
        status (str): Expected device status.
        id (str): Device id.

    Returns:
        str: Final truncated text.
    """
    expected_values = [f'"unique-id":"{id}"', f'"device-status":"{status}"']
    if status in ("FAILED_NOT_ALLOWED", "FAILED_AUTH_FAILURE"):
        expected_values.remove(f'"unique-id":"${id}"')
    utils.check_for_element_at_uri(DEVICE_STATUS, expected_values)

def apply_ssh_based_call_home_configuration():
    """Upload netopeer2 configuration files needed for SSH transport"""
    infra.copy_file(
        src_dir="variables/netconf/callhome/configuration-files/ssh/",
        src_file_name="ietf-netconf-server.xml",
        dst_dir="tmp/configuration-files/"
    )
    infra.copy_file(
        src_dir="variables/netconf/callhome/configuration-files/ssh/",
        src_file_name="ietf-keystore.xml",
        dst_dir="tmp/configuration-files/"
    )

def apply_tls_based_call_home_configuration():
    generate_certificates_for_tls_configuration()
    """Upload netopeer2 configuration files needed for TLS transport"""
    infra.copy_file(
        src_dir="variables/netconf/callhome/configuration-files/tls",
        src_file_name="ietf-keystore.xml",
        dst_dir="tmp/configuration-files/"
    )
    infra.copy_file(
        src_dir="variables/netconf/callhome/configuration-files/tls",
        src_file_name="ietf-truststore.xml",
        dst_dir="tmp/configuration-files/"
    )
    infra.copy_file(
        src_dir="variables/netconf/callhome/configuration-files/tls",
        src_file_name="ietf-netconf-server.xml",
        dst_dir="tmp/configuration-files/"
    )

def generate_certificates_for_tls_configuration():
    """Generates certificates for 2-way TLS authentication (ca, server, client)"""
    infra.shell("rm -rf ./certs && mkdir ./certs")
    infra.copy_file(
        src_dir="variables/netconf/callhome/",
        src_file_name="x509_v3.cfg",
        dst_dir="tmp/"
    )
    infra.shell("openssl genrsa -out tmp/certs/ca.key 2048")
    infra.shell('openssl req -x509 -new -extensions v3_ca -nodes -key tmp/certs/ca.key -sha256 -days 365 -subj "/C=US/ST=CA/L=Netopeer/O=netopeerCA/CN=netopeerCA" -out tmp/certs/ca.pem')
    infra.shell("openssl genrsa -out tmp/certs/server.key 2048")
    infra.shell('openssl req -new -sha256 -key tmp/certs/server.key -subj "/C=US/ST=CA/L=Netopeer/O=Netopeer2/CN=netopeer2-server" -out tmp/certs/server.csr')
    infra.shell("openssl x509 -req -in tmp/certs/server.csr -CA tmp/certs/ca.pem -CAkey tmp/certs/ca.key -CAcreateserial -extfile x509_v3.cfg -out tmp/certs/server.crt -days 365 -sha256")
    infra.shell("openssl rsa -in tmp/certs/server.key -pubout > tmp/certs/server.pub")
    infra.shell("openssl genrsa -out tmp/certs/client.key 2048")
    infra.shell('openssl req -new -sha256 -key tmp/certs/client.key -subj "/C=US/ST=CA/L=Netopeer/O=Netopeer2/CN=netopeer2-client" -out tmp/certs/client.csr')
    infra.shell("openssl x509 -req -in tmp/certs/client.csr -CA tmp/certs/ca.pem -CAkey tmp/certs/ca.key -CAcreateserial -extfile x509_v3.cfg -out tmp/certs/client.crt -days 1024 -sha256")
    infra.copy_file(
        src_dir="tmp/",
        src_file_name="certs",
        dst_dir="tmp/configuration-files/"
    )

def get_certificate_file_content(file_name: str):
    """Get certificate or key file content

    This removes from the pem file headers and also new line characters.
    
    Args:
        file_name (str): Name of the pem formated certificate or key file.

    Returns:
        str: Certificate or key file content.
    """
    rc, content = infra.shell(f"sed -u '1d; $d' tmp/configuration-files/certs/${file_name} | sed -z 's!\\n!\\\\n!g'")

    return content

def register_keys_and_certificates_in_odl_cotroller():
    """Register pre-configured netopeer2 certificates and key in ODL-netconf keystore"""
    #rc, pem_client_key = infra.shell("cat ./configuration-files/certs/client.key")
    pem_client_key = infra.get_file_content("tmp/configuration-files/certs/client.key")
    template = infra.get_file_content(ADD_KEYSTORE_ENTRY_REQ)
    body = template.replace("{pem-client-key}", pem_client_key)
    resp = templated_requests.post_to_uri(
        uri=f"{NETCONF_KEYSTORE_URL}:add-keystore-entry",
        data=body,
        headers=HEADERS,
        expected_code=templated_requests.ALLOWED_STATUS_CODES,
    )
    client_key = get_certificate_file_content("client.key")
    certificat_chain = get_certificate_file_content("client.crt")
    template = infra.get_file_content(ADD_PRIVATE_KEY_REQ)
    body = template.replace("{client-key}", client_key)
    body = body.replace("{certificate-chain}", certificat_chain)
    resp = templated_requests.post_to_uri(
        uri=f"{NETCONF_KEYSTORE_URL}:add-private-key",
        data=body,
        headers=HEADERS,
        expected_code=templated_requests.ALLOWED_STATUS_CODES,
    )
    ca_certificate = get_certificate_file_content("ca.pem")
    device_certificate = get_certificate_file_content("server.crt")
    template = infra.get_file_content(ADD_TRUSTED_CERTIFICATE)
    body = template.replace("{ca-certificate}", ca_certificate)
    body = body.replace("{device-certificate}", device_certificate)
    resp = templated_requests.post_to_uri(
        uri=f"{NETCONF_KEYSTORE_URL}:add-trusted-certificate",
        data = body,
        headers=HEADERS,
        expected_code=templated_requests.ALLOWED_STATUS_CODES,
    )

def register_global_credentials_for_ssh_call_home_devices(username: str, password: str):
    """Set global credentials for SSH call-home devices
    
    Args:
        username (str): Username used for global login.
        password (str): Password used for global login.

    Returns:
        None
    """
    template = infra.get_file_content(CREATE_GLOBAL_CREDENTIALS_REQ)
    body = template.replace("{username}", username)
    body = body.replace("{password}", password)
    resp = templated_requests.post_to_uri(
        uri=GLOBAL_CONFIG_URL,
        data=body,
        headers=HEADERS,
        expected_code=templated_requests.ALLOWED_STATUS_CODES,
    )

def register_ssh_call_home_devices_in_odl_controller(device_name: str, hostkey: str, username: str | None = None, password: str | None = None):
    """Registration call-home device with SSH transport using latest models
    
    Args:
        device_name(str): Call-home device name.
        hostkey (str): Hostkey used for authentication.
        username (str | None): Username used for authentication.
        password (str | None): Password used for authentication.

    Returns:
        None
    """
    if not username and not password:
        template = get_create_device_request_without_credentials_template()
    else:
        template = get_create_device_request_template()
    body = template.replace("{device_name}", device_name)
    body = body.replace("{username}", username)
    body = body.replace("{password}", password)
    body = body.replace("{hostkey}", hostkey)
    resp = templated_requests.post_to_uri(
        uri=WHITELIST,
        data=body,
        headers=HEADERS,
        expected_code=templated_requests.ALLOWED_STATUS_CODES,
    )

def get_create_device_request_template():
    template = infra.get_file_content(CREATE_SSH_DEVICE_REQ)

    return template

def get_create_device_request_without_credentials_template():
    template = infra.get_file_content(CREATE_SSH_DEVICE_REQ_HOST_KEY_ONLY)

    return template

def register_tls_call_home_device_in_odl_controller(device_name: str, key_id: str, certificate_id: str):
    """Registration call-home device with TLS transport
    
    Args:
        device_name(str): Call-home device name.
        key_id (str): ID of the key used for authentication.
        certificate_id (str): ID of the certificate used for authentication.

    Returns:
        None
    """
    template = infra.get_file_content(CREATE_TLS_DEVICE_REQ)
    body = template.replace("{device_name}", device_name)
    body = body.replace("{key_id}", key_id)
    body = body.replace("{certificate_id}", certificate_id)
    resp = templated_requests.post_to_uri(
        uri=WHITELIST,
        data=body,
        headers=HEADERS,
        expected_code=templated_requests.ALLOWED_STATUS_CODES,
    )

def test_setup():
    """Set configuration folder, generates a new host key for the container"""
    infra.shell("rm -rf tmp/configuration-files && mkdir tmp/configuration-files")
    infra.shell("ssh-keygen -q -t rsa -b 2048 -N '' -m pem -f tmp/configuration-files/ssh_host_rsa_key")
    public_key = infra.shell("cat tmp/configuration-files/ssh_host_rsa_key.pub | awk '{print $2}'")

    yield public_key

    infra.shell("rm -rf ./configuration-files")
    templated_requests.delete_from_uri_request(WHITELIST)
    templated_requests.delete_from_uri_request(NETCONF_KEYSTORE_DATA_URL)

def suite_setup():
    """Get the suite ready for callhome test cases."""
    infra.copy_file(
        src_dir="variables/netconf/callhome/",
        src_file_name="docker-compose.yaml",
        dst_dir="tmp/"
    )
    infra.copy_file(
        src_dir="variables/netconf/callhome/",
        src_file_name="init_configuration.sh",
        dst_dir="tmp/"
    )
    netconf_cl_ssh_port = 4334
    infra.shell("sed -i -e 's/NETCONF_CH_SSH/${netconf_cl_ssh_port}/g' tmp/docker-compose.yaml")
    infra.shell("sed -i -e 's/NETCONF_CH_TLS/4335/g' tmp/docker-compose.yaml")
    infra.shell("ssh-keygen -q -t rsa -b 2048 -N '' -m pem -f tmp/incorrect_ssh_host_rsa_key")
    rc, INCORRECT_PUBLIC_KEY = infra.shell("awk '{print $2}' tmp/incorrect_ssh_host_rsa_key.pub")





