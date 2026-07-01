#
# Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License v1.0 which accompanies this distribution,
# and is available at http://www.eclipse.org/legal/epl-v10.html
#

from collections.abc import Callable
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timedelta
import logging
import subprocess
import math
import re

from libraries import infra
from libraries import restconf
from libraries import restconf_utils
from libraries import templated_requests
from libraries import utils
from libraries.variables import variables

MAX_HEAP = "1G"
TESTTOOL_DEFAULT_JAVA_OPTIONS = (
    f"-Xmx{MAX_HEAP} -Djava.security.egd=file:/dev/./urandom"
)
DIRECTORY_WITH_DEVICE_TEMPLATES = "variables/netconf/device"
FIRST_TESTTOOL_PORT = 17830
BASE_NETCONF_DEVICE_PORT = 17830
DEVICE_NAME_BASE = "netconf-scaling-device"
TESTTOOL_BOOT_TIMEOUT = 60
ENABLE_NETCONF_TEST_TIMEOUT = variables.ENABLE_GLOBAL_TEST_DEADLINES
RESTCONF_ROOT = variables.RESTCONF_ROOT
REST_API = variables.REST_API
TOOLS_IP = variables.TOOLS_IP
ODL_NETCONF_NAMESPACE = variables.ODL_NETCONF_NAMESPACE

NETCONF_MOUNTED_DEVICE_TYPES = dict()

log = logging.getLogger(__name__)


def check_device_has_no_netconf_connector(device_name: str):
    """Check that there are no instances of the specified device
    in the Netconf topology.

    Args:
        device_name (str): The name of the device to be checked.

    Returns:
        None
    """
    # FIXME: Similarly to "Count_Netconf_Connectors_For_Device", this does not check
    # whether the device has no netconf connector but whether the device is present
    # in the netconf topology or not. Rename, proposed new name:
    # Check_Device_Not_Present_In_Netconf_Topology
    count = count_netconf_connectors_for_device(device_name)
    assert count == 0


def check_device_connected(device_name: str):
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


def check_device_completely_gone(device_name: str):
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
        f"node={device_name}",
    )
    restconf_utils.no_content_from_uri(uri)


def count_netconf_connectors_for_device(device_name: str) -> int:
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
    actual_count = len(mounts.split(f'"node-id":"{device_name}"')) - 1

    return actual_count


