#
# Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License v1.0 which accompanies this distribution,
# and is available at http://www.eclipse.org/legal/epl-v10.html
#
# Based on the original Robot Framework integration test:
# https://github.com/opendaylight/integration-test/blob/901c7e139945b436d95a44b3b592904c3d7a4f9f/csit/suites/netconf/clusteringscale/performance.robot
#

import logging
import textwrap

import allure
import pytest

from libraries import infra
from libraries import netconf
from libraries import rest_perf_client
from libraries import templated_requests
from libraries import utils
from libraries.variables import variables
from suites.suite_order import SuiteOrder


RESTCONF_ROOT = variables.RESTCONF_ROOT
ODL_NETCONF_NAMESPACE = variables.ODL_NETCONF_NAMESPACE

CONFIGURER_IP = variables.CLUSTER_MEMBER_IPS[0]
SETTER_IP = variables.CLUSTER_MEMBER_IPS[1]
PERFCLIENT_IP = variables.CLUSTER_MEMBER_IPS[2]

DIRECTORY_WITH_CRUD_TEMPLATES = "variables/netconf/CRUD"
DEVICE_NAME = f"{netconf.FIRST_TESTTOOL_PORT}-sim-device"
DEVICE_TYPE = "configure-via-topology"
REQUEST_COUNT = 16384
TESTTOOL_DEVICE_TIMEOUT = REQUEST_COUNT / 10 + 20
DEVICE_DATA_CONNECT_TIMEOUT = 60
RESTPERFCLIENT_URL = (
    f"/{RESTCONF_ROOT}/data/network-topology:network-topology"
    f"/topology=topology-netconf/node={DEVICE_NAME}"
    f"/yang-ext:mount/car:cars"
)
EMPTY_DATA = f'<data xmlns="{ODL_NETCONF_NAMESPACE}"></data>'


log = logging.getLogger(__name__)


