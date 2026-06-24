#
# Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License v1.0 which accompanies this distribution,
# and is available at http://www.eclipse.org/legal/epl-v10.html
#
# Based on the original Robot Framework integration test:
# https://github.com/opendaylight/integration-test/blob/901c7e139945b436d95a44b3b592904c3d7a4f9f/csit/suites/netconf/restperfclient/mdsal.robot
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


ODL_IP = variables.ODL_IP
ODL_NETCONF_MDSAL_PORT = variables.ODL_NETCONF_MDSAL_PORT
ODL_NETCONF_USER = variables.ODL_NETCONF_USER
ODL_NETCONF_PASSWORD = variables.ODL_NETCONF_PASSWORD
USE_NETCONF_CONNECTOR = variables.USE_NETCONF_CONNECTOR

DIRECTORY_WITH_TEMPLATE_FOLDERS = "variables/netconf/RestPerfClient"
REQUEST_COUNT = 16384
DIRECT_MDSAL_TIMEOUT = REQUEST_COUNT / 50 + 10
NETCONF_CONNECTOR_MDSAL_TIMEOUT = REQUEST_COUNT / 10 + 20
DEVICE_TYPE = "default" if USE_NETCONF_CONNECTOR else "full-uri-device"
TEST_DEVICE = "odl-mdsal-northbound-via-netconf-connector"


log = logging.getLogger(__name__)


@pytest.mark.mdsal
@pytest.mark.testtool
@pytest.mark.restperfclient
@pytest.mark.performance
@pytest.mark.single_device
@pytest.mark.usefixtures("preconditions")
@pytest.mark.usefixtures("log_test_suite_start_end_to_karaf")
@pytest.mark.usefixtures("log_test_case_start_end_to_karaf")
@pytest.mark.run(order=SuiteOrder.RESTPERFCLIENT_MDSAL)
class TestMdsal:

    @pytest.fixture
    def cars_test_data(self, allure_step_with_separate_logging):
        with allure_step_with_separate_logging("step_create_test_data"):
            # Send some sample test data into the device and check that
            # the request went OK.
            templated_requests.post_templated_request(
                f"{DIRECTORY_WITH_TEMPLATE_FOLDERS}/cars", {}, json=False
            )
        yield
        with allure_step_with_separate_logging("step_cleanup_test_data"):
            # Cleanup the test data produced by the restperf client.
            templated_requests.delete_templated_request(
                f"{DIRECTORY_WITH_TEMPLATE_FOLDERS}/cars-delete", None
            )

    @pytest.fixture
    def odl_netconf_connector(self, allure_step_with_separate_logging):
        with allure_step_with_separate_logging(
            "step_configure_odl_as_device_on_netconf"
        ):
            infra.shell("rm -rf opendaylight/cache/schema/*")
            netconf.configure_device_in_netconf(
                TEST_DEVICE,
                device_type=DEVICE_TYPE,
                device_address=ODL_IP,
                device_port=ODL_NETCONF_MDSAL_PORT,
                device_user=ODL_NETCONF_USER,
                device_password=ODL_NETCONF_PASSWORD,
            )
            netconf.wait_device_connected(TEST_DEVICE)
        yield
        with allure_step_with_separate_logging("step_deconfigure_odl_from_netconf"):
            # Deconfigure the ODL MDSAL Northbound attached to a Netconf connector.
            netconf.remove_device_from_netconf(TEST_DEVICE)

    @allure.description(
        textwrap.dedent(
            """
            **Netconf-restperfclient MDSAL direct access test case.**

            Perform given count of update operations on ODL MDSAL. The requests \
            are directed directly to MDSAL via Restconf. \
            Netconf-testtool-restperfclient tool is used to generate and send \
            the requests. The restperfclient is used to generate the "update" \
            requests, the "create" request is issued in a separate test case.
            """
        )
    )
    def test_mdsal_direct_access(
        self, cars_test_data, allure_step_with_separate_logging
    ):

        with allure_step_with_separate_logging(
            "step_run_restperfclient_directly_on_mdsal"
        ):
            # Deploy and execute restperfclient, asking it to send the specified
            # amount of requests to the MDSAL via Restconf.
            direct_log = rest_perf_client.invoke_restperfclient(
                edits=REQUEST_COUNT,
                url="/restconf/data/car:cars",
                timeout=DIRECT_MDSAL_TIMEOUT,
                testcase="direct",
            )

        with allure_step_with_separate_logging(
            "step_check_for_failed_direct_mdsal_requests"
        ):
            # Make sure there are no failed requests in the restperfclient log.
            # This is a separate test case to distinguish between restperfclient
            # failure and failed requests. Failed requests are rejected because
            # we don't want to test performance of ODL rejecting our requests.
            assert (
                rest_perf_client.grep_restperfclient_log(direct_log, "thread timed out")
                == ""
            )
            assert (
                rest_perf_client.grep_restperfclient_log(direct_log, "Request failed")
                == ""
            )
            assert (
                rest_perf_client.grep_restperfclient_log(direct_log, "Status code")
                == ""
            )

        with allure_step_with_separate_logging("step_collect_direct_access_logs"):
            # Collect logs generated by the restperf client.
            infra.shell(f"cp '{direct_log}' results/")

    @allure.description(
        textwrap.dedent(
            """
            **Netconf-restperfclient MDSAL netconf connector test case.**

            Perform given count of update operations on ODL MDSAL. \
            MDSAL is mounted onto a netconf connector and the requests are \
            directed to that connector. Netconf-testtool-restperfclient \
            tool is used to generate and send the requests and the requests are sent \
            synchronously as the netconf connector mounted MDSAL does not support \
            asynchronous requests. The restperfclient is used to generate the "update" \
            requests, the "create" request is issued in a separate test case.
            """
        )
    )
    def test_mdsal_netconf_connector(
        self, cars_test_data, odl_netconf_connector, allure_step_with_separate_logging
    ):
        with allure_step_with_separate_logging(
            "step_run_restperfclient_through_netconf_connector"
        ):
            # Ask RestPerfClient to send the requests to the MDSAL mapped via netconf
            # topology device.
            connector_log = rest_perf_client.invoke_restperfclient(
                edits=REQUEST_COUNT,
                url=f"/restconf/data/network-topology:network-topology"
                f"/topology=topology-netconf/node={TEST_DEVICE}"
                f"/yang-ext:mount/car:cars",
                timeout=NETCONF_CONNECTOR_MDSAL_TIMEOUT,
                testcase="netconf-connector",
            )

        with allure_step_with_separate_logging(
            "step_check_for_failed_netconf_connector_requests"
        ):
            # Make sure there are no failed requests in the restperfclient log.
            # This is a separate test case to distinguish between restperfclient
            # failure and failed requests. Failed requests are rejected because
            # we don't want to test performance of ODL rejecting our requests.
            with utils.report_known_bug_on_failure("5581"):
                assert (
                    rest_perf_client.grep_restperfclient_log(
                        connector_log, "thread timed out"
                    )
                    == ""
                )
                assert (
                    rest_perf_client.grep_restperfclient_log(
                        connector_log, "Request failed"
                    )
                    == ""
                )
                assert (
                    rest_perf_client.grep_restperfclient_log(
                        connector_log, "Status code"
                    )
                    == ""
                )

        with allure_step_with_separate_logging("step_collect_connector_access_logs"):
            # Collect logs generated by the restperf client.
            infra.shell(f"cp '{connector_log}' results/")