def configure_device_in_netconf(
    device_name: str,
    device_type: str = "default",
    device_port: int = FIRST_TESTTOOL_PORT,
    device_address: str = TOOLS_IP,
    device_user: str = "admin",
    device_password: str = "topsecret",
    device_key: str = "device-key",
    schema_directory: str = "/tmp/schema",
    http_timeout: float | tuple[float, float] | None = None,
    http_method: str = "put",
):
    """Tell Netconf about the specified device so it can add it into its configuration.

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
            f"{DIRECTORY_WITH_DEVICE_TEMPLATES}/{device_type}",
            mapping,
            http_timeout=http_timeout,
            json=False,
        )
    else:
        templated_requests.put_templated_request(
            f"{DIRECTORY_WITH_DEVICE_TEMPLATES}/{device_type}",
            mapping,
            http_timeout=http_timeout,
            json=False,
        )
        NETCONF_MOUNTED_DEVICE_TYPES[device_name] = device_type


def wait_device_connected(device_name: str, timeout: int = 20, period: int = 1):
    """Wait for the device to become connected.

    It is more readable to use this function in a test case than to put the whole
    wait_until_function_pass below into it.

    Args:
        device_name (str): The name of the device to be connected.
        timeout (int): Maximum time in seconds to wait for the connection.
            Note: If the timeout is not evenly divisible by the period, the total
            allowed wait time will be rounded up to the nearest multiple of the period.
        period (int): Time in seconds between polling attempts.

    Returns:
        None
    """
    utils.wait_until_function_pass(
        math.ceil(timeout / period), period, check_device_connected, device_name
    )


def check_device_is_up(last_port: int):
    """Verify port is actively used by the device

    Args:
        last_port (int): The port number to check for a 'LISTEN' state.

    Returns:
        None
    """
    count = infra.count_port_occurrences(last_port, "LISTEN", "")
    assert count == 1


def check_device_is_up_and_running(device_number: int):
    """Check device port is open.

    Query ss whether testtool device with the specified number has its port
    open and fail if not.

    Args:
        device_number (int): The index number of the device.

    Returns:
        None
    """
    device_port = FIRST_TESTTOOL_PORT + device_number
    check_device_is_up(device_port)


def wait_device_is_up_and_running(device_name: str, log_response: bool = True):
    """Wait until the device is fully started and listening on its designated port.

    Args:
        device_name (str): The full name of the device. It needs to be in format
            NAME-INDEX (e.g., 'netconf-scaling-device-5').
        log_response (bool): Whether to log the polling responses. Defaults to True.

    Returns:
        None
    """
    number = int(device_name.split("-").pop())
    utils.wait_until_function_pass(
        TESTTOOL_BOOT_TIMEOUT, 1, check_device_is_up_and_running, number
    )


def remove_device_from_netconf(device_name: str):
    """Tell Netconf to deconfigure the specified device.

    Args:
        device_name (str): The name of the device to be removed.

    Returns:
        None
    """
    device_type = NETCONF_MOUNTED_DEVICE_TYPES.pop(device_name)
    mapping = {"DEVICE_NAME": device_name, "RESTCONF_ROOT": RESTCONF_ROOT}
    templated_requests.delete_templated_request(
        f"{DIRECTORY_WITH_DEVICE_TEMPLATES}/{device_type}", mapping
    )


def wait_device_fully_removed(device_name: str, timeout: int = 10, period: int = 1):
    """Wait until all netconf connectors for the device with the given name disappear.

    Call of Remove_Device_From_Netconf returns before netconf gets
    around deleting the device's connector. To ensure the device is
    really gone from netconf, use this function to make sure all
    connectors disappear. If a call to remove_device_from_netconf
    is not made before using this function, the wait will fail.
    Using this function is more readable than putting the wait_until_function_* below
    into a test case.

    Args:
        device_name (str): The name of the device to be removed.
        timeout (int): Maximum time in seconds to wait for complete removal.
            Note: If the timeout is not evenly divisible by the period, the total
            allowed wait time will be rounded up to the nearest multiple of the period.
        period (int): Time in seconds between polling attempts.

    Returns:
        None
    """
    utils.wait_until_function_pass(
        math.ceil(timeout / period), period, check_device_completely_gone, device_name
    )


def start_testtool(
    filename: str,
    device_count: int = 10,
    debug: bool = True,
    schemas: str | None = None,
    rpc_config: str | None = None,
    tool_options: str = "",
    java_options: str = TESTTOOL_DEFAULT_JAVA_OPTIONS,
    mdsal: bool = True,
    log_response: bool = True,
) -> subprocess.Popen:
    """Start the Netconf testtool in the background and wait to become responsive.

    Arrange to collect tool's output into a log file.
    Will use specific ${schemas} unless argument resolves to 'none',
    which signifies that there are no additional schemas to be deployed.
    If so the directory for the additional schemas is deleted on the
    remote machine and the additional schemas argument is left out.

    Args:
        filename (str): The path to the testtool jar file.
        device_count (int): The number of Netconf devices to simulate.
        debug (bool): Whether to start the testtool with debug log level.
        schemas (str | None): Path to additional schemas to deploy.
        rpc_config (str | None): Path to an optional custom RPC configuration file.
        tool_options (str): Additional CLI arguments to pass to the testtool.
        java_options (str): JVM arguments for the testtool execution.
        mdsal (bool): Whether to use MD-SAL datastore.
        log_response (bool): Whether to log the startup polling responses.

    Returns:
        subprocess.Popen: The background process object representing the running
            testtool, augmented with the `testtool_log_filename` attribute.
    """
    schemas_option = deploy_additional_schemas(schemas)
    rpc_config_option = deploy_custom_rpc(rpc_config)
    command = f"java {java_options} -jar {filename} {tool_options} --device-count {device_count} --debug {debug} {schemas_option} {rpc_config_option} --md-sal {mdsal}"
    log.info(f"Running testtool: {command}")
    logfile = utils.get_log_file_name("testtool")
    process = infra.shell(f"{command} >tmp/{logfile} 2>&1", run_in_background=True)
    process.testtool_log_filename = logfile
    perform_operation_on_each_device(
        wait_device_is_up_and_running, device_count, log_response=log_response
    )

    return process


def stop_testtool(process: subprocess.Popen, gracefully: bool = True):
    """Stop pcep pcc mock process by sending SIGINT signal.

    Args:
        process (subprocess.Popen): Netconf testtool process handler.
        gracefully (bool): If True, attempts a graceful shutdown before forcing a kill.

    Returns:
        None
    """
    stdout = process.stdout
    log.debug(f"Testtool stdout:\n{stdout}")
    log.info(f"Killing testtool process with PID {process.pid}")
    infra.stop_process_by_pid(process.pid, gracefully=gracefully, timeout=150)
    logfile = process.testtool_log_filename
    infra.shell(f"cp tmp/{logfile} results/")


def deploy_additional_schemas(schemas: str | None) -> str:
    """Copy additional schemas files to common tmp/schemas dir location.

    Internal function for start_testtool
    This deploys the additional schemas if any and returns a
    command line argument to be added to the testtool commandline
    to tell it to load them. While this code could be integrated
    into its only user, I considered the resulting code to be too
    unreadable as the actions are quite different in the two
    possibilities (additional schemas present versus no additional
    schemas present), therefore a separate function is used.

    Args:
        schemas (str | None): The path to the schemas directory.

    Returns:
        str: The formatted command line argument for testtool.
    """
    # Make sure there is no schemas directory on the local machine. A
    # previous test suite might have left some debris there and that might
    # lead to spurious failures, so it is better to make sure we start with a
    # clean slate. Additionally when the caller did not specify any
    # additional schemas for testtool, we want to make extra sure none are
    # used.
    rc, response = infra.shell("rm -rf schemas 2>&1")
    log.info(f"{response=}")
    if not schemas:
        return ""
    infra.shell("rm -rf tmp/schemas")
    infra.copy_dir(schemas, "tmp/schemas")
    rc, response = infra.shell("ls tmp/schemas")
    log.info(f"{response=}")

    return "--schemas-dir ./tmp/schemas"


def deploy_custom_rpc(rpc_config: str | None) -> str:
    """Copy RPC config file to common tmp/ dir location.

    Internal function for start_testtool
    This deploys the optional custom rpc file.
    Drop out of the function, returning no command line argument when there
    is no rpc file to deploy.

    Args:
        rpc_config (str | None): The path to the RPC configuration file.

    Returns:
        str: The formatted command line argument for testtool.
    """
    if not rpc_config:
        return ""
    infra.shell(f"cp {rpc_config} tmp/")
    return "--rpc-config tmp/customaction.xml"


def perform_operation_on_each_device(
    operation: Callable, count: int, timeout: int = 45, log_response: bool = True
):
    """Execute a specified operation on number of specified devices.

    Args:
        operation (Callable): The function to be execute on each device.
        count (int): The total number of devices.
        timeout (int): The maximum allowed time in seconds for the entire execution.
        log_response (bool): Whether to log the operation's output.

    Returns:
        None
    """
    current_date = datetime.now()
    deadline_date = current_date + timedelta(seconds=timeout)
    for i in range(count):
        perform_operation_with_checking_on_next_device(
            i, operation, deadline_date, log_response
        )


def perform_operation_with_checking_on_next_device(
    number: int, operation: Callable, deadline_date: datetime, log_response: bool = True
):
    """Execute the operation on the specific device identified by its index number.

    Args:
        number (int): The index number of the device.
        operation (Callable): The function to be executed on the device.
        deadline_date (datetime): The absolute timestamp when the timeout expires.
        log_response (bool): Whether to log the operation's output.

    Returns:
        None
    """
    check_netconf_test_timeout_not_expired(deadline_date)
    operation(f"{DEVICE_NAME_BASE}-{number}", log_response=log_response)


def check_netconf_test_timeout_not_expired(deadline_date: datetime):
    """Check if the current time has already exceeded the specified deadline.

    Args:
        deadline_date (datetime): The absolute timestamp indicating when
            the timeout expires.

    Returns:
        None
    """
    if not ENABLE_NETCONF_TEST_TIMEOUT:
        return
    current_date = datetime.now()
    if current_date > deadline_date:
        raise AssertionError("The global time out period expired")


def configure_device(
    device_name: str, device_type: str = "full-uri-device", log_response: bool = True
):
    """Configure a single scaling device on the Netconf connector.

    Per-device operation suitable for perform_operation_on_each_device. The
    device's port is derived from the trailing index in its name so that each
    device points at its own testtool port (FIRST_TESTTOOL_PORT + index).

    Args:
        device_name (str): The full name of the device in format NAME-INDEX
            (e.g. 'netconf-scaling-device-0').
        device_type (str): The template type for the device.
        log_response (bool): Whether to log the operation's output. Accepted for
            interface compatibility with perform_operation_on_each_device.

    Returns:
        None
    """
    number = int(device_name.split("-").pop())
    device_port = FIRST_TESTTOOL_PORT + number
    configure_device_in_netconf(
        device_name, device_type=device_type, device_port=device_port
    )


def wait_connected(device_name: str, log_response: bool = True):
    """Wait until a single scaling device becomes connected through Netconf.

    Per-device operation suitable for perform_operation_on_each_device.

    Args:
        device_name (str): The name of the device to wait for.
        log_response (bool): Whether to log the operation's output. Accepted for
            interface compatibility with perform_operation_on_each_device.

    Returns:
        None
    """
    wait_device_connected(device_name, timeout=300, period=0.5)


def deconfigure_device(device_name: str, log_response: bool = True):
    """Deconfigure a single scaling device from the Netconf connector.

    Per-device operation suitable for perform_operation_on_each_device.

    Args:
        device_name (str): The name of the device to be removed.
        log_response (bool): Whether to log the operation's output. Accepted for
            interface compatibility with perform_operation_on_each_device.

    Returns:
        None
    """
    remove_device_from_netconf(device_name)


def check_device_deconfigured(device_name: str, log_response: bool = True):
    """Wait until a single scaling device is completely gone from Netconf.

    Per-device operation suitable for perform_operation_on_each_device.

    Args:
        device_name (str): The name of the device expected to disappear.
        log_response (bool): Whether to log the operation's output. Accepted for
            interface compatibility with perform_operation_on_each_device.

    Returns:
        None
    """
    wait_device_fully_removed(device_name, timeout=120, period=0.5)


def check_device_data_is_empty(device_name: str, log_response: bool = True):
    """Get the configuration data of a device and check that it is empty.

    Per-device operation: requests the configuration data of the device mounted
    onto its Netconf connector and asserts that the returned data is the empty
    document (either '<data .../>' or '<data ...></data>').

    Args:
        device_name (str): The name of the device to query.
        log_response (bool): Whether to log the operation's output. Accepted for
            interface compatibility with perform_operation_on_each_device.

    Returns:
        None
    """
    uri = (
        f"{REST_API}/network-topology:network-topology/topology=topology-netconf/"
        f"node={device_name}/yang-ext:mount?content=config"
    )
    headers = {"Accept": "application/yang-data+xml"}
    data = templated_requests.get_from_uri(uri, headers=headers).text
    escaped = re.escape(ODL_NETCONF_NAMESPACE)
    assert (
        re.match(rf'<data xmlns="{escaped}"(\/>|></data>)', data) is not None
    ), f"Device {device_name} returned unexpected non-empty data: {data}"


def get_data_from_devices_concurrently(device_count: int, worker_count: int):
    """Issue GET requests to every scaling device concurrently and verify them.

    Reimplements the multi-threaded behavior of the netconf_tools/getter.py tool:
    a pool of worker_count workers together issues a GET request for the
    configuration data of each of the device_count devices and verifies that
    each device returns empty data. All failures are collected and reported
    together so that a single failing device does not mask the others.

    The project's templated_requests helpers are reused instead of running the
    standalone getter.py tool: its AuthStandalone helper hardcodes a RESTCONF
    prefix (/rests/, port 8181) that does not match this project (restconf,
    port 8182), and running in-process avoids spawning a subprocess and parsing
    the tool's stdout.

    Args:
        device_count (int): The number of devices to query.
        worker_count (int): The number of concurrent worker threads to use.

    Returns:
        None
    """
    device_names = [f"{DEVICE_NAME_BASE}-{number}" for number in range(device_count)]
    errors = {}
    with ThreadPoolExecutor(max_workers=worker_count) as executor:
        future_to_name = {
            executor.submit(check_device_data_is_empty, name): name
            for name in device_names
        }
        for future in as_completed(future_to_name):
            name = future_to_name[future]
            try:
                future.result()
            except Exception as e:
                errors[name] = str(e)
    assert not errors, (
        f"GET requests failed for {len(errors)}/{device_count} devices: {errors}"
    )


def configure_device_and_verify(
    device_name: str, device_type: str = "full-uri-device", log_response: bool = True
):
    """Configure a single scaling device and wait until it is connected.

    Per-device operation suitable for perform_operation_on_each_device: configures
    the device on the Netconf connector and then waits for it to become connected
    before returning, so devices are brought up and verified one at a time.

    Args:
        device_name (str): The full name of the device in format NAME-INDEX
            (e.g. 'netconf-scaling-device-0').
        device_type (str): The template type for the device.
        log_response (bool): Whether to log the operation's output. Accepted for
            interface compatibility with perform_operation_on_each_device.

    Returns:
        None
    """
    configure_device(device_name, device_type=device_type, log_response=log_response)
    wait_connected(device_name, log_response=log_response)


def deconfigure_device_and_verify(device_name: str, log_response: bool = True):
    """Deconfigure a single scaling device and wait until it is fully removed.

    Per-device operation suitable for perform_operation_on_each_device:
    deconfigures the device from the Netconf connector and then waits until it has
    completely disappeared from the Netconf topology before returning.

    Args:
        device_name (str): The name of the device to be removed.
        log_response (bool): Whether to log the operation's output. Accepted for
            interface compatibility with perform_operation_on_each_device.

    Returns:
        None
    """
    deconfigure_device(device_name, log_response=log_response)
    check_device_deconfigured(device_name, log_response=log_response)
