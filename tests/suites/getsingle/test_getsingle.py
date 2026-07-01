#
# Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License v1.0 which accompanies this distribution,
# and is available at http://www.eclipse.org/legal/epl-v10.html
#
# Based on the original Robot Framework integration test:
# https://github.com/opendaylight/integration-test/blob/master/csit/suites/netconf/scale/getsingle.robot
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

# NOTE: Matches the getmulti suite (NETCONF-1642) and the upstream Robot suite,
# which both use 500 devices.
DEVICE_COUNT = 500
TIMEOUT_FACTOR = 10
OPERATION_TIMEOUT = DEVICE_COUNT * TIMEOUT_FACTOR
DEVICE_TYPE = "default" if USE_NETCONF_CONNECTOR else "full-uri-device"


log = logging.getLogger(__name__)


@pytest.mark.testtool
@pytest.mark.performance
@pytest.mark.multi_device
@pytest.mark.usefixtures("preconditions")
@pytest.mark.usefixtures("log_test_suite_start_end_to_karaf")
@pytest.mark.usefixtures("log_test_case_start_end_to_karaf")
@pytest.mark.run(order=SuiteOrder.GETSINGLE)
class TestGetsingle:

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
            **netconf-connector scaling test suite (single-threaded GET requests).**

            Performs scaling tests:
            - Configuring devices one by one.
            - Sending requests for configuration data.
            - Deconfiguring devices one by one.
            """
        )
    )
    def test_getsingle(self, netconf_testtool, allure_step_with_separate_logging):

        with allure_step_with_separate_logging("step_configure_devices_onto_netconf"):
            """Make requests to configure the testtool devices, waiting for each
            device to connect before moving on to the next one."""
            netconf.perform_operation_on_each_device(
                functools.partial(
                    netconf.configure_device_and_verify, device_type=DEVICE_TYPE
                ),
                DEVICE_COUNT,
                timeout=OPERATION_TIMEOUT,
            )

        with allure_step_with_separate_logging("step_get_data_from_devices"):
            """Ask testtool devices for data, one at a time, and verify each
            device returns empty configuration data."""
            netconf.perform_operation_on_each_device(
                netconf.check_device_data_is_empty,
                DEVICE_COUNT,
                timeout=OPERATION_TIMEOUT,
            )

        with allure_step_with_separate_logging("step_deconfigure_devices_from_netconf"):
            """Make requests to deconfigure the testtool devices, waiting for each
            device to disappear. This step is expected to pass; if it fails, a
            link to the known bug is logged."""
            with utils.report_known_bug_on_failure("4547"):
                netconf.perform_operation_on_each_device(
                    netconf.deconfigure_device_and_verify,
                    DEVICE_COUNT,
                    timeout=OPERATION_TIMEOUT,
                )
