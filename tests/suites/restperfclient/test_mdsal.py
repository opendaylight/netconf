#
# Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License v1.0 which accompanies this distribution,
# and is available at http://www.eclipse.org/legal/epl-v10.html
#
# Based on the original Robot Framework integration test:
# https://github.com/opendaylight/integration-test/blob/master/csit/suites/netconf/restperfclient/mdsal.robot
#

import logging
import math
import textwrap

import allure
import pytest

from libraries import infra
from libraries import netconf
from libraries import templated_requests
from libraries import utils
from libraries.variables import variables


ODL_IP = variables.ODL_IP
ODL_USER = variables.ODL_USER
ODL_PASSWORD = variables.ODL_PASSWORD
RESTCONF_PORT = variables.RESTCONF_PORT
ODL_NETCONF_MDSAL_PORT = variables.ODL_NETCONF_MDSAL_PORT
ODL_NETCONF_USER = variables.ODL_NETCONF_USER
ODL_NETCONF_PASSWORD = variables.ODL_NETCONF_PASSWORD
RESTPERF_FILENAME = variables.RESTPERF_FILENAME
USE_NETCONF_CONNECTOR = variables.USE_NETCONF_CONNECTOR

DIRECTORY_WITH_TEMPLATE_FOLDERS = "variables/netconf/RestPerfClient"
REQUEST_COUNT = 16384
DIRECT_MDSAL_TIMEOUT = REQUEST_COUNT / 50 + 10
NETCONF_CONNECTOR_MDSAL_TIMEOUT = REQUEST_COUNT / 10 + 20
DEVICE_TYPE = "default" if USE_NETCONF_CONNECTOR  else "full-uri-device"
TEST_DEVICE = "odl-mdsal-northbound-via-netconf-connector"


log = logging.getLogger(__name__)


@pytest.mark.usefixtures("preconditions")
@pytest.mark.usefixtures("log_test_suite_start_end_to_karaf")
@pytest.mark.usefixtures("log_test_case_start_end_to_karaf")
@pytest.mark.run(order=7)
class TestMdsal:

    @allure.description(
        textwrap.dedent(
            """
            **netconf-restperfclient MDSAL performance test suite.**

            Perform given count of update operations on ODL MDSAL. In first half the
            requests are directed directly to MDSAL via Restconf and in the second
            half the MDSAL is mounted onto a netconf connector and the requests are
            directed to that connector. In both cases the netconf-testtool-restperfclient
            tool is used to generate and send the requests and the requests are sent
            synchronously as the netconf connector mounted MDSAL does not support
            asynchronous requests. The restperfclient is used to generate the "update"
            requests, the "create" request is issued in a separate test case.
            """
        )
    )
    def test_mdsal(self, allure_step_with_separate_logging):
        with allure_step_with_separate_logging("step_create_test_data_for_direct_access"):
            templated_requests.post_templated_request(
                f"{DIRECTORY_WITH_TEMPLATE_FOLDERS}/cars", {}, json=False
            )

        with allure_step_with_separate_logging("step_run_restperfclient_directly_on_mdsal"):
            direct_log = _invoke_restperfclient(
                DIRECT_MDSAL_TIMEOUT,
                "/rests/data/car:cars",
                testcase="direct",
            )

        with allure_step_with_separate_logging("step_check_for_failed_direct_mdsal_requests"):
            # Separate step so a failed request is distinguishable from a restperfclient crash
            assert _grep_log(direct_log, "thread timed out") == ""
            assert _grep_log(direct_log, "Request failed") == ""
            assert _grep_log(direct_log, "Status code") == ""

        with allure_step_with_separate_logging("step_cleanup_and_collect_for_direct_access"):
            infra.shell(f"cp {direct_log} results/")
            templated_requests.delete_templated_request(
                f"{DIRECTORY_WITH_TEMPLATE_FOLDERS}/cars-delete", {}
            )

        with allure_step_with_separate_logging("step_create_test_data_for_connector_access"):
            templated_requests.post_templated_request(
                f"{DIRECTORY_WITH_TEMPLATE_FOLDERS}/cars", {}, json=False
            )

        with allure_step_with_separate_logging("step_configure_odl_as_device_on_netconf"):
            netconf.configure_device_in_netconf(
                TEST_DEVICE,
                device_type=DEVICE_TYPE,
                device_address=ODL_IP,
                device_port=ODL_NETCONF_MDSAL_PORT,
                device_user=ODL_NETCONF_USER,
                device_password=ODL_NETCONF_PASSWORD,
            )
            netconf.wait_device_connected(TEST_DEVICE)

        with allure_step_with_separate_logging("step_run_restperfclient_through_netconf_connector"):
            connector_log = _invoke_restperfclient(
                NETCONF_CONNECTOR_MDSAL_TIMEOUT,
                f"/rests/data/network-topology:network-topology"
                f"/topology=topology-netconf/node={TEST_DEVICE}"
                f"/yang-ext:mount/car:cars",
                testcase="netconf-connector",
            )

        with allure_step_with_separate_logging("step_check_for_failed_netconf_connector_requests"):
            with utils.report_known_bug_on_failure("5581"):
                assert _grep_log(connector_log, "thread timed out") == ""
            assert _grep_log(connector_log, "Request failed") == ""
            assert _grep_log(connector_log, "Status code") == ""

        with allure_step_with_separate_logging("step_deconfigure_odl_from_netconf"):
            netconf.remove_device_from_netconf(TEST_DEVICE)

        with allure_step_with_separate_logging("step_cleanup_and_collect_for_connector_access"):
            infra.shell(f"cp {connector_log} results/")
            templated_requests.delete_templated_request(
                f"{DIRECTORY_WITH_TEMPLATE_FOLDERS}/cars-delete", {}
            )


def _invoke_restperfclient(timeout: int, url: str, testcase: str = "") -> str:
    """Run restperfclient, write its output to a log file, and return the log path.

    Mirrors RF's Invoke_Restperfclient keyword. Bug 5413 (restperfclient
    hanging) is known and handled by the caller via report_known_bug_on_failure.
    """
    log_file = f"restperfclient-{testcase}.log" if testcase else "restperfclient.log"
    timeout_minutes = math.ceil(timeout / 60)
    command = (
        f"java -Xmx4G -jar {RESTPERF_FILENAME}"
        f" --ip {ODL_IP}"
        f" --port {RESTCONF_PORT}"
        f" --edits {REQUEST_COUNT}"
        f" --edit-content {DIRECTORY_WITH_TEMPLATE_FOLDERS}/request1.json"
        f" --async-requests false"
        f" --auth {ODL_USER} {ODL_PASSWORD}"
        f" --timeout {timeout_minutes}"
        f" --destination {url}"
    )
    log.info(f"Running restperfclient: {command}")
    # Add 2 minutes headroom over the restperfclient's own timeout
    rc, output = infra.shell(command, timeout=timeout + 120)
    log.info(f"restperfclient output: {output}")
    with open(log_file, "w") as f:
        f.write(output)
    assert "FINISHED. Execution time:" in output, (
        f"restperfclient did not finish cleanly; check {log_file}"
    )
    return log_file


def _grep_log(log_file: str, pattern: str) -> str:
    _, result = infra.shell(f"grep '{pattern}' {log_file} || true")
    return result.strip()
