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
from suites.suite_order import SuiteOrder


ODL_IP = variables.ODL_IP
RESTCONF_ROOT = variables.RESTCONF_ROOT
ODL_NETCONF_MDSAL_PORT = variables.ODL_NETCONF_MDSAL_PORT
USE_NETCONF_CONNECTOR = variables.USE_NETCONF_CONNECTOR

NETCONFREADY_WAIT = 60
NETCONFREADY_FALLBACK_WAIT = 1200
DEBUG_LOGGING_FOR_EVERYTHING = False
NETCONFREADY_WAIT_MDSAL = 60
DEVICE_NAME = "test-device"
DEVICE_PORT = 2830
NETCONF_FOLDER = "variables/netconf/device"
NETCONF_CONNECTOR_SUFFIX = (
    "/node/controller-config/yang-ext:mount/config:modules/module"
    "/sal-restconf-service:json-restconf-service-impl/json-restconf-service-impl"
)

log = logging.getLogger(__name__)


def get_netconf_topology_uri(pretty_print: bool = False) -> str:
    """Build the URI for the netconf topology endpoint

    If USE_NETCONF_CONNECTOR is true, the controller-config mount suffix is appended.

    Args:
        pretty_print (bool): If True, appends the odl-pretty-print=true query
            parameter to the URI.

    Returns:
        str: RESTCONF URI for the netconf topology.
    """
    uri = (
        f"{RESTCONF_ROOT}/data/network-topology:network-topology"
        f"/topology=topology-netconf"
    )
    if USE_NETCONF_CONNECTOR:
        uri += NETCONF_CONNECTOR_SUFFIX
    if pretty_print:
        uri += "?odl-pretty-print=true"
    return uri


def check_netconf_topology_ready():
    """Check readiness of the topology by temporarily mounting test device

    It configures and mounts a test device and then checks if the mount point
    and netconf state are accessible through RESTCONF.
    """
    try:
        netconf.configure_device_in_netconf(
            device_name=DEVICE_NAME,
            device_type="full-uri-device",
            device_address=ODL_IP,
            device_port=DEVICE_PORT,
            device_user="admin",
            device_password="admin",
        )
        mapping = {"DEVICE_NAME": DEVICE_NAME, "RESTCONF_ROOT": RESTCONF_ROOT}
        utils.wait_until_function_pass(
            10,
            3,
            templated_requests.get_templated_request,
            f"{NETCONF_FOLDER}/full-uri-mount",
            mapping,
        )
        utils.wait_until_function_pass(
            5,
            3,
            templated_requests.get_templated_request,
            f"{NETCONF_FOLDER}/netconf-state",
            mapping,
        )
    finally:
        netconf.remove_device_from_netconf(DEVICE_NAME)


def check_netconf_up_and_running(pretty_print: bool = False) -> None:
    """Check if NETCONF topology data is accessible through RESTCONF

    Also checks for response body content, if 'data model content does not exist'
    is returned, it annotates the test result with a link to known bug 5832.

    Args:
        pretty_print (bool): If True, requests pretty-printed output from ODL.

    Returns:
        None
    """
    resp = templated_requests.get_from_uri(get_netconf_topology_uri(pretty_print))
    log.debug(f"Topology response: {resp.text}")
    assert resp.status_code == 200

    newline_count = resp.text.count("\n")
    if pretty_print:
        assert newline_count > 0, (
            "Expected a multi-line formatted response because 'pretty_print' "
            "is enabled, but the response contained no newlines."
        )
    else:
        assert newline_count == 0, (
            f"Expected a single-line response because 'pretty_print' is disabled, "
            f"but found {newline_count} newline(s)."
        )

    with utils.report_known_bug_on_failure("5832"):
        assert "data model content does not exist" not in resp.text


def check_netconf_mdsal_up_and_running() -> None:
    """Checks that the MDSAL NETCONF port is in the LISTEN state

    Returns:
        None
    """
    count = infra.count_port_occurrences(ODL_NETCONF_MDSAL_PORT, "LISTEN", "")
    assert (
        count == 1
    ), f"Expected MDSAL port {ODL_NETCONF_MDSAL_PORT} to be listening, got {count=}."


