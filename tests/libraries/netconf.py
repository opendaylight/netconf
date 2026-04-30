#
# Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License v1.0 which accompanies this distribution,
# and is available at http://www.eclipse.org/legal/epl-v10.html
#

import logging

from libraries import restconf
from libraries import restconf_utils
from libraries import templated_requests
from libraries import utils
from libraries.variables import variables

DIRECTORY_WITH_DEVICE_TEMPLATES = "variables/netconf/device"
FIRST_TESTTOOL_PORT = 17830
RESTCONF_ROOT = variables.RESTCONF_ROOT
TOOLS_IP = variables.TOOLS_IP

NETCONF__MOUNTED_DEVICE_TYPES = dict()

log = logging.getLogger(__name__)


def check_device_has_no_netconf_connector(device_name):
    """Check that there are no instances of the specified device
    in the Netconf topology.

    Args:
        device_name (str): The name of the device to be checked.

    Returns:
        None
    """
    # FIXME: Similarlt to "Count_Netconf_Connectors_For_Device", this does not check
    # whether the device has no netconf connector but whether the device is present
    # in the netconf topology or not. Rename, proposed new name:
    # Check_Device_Not_Present_In_Netconf_Topology
    count = count_netconf_connectors_for_device(device_name)
    assert count == 0


def check_device_connected(device_name):
    """Check that the specified device is accessible from Netconf.

    Args:
        device_name (str): The name of the device to be verified.

    Returns:
        None
    """
    uri = restconf.generate_uri(
        "network-topology:network-topology",
        "operational",
        "topology=topology-netconf",
        f"node={device_name}",
    )
    resp = templated_requests.get_from_uri(uri)
    device_status = resp.text
    assert 'connection-status":"connected"' in device_status


def check_device_completely_gone(device_name):
    """Check that the specified device has no Netconf connectors nor associated data.

    Args:
        device_name (str): The name of the device to be verified as removed.

    Returns:
        None
    """
    check_device_has_no_netconf_connector(device_name)
    uri = restconf.generate_uri(
        "network-topology:network-topology",
        "config",
        'topology="topology-netconf"',
        "node=device_name",
    )
    restconf_utils.no_content_from_uri(uri)


def count_netconf_connectors_for_device(device_name):
    """Count all instances of the specified device in the Netconf topology
    (usually 0 or 1).

    Args:
        device_name (str): The name of the device to be counted.

    Returns:
        int: The number of times the device appears in the Netconf topology.
    """
    # FIXME: This no longer counts netconf connectors, it counts "device instances
    # in Netconf topology". This function should be renamed but without an automatic
    # function naming standards checker this is potentially destabilizing change
    # so right now it is as FIXME. Proposed new name:
    # Count_Device_Instances_In_Netconf_Topology
    uri = restconf.generate_uri("network-topology:network-topology", "operational")
    resp = templated_requests.get_from_uri(uri)
    mounts = resp.text
    log.info(f"{mounts=}")
    actual_count = len(mounts.split(f'"node-id": "{device_name}"')) - 1

    return actual_count


def configure_device_in_netconf(
    device_name,
    device_type="default",
    device_port=FIRST_TESTTOOL_PORT,
    device_address=TOOLS_IP,
    device_user="admin",
    device_password="topsecret",
    device_key="device-key",
    schema_directory="/tmp/schema",
    http_timeout=None,
    http_method="put",
):
    """
    Tell Netconf about the specified device so it can add it into its configuration.

    Args:
        device_name (str): The name of the device to be configured.
        device_type (str): The template type for the device.
        device_port (int): The port the device is listening on.
        device_address (str): The IP address of the device.
        device_user (str): Username for device authentication.
        device_password (str): Password for device authentication.
        device_key (str): Device key identifier.
        schema_directory (str): Path to the schema directory.
        http_timeout (float | tuple[float, float] | None): How many seconds to wait for
            the server to send data before giving up.
        http_method (str): The HTTP method to use ("put" or "post").

    Returns:
        None
    """
    mapping = {
        "DEVICE_IP": device_address,
        "DEVICE_NAME": device_name,
        "DEVICE_PORT": device_port,
        "DEVICE_USER": device_user,
        "DEVICE_PASSWORD": device_password,
        "DEVICE_KEY": device_key,
        "SCHEMA_DIRECTORY": schema_directory,
        "RESTCONF_ROOT": RESTCONF_ROOT,
    }
    if http_method == "post":
        templated_requests.post_templated_request(
            f"{DIRECTORY_WITH_DEVICE_TEMPLATES}/scandium/{device_type}",
            mapping,
            http_timeout=http_timeout,
            json=False,
        )
    else:
        templated_requests.put_templated_request(
            f"{DIRECTORY_WITH_DEVICE_TEMPLATES}/scandium/{device_type}",
            mapping,
            http_timeout=http_timeout,
            json=False,
        )
        NETCONF__MOUNTED_DEVICE_TYPES[device_name] = device_type


def wait_device_connected(device_name, timeout=20, period=1):
    """
    Wait for the device to become connected.

    It is more readable to use this function in a test case than to put the whole
    wait_until_function_pass below into it.

    Args:
        device_name (str): The name of the device to be connected.
        timeout (int): Maximum time in seconds to wait for the connection.
        period (int): Time in seconds between polling attempts.

    Returns:
        None
    """
    utils.wait_until_function_pass(
        int(timeout / period), period, check_device_connected, device_name
    )


def remove_device_from_netconf(device_name):
    """Tell Netconf to deconfigure the specified device.

    Args:
        device_name (str): The name of the device to be removed.

    Returns:
        None
    """
    device_type = NETCONF__MOUNTED_DEVICE_TYPES.pop(device_name)
    mapping = {"DEVICE_NAME": device_name, "RESTCONF_ROOT": RESTCONF_ROOT}
    templated_requests.delete_templated_request(
        f"{DIRECTORY_WITH_DEVICE_TEMPLATES}/scandium/{device_type}", mapping
    )


def wait_device_fully_removed(device_name, timeout=10, period=1):
    """
    Wait until all netconf connectors for the device with the given name disappear.

    Call of Remove_Device_From_Netconf returns before netconf gets
    around deleting the device's connector. To ensure the device is
    really gone from netconf, use this keyword to make sure all
    connectors disappear. If a call to Remove_Device_From_Netconf
    is not made before using this keyword, the wait will fail.
    Using this keyword is more readable than putting the WUKS below
    into a test case.

    Args:
        device_name (str): The name of the device to be removed.
        timeout (int): Maximum time in seconds to wait for complete removal.
        period (int): Time in seconds between polling attempts.

    Returns:
        None
    """
    utils.wait_until_function_pass(
        int(timeout / period), period, check_device_completely_gone, device_name
    )
