#
# Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License v1.0 which accompanies this distribution,
# and is available at http://www.eclipse.org/legal/epl-v10.html
#
# Based on the original Robot Framework integration test:
# https://github.com/opendaylight/integration-test/blob/master/csit/suites/netconf/restperfclient/performance.robot
#

import logging
import textwrap

import allure
import pytest

from libraries import infra
from libraries import netconf
from libraries import rest_perf_client
from libraries import templated_requests
from libraries.variables import variables
from suites.suite_order import SuiteOrder


USE_NETCONF_CONNECTOR = variables.USE_NETCONF_CONNECTOR
RESTCONF_ROOT = variables.RESTCONF_ROOT

DIRECTORY_WITH_CRUD_TEMPLATES = "variables/netconf/CRUD"
DEVICE_NAME = f"{netconf.FIRST_TESTTOOL_PORT}-sim-device"
DEVICE_TYPE = "default" if USE_NETCONF_CONNECTOR else "full-uri-device"
REQUEST_COUNT = 16384
# Same formula as the original suite's Setup_Everything: REQUEST_COUNT / 10 + 20.
TESTTOOL_DEVICE_TIMEOUT = REQUEST_COUNT / 10 + 20
RESTPERFCLIENT_URL = (
    f"/{RESTCONF_ROOT}/data/network-topology:network-topology"
    f"/topology=topology-netconf/node={DEVICE_NAME}"
    f"/yang-ext:mount/car:cars"
)


log = logging.getLogger(__name__)


@pytest.mark.testtool
@pytest.mark.restperfclient
@pytest.mark.performance
@pytest.mark.single_device
@pytest.mark.usefixtures("preconditions")
@pytest.mark.usefixtures("log_test_suite_start_end_to_karaf")
@pytest.mark.usefixtures("log_test_case_start_end_to_karaf")
@pytest.mark.run(order=SuiteOrder.RESTPERFCLIENT_PERFORMANCE)
class TestPerformance:

    @pytest.fixture
    def netconf_testtool(self, allure_step_with_separate_logging):
        """Start and manage the underlying Netconf testtool simulator process.

        Starts a single simulated device using the CRUD schemas, yields the
        running process and guarantees the process is terminated afterwards.

        Yields:
            subprocess.Popen: The running testtool process handler.
        """
        with allure_step_with_separate_logging("step_start_testtool"):
            # Deploy and start test tool, then wait for its device to become online.
            testtool_process = netconf.start_testtool(
                "build_tools/netconf-testtool.jar",
                device_count=1,
                schemas=f"{DIRECTORY_WITH_CRUD_TEMPLATES}/schemas",
                debug=False,
                mdsal=True,
            )
        yield testtool_process
        with allure_step_with_separate_logging("step_stop_testtool"):
            # Stop netconf testtool.
            netconf.stop_testtool(testtool_process)

    @pytest.fixture
    def connected_netconf_testtools(
        self, netconf_testtool, allure_step_with_separate_logging
    ):
        """Mount the testtool simulator into ODL and manage the connection lifecycle.

        Requiring the `netconf_testtool` fixture ensures the simulator process is
        running first. This fixture then configures the device on the Netconf
        connector, waits until it becomes connected, and deconfigures it on teardown.

        Args:
            netconf_testtool: Fixture that starts the netconf testtool.
            allure_step_with_separate_logging: Fixture used to log distinct steps into
                the Allure report.

        Yields:
            None
        """
        with allure_step_with_separate_logging("step_configure_device_on_netconf"):
            # Configure the testtool device on Netconf connector.
            netconf.configure_device_in_netconf(
                DEVICE_NAME,
                device_type=DEVICE_TYPE,
                http_timeout=2,
            )

        with allure_step_with_separate_logging(
            "step_wait_for_device_to_become_connected"
        ):
            # Wait until the device becomes available through Netconf.
            netconf.wait_device_connected(DEVICE_NAME)

        yield

        with allure_step_with_separate_logging("step_deconfigure_device_from_netconf"):
            # Deconfigure the testtool device on Netconf connector.
            netconf.remove_device_from_netconf(DEVICE_NAME)

    @allure.description(
        textwrap.dedent(
            """
            **netconf-restperfclient Update performance test suite.**

            Perform given count of update operations on device data mounted onto a \
            netconf connector (using the netconf-testtool-restperfclient tool) and \
            see how much time it took. More exactly, it sends the data to a restconf \
            mountpoint of the netconf connector belonging to the device, which turns \
            out to turn the first request sent to a "create" request and the \
            remaining requests to "update" requests (due to how the testtool device \
            behavior is implemented).
            """
        )
    )
    def test_performance(
        self, connected_netconf_testtools, allure_step_with_separate_logging
    ):

        with allure_step_with_separate_logging("step_create_device_data"):
            # Send some sample test data into the device and check that
            # the request went OK.
            mapping = {"DEVICE_NAME": DEVICE_NAME, "RESTCONF_ROOT": RESTCONF_ROOT}
            templated_requests.post_templated_request(
                f"{DIRECTORY_WITH_CRUD_TEMPLATES}/cars", mapping, json=False
            )

        with allure_step_with_separate_logging("step_run_restperfclient"):
            # Deploy and execute restperfclient, asking it to send the specified
            # amount of requests to the netconf connector of the device.
            perf_log = rest_perf_client.invoke_restperfclient(
                edits=REQUEST_COUNT,
                url=RESTPERFCLIENT_URL,
                timeout=TESTTOOL_DEVICE_TIMEOUT,
                testcase="performance",
                asynchronous=False,
            )

        with allure_step_with_separate_logging("step_collect_results"):
            # Collect logs generated by the restperf client. This is done before
            # the failed-request check so the log is preserved even when that check
            # fails, mirroring the original suite where Cleanup_And_Collect ran as an
            # independent, always-executed test case.
            infra.shell(f"cp '{perf_log}' results/")

        with allure_step_with_separate_logging("step_check_for_failed_requests"):
            # Make sure there are no failed requests in the restperfclient log.
            # This is a separate step to distinguish between restperfclient failure
            # and failed requests. Failed requests are rejected because we don't want
            # to test performance of ODL rejecting our requests.
            assert (
                rest_perf_client.grep_restperfclient_log(perf_log, "thread timed out")
                == ""
            )
            assert (
                rest_perf_client.grep_restperfclient_log(perf_log, "Request failed")
                == ""
            )
            assert (
                rest_perf_client.grep_restperfclient_log(perf_log, "Status code") == ""
            )