@pytest.mark.cluster
@pytest.mark.testtool
@pytest.mark.restperfclient
@pytest.mark.performance
@pytest.mark.usefixtures("odl_three_node_cluster")
@pytest.mark.run(order=SuiteOrder.CLUSTERING_SCALE_PERFORMANCE)
class TestPerformance:

    def check_empty_data_present(self, host: str):
        """Fetch the device's config data from a single node. This data should be empty.

        Args:
            host (str): Cluster member to query.

        Returns:
            None
        """
        netconf.check_device_data_is_empty(DEVICE_NAME)

    @pytest.fixture
    def netconf_testtool(self, allure_step_with_separate_logging):
        """Start and manage the underlying Netconf testtool simulator process.

        This fixture handles the lifecycle of the simulator process. It
        starts the netconf testtool with the required schemas and RPC
        configurations, yields the running process and guarantees the process
        is terminated after execution.

        Yields:
            subprocess.Popen: The running testtool process handler.
        """
        with allure_step_with_separate_logging("step_start_testtool"):
            # Start test tool, then wait for all its devices to become online.
            testtool_process = netconf.start_testtool(
                device_count=1,
                schemas=f"{DIRECTORY_WITH_CRUD_TEMPLATES}/schemas",
                debug=False,
                base_startup_timeout=60,
            )
        yield testtool_process
        with allure_step_with_separate_logging("step_stop_testtool"):
            # Stop netconf testtool.
            netconf.stop_testtool(testtool_process)

    @pytest.fixture
    def connected_netconf_testtools(
        self, netconf_testtool, allure_step_with_separate_logging
    ):
        """Mount the device via the configurer node and manage its lifecycle.

        Configures the device in the Netconf topology using CONFIGURER_IP
        (node 1), waits until it becomes visible on that node, then waits
        until its data becomes visible on SETTER_IP (node 2). This forces the
        cluster nodes to communicate with each other about the device before
        any data operations run against it. By requiring the `netconf_testtool`
        fixture, this setup ensures the simulator process is running first.

        Args:
            netconf_testtool: Fixture that starts the netconf testtool.
            allure_step_with_separate_logging: Fixture used to log distinct steps
                into the Allure report.

        Yields:
            None
        """
        with allure_step_with_separate_logging("step_configure_device_on_netconf"):
            # Configure the testtool device on Netconf connector, using node 1.
            netconf.configure_device_in_netconf(
                DEVICE_NAME, device_type=DEVICE_TYPE, host=CONFIGURER_IP
            )

        with allure_step_with_separate_logging(
            "step_wait_for_device_to_become_connected"
        ):
            # Wait until the device becomes available through Netconf on node 1.
            netconf.wait_device_connected(DEVICE_NAME, host=CONFIGURER_IP)

        with allure_step_with_separate_logging("step_wait_for_device_data_to_be_seen"):
            # Wait until the device data show up at node 2.
            utils.wait_until_function_pass(
                DEVICE_DATA_CONNECT_TIMEOUT, 1, self.check_empty_data_present, SETTER_IP
            )

        yield

        with allure_step_with_separate_logging("step_deconfigure_device_from_netconf"):
            # Deconfigure the testtool device on Netconf connector using node 1.
            netconf.remove_device_from_netconf(DEVICE_NAME, host=CONFIGURER_IP)

    @allure.description(
        textwrap.dedent(
            """
            **netconf-restperfclient Update performance test suite (clustered setup).**

            Perform given count of update operations on device data mounted onto a \
            netconf connector (using the netconf-testtool-restperfclient tool) and \
            see how much time it took. More exactly, it sends the data to a restconf \
            mountpoint of the netconf connector belonging to the device, which turns \
            out to turn the first request sent to a "create" request and the \
            remaining requests to "update" requests (due to how the testtool device \
            behavior is implemented).

            The difference from the single-node suite (see \
            restperfclient/test_performance.py) is that the device is configured and \
            the data on it created using one node in the cluster and the update \
            operations are issued on a different node. This forces the cluster nodes \
            to communicate with each other about the data to be sent to the device.
            """
        )
    )
    def test_performance(
        self, connected_netconf_testtools, allure_step_with_separate_logging
    ):

        with allure_step_with_separate_logging("step_create_device_data"):
            # Send some sample test data into the device through node 2 and check
            # that the request went OK.
            mapping = {"DEVICE_NAME": DEVICE_NAME, "RESTCONF_ROOT": RESTCONF_ROOT}
            templated_requests.post_templated_request(
                f"{DIRECTORY_WITH_CRUD_TEMPLATES}/cars",
                mapping,
                json=False,
                host=SETTER_IP,
            )

        with allure_step_with_separate_logging("step_run_restperfclient"):
            # Deploy and execute restperfclient, asking it to send the specified
            # amount of requests to the netconf connector of the device through
            # node 3. The duration of this step is the main performance metric.
            perf_log = rest_perf_client.invoke_restperfclient(
                edits=REQUEST_COUNT,
                url=RESTPERFCLIENT_URL,
                timeout=TESTTOOL_DEVICE_TIMEOUT,
                testcase="performance",
                ip=PERFCLIENT_IP,
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
            # to test performance of ODL rejecting our requests. If this step fails,
            # then the duration of step_run_restperfclient cannot be trusted to show
            # the real performance of the cluster.

            # Check for 'thread timed out'
            timed_out_logs = rest_perf_client.grep_restperfclient_log(
                perf_log, "thread timed out"
            )
            assert not timed_out_logs, (
                f"restperfclient log contains thread timeout errors ({perf_log}):\n"
                f"{timed_out_logs}"
            )

            # Check for 'Request failed'
            failed_request_logs = rest_perf_client.grep_restperfclient_log(
                perf_log, "Request failed"
            )
            assert not failed_request_logs, (
                f"restperfclient log contains failed requests ({perf_log}):\n"
                f"{failed_request_logs}"
            )

            # Check for 'Status code'
            status_code_logs = rest_perf_client.grep_restperfclient_log(
                perf_log, "Status code"
            )
            assert not status_code_logs, (
                "restperfclient log contains unexpected status code entries "
                f"({perf_log}):\n{status_code_logs}"
            )