@pytest.mark.always
@pytest.mark.testtool
@pytest.mark.single_device
@pytest.mark.usefixtures("preconditions")
@pytest.mark.usefixtures("log_test_suite_start_end_to_karaf")
@pytest.mark.usefixtures("log_test_case_start_end_to_karaf")
@pytest.mark.run(order=SuiteOrder.NETCONF_READY)
class TestNetconfReady:

    @pytest.fixture(autouse=True, scope="class")
    def suite_setup(self):
        """Enable Karaf DEBUG logging for the suite if configured.

        Restores the default log level on teardown.
        """
        if DEBUG_LOGGING_FOR_EVERYTHING:
            infra.execute_karaf_command("log:set DEBUG")
        yield
        if DEBUG_LOGGING_FOR_EVERYTHING:
            infra.execute_karaf_command("log:set INFO")

    @allure.description(
        textwrap.dedent(
            """
            **Netconf topology readiness validation test case.**

            Verifies that the core NETCONF topology endpoint becomes available \
            and functional. This test configures a dynamic test device directly \
            via RESTCONF and ensures that the node mount and netconf-state endpoints \
            successfully return data within the initial setup thresholds.

            *Note: This test case is skipped if `USE_NETCONF_CONNECTOR` is true, \
            as the readiness checking responsibilities shift to the controller-config \
            endpoints.*
            """
        )
    )
    def test_netconf_topology_ready(self, allure_step_with_separate_logging):
        if USE_NETCONF_CONNECTOR:
            pytest.skip(
                "Skipped: USE_NETCONF_CONNECTOR is enabled; readiness is verified "
                "by test_netconf_connector_ready."
            )
        with allure_step_with_separate_logging(
            "step_check_whether_netconf_topology_is_ready"
        ):
            utils.wait_until_function_pass(10, 1, check_netconf_topology_ready)

    @pytest.mark.dependency(name="test_netconf_connector_ready")
    @allure.description(
        textwrap.dedent(
            """
            **Netconf connector initialization test case.**

            Iteratively verifies whether the NETCONF topology connector endpoint \
            becomes active, implementing a phased retry strategy to compensate \
            for slow startup periods.

            The validation cycle performs:
            1. An immediate check for netconf topology retrieval.
            2. A standard retry wait window bounded by `NETCONFREADY_WAIT`.
            3. A fallback prolonged retry window bounded by \
            `NETCONFREADY_FALLBACK_WAIT` to handle delayed initializations.
            4. A defensive functional assertion for **Bug 5014**, where NETCONF \
            remains in a dead state on boot until a device configuration request \
            explicitly triggers it awake.
            """
        )
    )
    def test_netconf_connector_ready(self, allure_step_with_separate_logging):
        last_error = None

        wait_steps = [
            (
                "step_check_whether_netconf_connector_is_up_and_running",
                check_netconf_up_and_running,
            ),
            (
                "step_wait_for_netconf_connector",
                lambda: utils.wait_until_function_pass(
                    NETCONFREADY_WAIT, 1, check_netconf_up_and_running
                ),
            ),
            (
                "step_wait_even_longer",
                lambda: utils.wait_until_function_pass(
                    NETCONFREADY_FALLBACK_WAIT, 1, check_netconf_up_and_running
                ),
            ),
        ]

        for step_name, action in wait_steps:
            with allure_step_with_separate_logging(step_name):
                try:
                    action()
                    return  # Netconf is up, test passes.
                except AssertionError as e:
                    # Step failed, but does not fail the test — continue to next step
                    # with longer timeout.
                    log.warning(f"Step '{step_name}' failed: {e}")
                    last_error = e

        with allure_step_with_separate_logging("step_check_for_bug_5014"):
            # Netconf did not start properly, check whether this matches known Bug 5014.
            netconf.configure_device_in_netconf(
                device_name=DEVICE_NAME, device_type="configure-via-topology"
            )
            netconf.remove_device_from_netconf(device_name=DEVICE_NAME)
            with utils.report_known_bug_on_failure("5014"):
                utils.run_function_and_expect_error(check_netconf_up_and_running)

        pytest.fail(
            f"Netconf connector did not become ready during the whole wait time. "
            f"Last encountered error: {last_error}"
        )

    @pytest.mark.dependency(depends=["test_netconf_connector_ready"])
    @allure.description(
        textwrap.dedent(
            """
            **Netconf pretty-print payload formatting validation test case.**

            Verifies that the OpenDaylight Controller correctly structures \
            the netconf topology response text when explicitly requested via an HTTP \
            query parameter.
            """
        )
    )
    def test_pretty_print(self, allure_step_with_separate_logging):
        with allure_step_with_separate_logging(
            "step_check_whether_netconf_can_pretty_print"
        ):
            check_netconf_up_and_running(pretty_print=True)

    @allure.description(
        textwrap.dedent(
            """
            **Northbound NETCONF server for MDSAL initialization test case.**

            Ensure that the MDSAL-specific northbound server properly binds its port.
            """
        )
    )
    def test_mdsal_ready(self, allure_step_with_separate_logging):
        if not infra.is_karaf_feature_installed("odl-netconf-mdsal"):
            pytest.skip(
                "The 'odl-netconf-mdsal' feature is not installed, skipping port "
                "readiness check."
            )
        with allure_step_with_separate_logging("step_wait_for_mdsal"):
            utils.wait_until_function_pass(
                NETCONFREADY_WAIT_MDSAL, 1, check_netconf_mdsal_up_and_running
            )
