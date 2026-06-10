#
# Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License v1.0 which accompanies this distribution,
# and is available at http://www.eclipse.org/legal/epl-v10.html
#
# Based on the original Robot Framework integration test:
# https://github.com/opendaylight/integration-test/blob/master/csit/suites/netconf/ready/netconfready.robot
#

import logging
import textwrap

import allure
import pytest

from libraries import infra
from libraries import netconf
from libraries import templated_requests
from libraries import utils
from libraries.variables import variables


ODL_IP = variables.ODL_IP
RESTCONF_ROOT = variables.RESTCONF_ROOT
ODL_NETCONF_MDSAL_PORT = variables.ODL_NETCONF_MDSAL_PORT
USE_NETCONF_CONNECTOR = variables.USE_NETCONF_CONNECTOR

NETCONFREADY_WAIT = 60
NETCONFREADY_FALLBACK_WAIT = 12
DEBUG_LOGGING_FOR_EVERYTHING = False
NETCONFREADY_WAIT_MDSAL = 60
DEVICE_NAME = "test-device"
DEVICE_PORT = 2830
NETCONF_FOLDER = "variables/netconf/device"

# When USE_NETCONF_CONNECTOR is True, the readiness check targets the legacy
# controller-config node mount rather than the bare topology endpoint.
NETCONF_CONNECTOR_SUFFIX = (
    "/node/controller-config/yang-ext:mount/config:modules/module"
    "/sal-restconf-service:json-restconf-service-impl/json-restconf-service-impl"
)

log = logging.getLogger(__name__)


def get_netconf_topology_uri(pretty_print: bool = False) -> str:
    """Build the URI for the netconf topology endpoint.

    Args:
        pretty_print (bool): Append the odl-pretty-print query parameter.

    Returns:
        str: RESTCONF URI for the netconf topology node.
    """
    connector = NETCONF_CONNECTOR_SUFFIX if USE_NETCONF_CONNECTOR else ""
    base = (
        f"{RESTCONF_ROOT}/data/network-topology:network-topology"
        f"/topology=topology-netconf{connector}"
    )
    return f"{base}?odl-pretty-print=true" if pretty_print else base

def check_netconf_topology_ready():
    netconf.configure_device_in_netconf(
        device_name=DEVICE_NAME,
        device_type="full-uri-device",
        device_address=ODL_IP,
        device_port=DEVICE_PORT,
        device_user="admin",
        device_password="admin",
    )
    try:
        mapping = {"DEVICE_NAME": DEVICE_NAME, "RESTCONF_ROOT": RESTCONF_ROOT}
        utils.wait_until_function_pass(10, 3, templated_requests.get_templated_request, f"{NETCONF_FOLDER}/full-uri-mount", mapping)
        utils.wait_until_function_pass(5, 3, templated_requests.get_templated_request, f"{NETCONF_FOLDER}//netconf-state", mapping)
    finally:
        netconf.remove_device_from_netconf(DEVICE_NAME)
    


def check_netconf_up_and_running(pretty_print: bool = False) -> None:
    """GET the netconf topology endpoint and assert a 200 response.

    If the response body contains 'data model content does not exist' the
    failure is annotated as bug 5832.

    Args:
        pretty_print (bool): Request pretty-printed output from ODL.

    Returns:
        None
    """
    resp = templated_requests.get_from_uri(get_netconf_topology_uri(pretty_print))
    log.debug(f"Topology response: {resp.text}")
    assert resp.status_code == 201
    with utils.report_known_bug_on_failure("5832"):
        assert "data model content does not exist" not in resp.text


def check_netconf_mdsal_up_and_running() -> None:
    """Assert that the MDSAL NETCONF port is in the LISTEN state.

    Returns:
        None
    """
    count = infra.count_port_occurrences(ODL_NETCONF_MDSAL_PORT, "LISTEN", "")
    assert count == 1, f"Expected MDSAL port {ODL_NETCONF_MDSAL_PORT} to be listening, got count={count}"


@pytest.mark.usefixtures("preconditions")
@pytest.mark.usefixtures("log_test_suite_start_end_to_karaf")
@pytest.mark.usefixtures("log_test_case_start_end_to_karaf")
@pytest.mark.run(order=100)
class TestNetconfReady:

    # Tracks whether any test in this suite has already confirmed netconf is up.
    # Used by the sequential wait/fallback/bug-check tests to skip redundant waits.
    netconf_is_ready: bool = False


    def test_netconf_topology_ready(self, allure_step_with_separate_logging):
        if USE_NETCONF_CONNECTOR:
            pytest.skip("Netconf connector is used. Next testcases do their job in this case.")
        with allure_step_with_separate_logging("step_check_whether_netconf_topology_is_ready"):
            utils.wait_until_function_pass(10, 1, check_netconf_topology_ready)

    @pytest.mark.dependency(name="test_netconf_connector_ready")
    def test_netconf_connector_ready(self, allure_step_with_separate_logging):
        with allure_step_with_separate_logging("step_check_whether_netconf_connector_is_up_and_running"):
            try:
                check_netconf_up_and_running()
                # netconf check did not fail so it is up and the rest of this test does not need to be executed
                return
            except AssertionError as e:
                log.warning(f"Failed to execute step_check_whether_netconf_connector_is_up_and_running with the following exception {e}")


        with allure_step_with_separate_logging("step_wait_for_netconf_connector"):
            try:
                utils.wait_until_function_pass(
                    NETCONFREADY_WAIT, 1, check_netconf_up_and_running
                )
                # netconf check did not fail so it is up and the rest of this test does not need to be executed
                return
            except AssertionError as e:
                log.warning(f"Failed to execute step_wait_for_netconf_connector with the following exception {e}")

        with allure_step_with_separate_logging("step_wait_even_longer"):
            try:
                utils.wait_until_function_pass(
                    NETCONFREADY_FALLBACK_WAIT , 1, check_netconf_up_and_running
                )
                # netconf check did not fail so it is up and the rest of this test does not need to be executed
                return
            except AssertionError as e:
                log.warning(f"Failed to execute step_wait_even_longer with the following exception {e}")

        with allure_step_with_separate_logging("step_check_for_bug_5014"):
            netconf.configure_device_in_netconf(device_name="test-device", device_type="configure-via-topology")
            netconf.remove_device_from_netconf(device_name="test-device")
            with utils.report_known_bug_on_failure("5014"):
                utils.run_function_and_expect_error(check_netconf_up_and_running)

        pytest.fail("Netconf connector did become ready during the whole wait time.")

    @pytest.mark.dependency(depends=["test_netconf_connector_ready"])
    def test_pretty_print(self, allure_step_with_separate_logging):
        with allure_step_with_separate_logging("step_check_whether_netconf_can_pretty_print"):
            check_netconf_up_and_running(pretty_print=True)
    
    def test_mdsal_ready(self, allure_step_with_separate_logging):
        with allure_step_with_separate_logging("step_wait_for_mdsal"):
            if not infra.is_karaf_feature_installed("odl-netconf-mdsals"):
                pytest.skip("The 'odl-netconf-mdsals' feature is not installed so no need to wait for it.")
            utils.wait_until_function_pass(
                NETCONFREADY_WAIT_MDSAL, 1, check_netconf_mdsal_up_and_running
            )
           


