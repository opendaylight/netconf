#
# Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License v1.0 which accompanies this distribution,
# and is available at http://www.eclipse.org/legal/epl-v10.html
#
# Based on the original Robot Framework integration test:
# https://github.com/opendaylight/integration-test/blob/master/csit/suites/netconf/scale/getmulti.robot
#

import functools
import logging
import textwrap

import allure
import pytest

from libraries import netconf
from libraries import utils
from libraries.variables import variables
from suites.suite_order import SuiteOrder


USE_NETCONF_CONNECTOR = variables.USE_NETCONF_CONNECTOR

# NOTE: The upstream Robot suite uses DEVICE_COUNT=500. The device count is
# reduced here so the suite is runnable on a single local / CI node; it is a
# plain constant and can be raised again once a target scale is agreed upon.
DEVICE_COUNT = 10
WORKER_COUNT = 10
TIMEOUT_FACTOR = 10
OPERATION_TIMEOUT = DEVICE_COUNT * TIMEOUT_FACTOR
DEVICE_TYPE = "default" if USE_NETCONF_CONNECTOR else "full-uri-device"


log = logging.getLogger(__name__)


@pytest.mark.usefixtures("preconditions")
@pytest.mark.usefixtures("log_test_suite_start_end_to_karaf")
@pytest.mark.usefixtures("log_test_case_start_end_to_karaf")
@pytest.mark.run(order=SuiteOrder.GETMULTI)
class TestGetmulti:

    @pytest.fixture
    def netconf_testtool(self, allure_step_with_separate_logging):
        """Start and manage the underlying Netconf testtool simulator process.

        Starts DEVICE_COUNT simulated devices, waits for all of them to come up,
        yields the running process and guarantees the process is terminated
        afterwards.

        Yields:
            subprocess.Popen: The running testtool process handler.
        """
        with allure_step_with_separate_logging("step_start_testtool"):
            """Deploy and start test tool, then wait for all its devices to
            become online."""
            testtool_process = netconf.start_testtool(
                "build_tools/netconf-testtool.jar",
                device_count=DEVICE_COUNT,
                debug=False,
                mdsal=True,
            )
        yield testtool_process
        with allure_step_with_separate_logging("step_stop_testtool"):
            """Stop netconf testtool."""
            netconf.stop_testtool(testtool_process)

    @allure.description(
        textwrap.dedent(
            """
            **netconf-connector scaling test suite (multi-threaded GET requests).**

            Performs scaling tests:
            - Send configurations of the devices one by one (via restconf).
            - Wait for the devices to become connected.
            - Send requests for configuration data using WORKER_COUNT worker \
            threads.
            - Deconfigure the devices one by one.
            """
        )
    )
    def test_getmulti(self, netconf_testtool, allure_step_with_separate_logging):

        with allure_step_with_separate_logging("step_configure_devices_on_netconf"):
            """Make requests to configure the testtool devices."""
            netconf.perform_operation_on_each_device(
                functools.partial(netconf.configure_device, device_type=DEVICE_TYPE),
                DEVICE_COUNT,
                timeout=OPERATION_TIMEOUT,
            )

        with allure_step_with_separate_logging("step_wait_for_devices_to_connect"):
            """Wait for the devices to become connected."""
            netconf.perform_operation_on_each_device(
                netconf.wait_connected, DEVICE_COUNT, timeout=OPERATION_TIMEOUT
            )

        with allure_step_with_separate_logging("step_issue_requests_on_devices"):
            """Spawn the specified count of worker threads to issue a GET request
            to each of the devices and verify each returns empty configuration
            data."""
            netconf.get_data_from_devices_concurrently(DEVICE_COUNT, WORKER_COUNT)

        with allure_step_with_separate_logging("step_deconfigure_devices"):
            """Make requests to deconfigure the testtool devices. This step is
            expected to fail due to a known bug; if it now passes the suite
            simply continues."""
            with utils.report_known_bug_on_failure("4547"):
                netconf.perform_operation_on_each_device(
                    netconf.deconfigure_device,
                    DEVICE_COUNT,
                    timeout=OPERATION_TIMEOUT,
                )

        with allure_step_with_separate_logging("step_check_devices_are_deconfigured"):
            """Check there are no netconf connectors or other stuff related to the
            testtool devices."""
            netconf.perform_operation_on_each_device(
                netconf.check_device_deconfigured,
                DEVICE_COUNT,
                timeout=OPERATION_TIMEOUT,
            )
