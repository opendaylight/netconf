#
# Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License v1.0 which accompanies this distribution,
# and is available at http://www.eclipse.org/legal/epl-v10.html
#

import logging


from libraries import infra
from libraries import restconf_utils
from libraries import templated_requests
from libraries.variables import variables

HEADERS = variables.HEADERS
DEVICE_STATUS = (
    "/rests/data/odl-netconf-callhome-server:netconf-callhome-server/"
    "allowed-devices?content=nonconfig"
)
WHITELIST = variables.CALLHOME_WHITELIST
GLOBAL_CONFIG_URL = (
    "/rests/data/odl-netconf-callhome-server:netconf-callhome-server/global/credentials"
)
NETCONF_KEYSTORE_URL = "/rests/operations/netconf-keystore"
NETCONF_KEYSTORE_DATA_URL = variables.NETCONF_KEYSTORE_DATA_URL

CREATE_GLOBAL_CREDENTIALS_REQ = (
    "variables/netconf/callhome/json/create_global_credentials.json"
)
CREATE_SSH_DEVICE_REQ = "variables/netconf/callhome/json/create_ssh_device.json"
CREATE_SSH_DEVICE_REQ_HOST_KEY_ONLY = (
    "variables/netconf/callhome/json/create_device_hostkey_only.json"
)
CREATE_TLS_DEVICE_REQ = "variables/netconf/callhome/json/create_tls_device.json"
ADD_KEYSTORE_ENTRY_REQ = "variables/netconf/callhome/json/add_keystore_entry.json"
ADD_PRIVATE_KEY_REQ = "variables/netconf/callhome/json/add_private_key.json"
ADD_TRUSTED_CERTIFICATE = "variables/netconf/callhome/json/add_trusted_certificate.json"


log = logging.getLogger(__name__)


def get_certificate_file_content(file_name: str):
    """Get certificate or key file content with escaped newline characters.

    This reads a PEM-formatted file and replaces actual line breaks with
    literal '\\n' strings.

    Args:
        file_name (str): Name of the pem formated certificate or key file.

    Returns:
        str: Certificate or key file content.
    """
    rc, content = infra.shell(
        f"sed -z 's!\\n!\\\\n!g' /tmp/configuration-files/certs/{file_name}"
    )

    return content


def register_keys_and_certificates_in_odl_cotroller():
    """Register pre-configured netopeer2 certificates and key
    in ODL-netconf keystore.
    """

    # Registere client key
    pem_client_key = infra.get_file_content("/tmp/configuration-files/certs/client.key")
    template = infra.get_file_content(ADD_KEYSTORE_ENTRY_REQ)
    body = template.replace("{pem-client-key}", pem_client_key)
    templated_requests.post_to_uri(
        uri=f"{NETCONF_KEYSTORE_URL}:add-keystore-entry",
        data=body,
        headers=HEADERS,
        expected_code=templated_requests.ALLOWED_STATUS_CODES,
    )

    # Pair client key with certificate chain
    client_key = get_certificate_file_content("client.key")
    certificat_chain = get_certificate_file_content("client.crt")
    template = infra.get_file_content(ADD_PRIVATE_KEY_REQ)
    body = template.replace("{client-key}", client_key)
    body = body.replace("{certificate-chain}", certificat_chain)
    templated_requests.post_to_uri(
        uri=f"{NETCONF_KEYSTORE_URL}:add-private-key",
        data=body,
        headers=HEADERS,
        expected_code=templated_requests.ALLOWED_STATUS_CODES,
    )

    # Register device certificate
    ca_certificate = get_certificate_file_content("ca.pem")
    device_certificate = get_certificate_file_content("server.crt")
    template = infra.get_file_content(ADD_TRUSTED_CERTIFICATE)
    body = template.replace("{ca-certificate}", ca_certificate)
    body = body.replace("{device-certificate}", device_certificate)
    templated_requests.post_to_uri(
        uri=f"{NETCONF_KEYSTORE_URL}:add-trusted-certificate",
        data=body,
        headers=HEADERS,
        expected_code=templated_requests.ALLOWED_STATUS_CODES,
    )


def register_global_credentials_for_ssh_call_home_devices(username: str, password: str):
    """Set global credentials for SSH call-home devices.

    Args:
        username (str): Username used for global login.
        password (str): Password used for global login.

    Returns:
        None
    """
    template = infra.get_file_content(CREATE_GLOBAL_CREDENTIALS_REQ)
    body = template.replace("{username}", username)
    body = body.replace("{password}", password)
    templated_requests.put_to_uri_request(
        uri=GLOBAL_CONFIG_URL,
        data=body,
        headers=HEADERS,
        expected_code=templated_requests.ALLOWED_STATUS_CODES,
    )


def register_ssh_call_home_device_in_odl_controller(
    device_name: str,
    hostkey: str,
    username: str = "",
    password: str = "",
):
    """Registration call-home device with SSH transport using latest models.

    Args:
        device_name (str): Call-home device name.
        hostkey (str): Hostkey used for host authentication.
        username (str | None): Username used for client authentication.
        password (str | None): Password used for client authentication.

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
    """Returns json template for registering device including credentials.

    Args:
       None

    Returns:
        str: Json template
    """
    template = infra.get_file_content(CREATE_SSH_DEVICE_REQ)

    return template


def get_create_device_request_without_credentials_template():
    """Returns json template for registering device without credentials.

    Args:
       None

    Returns:
        str: Json template
    """
    template = infra.get_file_content(CREATE_SSH_DEVICE_REQ_HOST_KEY_ONLY)

    return template


def register_tls_call_home_device_in_odl_controller(
    device_name: str, key_id: str, certificate_id: str
):
    """Registration call-home device with TLS transport.

    Args:
        device_name (str): Call-home device name.
        key_id (str): Id of the key used for authentication.
        certificate_id (str): Id of the certificate used for authentication.

    Returns:
        None
    """
    template = infra.get_file_content(CREATE_TLS_DEVICE_REQ)
    body = template.replace("{device_name}", device_name)
    body = body.replace("{key_id}", key_id)
    body = body.replace("{certificate_id}", certificate_id)
    templated_requests.post_to_uri(
        uri=WHITELIST,
        data=body,
        headers=HEADERS,
        expected_code=templated_requests.ALLOWED_STATUS_CODES,
    )


def check_device_status(device_id: str | None, status: str):
    """Checks the operational device status.

    Args:
        device_id (str): Device uniqe-id.
        status (str): Expected device status.

    Returns:
        None
    """
    if device_id is None:
        restconf_utils.check_for_elements_at_uri(
            DEVICE_STATUS, (f'"device-status":"{status}"',)
        )
    else:
        resp = templated_requests.get_from_uri(uri=DEVICE_STATUS, expected_code=None)
        devices = resp.json()["odl-netconf-callhome-server:allowed-devices"]["device"]
        for device in devices:
            if device["unique-id"] == device_id:
                assert device["device-status"] == status, (
                    f"Expected status: {status} does not match present status: "
                    f'{device["device-status"]} for device id: {device_id}.'
                )
                return
        raise AssertionError(
            f"Did not find expected device {device_id} in the allowed-devices."
        )
