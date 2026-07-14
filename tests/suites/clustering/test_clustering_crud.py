#
# Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License v1.0 which accompanies this distribution,
# and is available at http://www.eclipse.org/legal/epl-v10.html
#
# Based on the original Robot Framework integration test:
# https://github.com/opendaylight/integration-test/blob/901c7e139945b436d95a44b3b592904c3d7a4f9f/csit/suites/netconf/clustering/CRUD.robot
#

import logging
import textwrap

import allure
import pytest

from libraries import netconf
from libraries import templated_requests
from libraries import utils
from libraries.variables import variables
from suites.suite_order import SuiteOrder

DEVICE_CHECK_TIMEOUT = 20
DEVICE_NAME = "netconf-test-device"
DEVICE_TYPE = "configure-via-topology"
DIRECTORY_WITH_TEMPLATE_FOLDERS = "variables/netconf/CRUD"

CONFIGURER_IP = variables.CLUSTER_MEMBER_IPS[0]
SETTER_IP = variables.CLUSTER_MEMBER_IPS[1]
CHECKER_IP = variables.CLUSTER_MEMBER_IPS[2]

RESTCONF_ROOT = variables.RESTCONF_ROOT
ODL_NETCONF_NAMESPACE = variables.ODL_NETCONF_NAMESPACE

EMPTY_DATA = f'<data xmlns="{ODL_NETCONF_NAMESPACE}"></data>'
ORIGINAL_DATA = (
    f'<data xmlns="{ODL_NETCONF_NAMESPACE}">'
    f'<cont xmlns="urn:opendaylight:test:netconf:crud">'
    f"<l>Content</l></cont></data>"
)
MODIFIED_DATA = (
    f'<data xmlns="{ODL_NETCONF_NAMESPACE}">'
    f'<cont xmlns="urn:opendaylight:test:netconf:crud">'
    f"<l>Modified Content</l></cont></data>"
)

log = logging.getLogger(__name__)


@pytest.mark.cluster
@pytest.mark.crud
@pytest.mark.testtool
@pytest.mark.functional
@pytest.mark.usefixtures("odl_three_node_cluster")
@pytest.mark.run(order=SuiteOrder.CLUSTERING_CRUD)
class TestClusteringCrud:

    def get_config_data(self, host: str) -> str:
        """Get and return the config data from the device, as seen by one node.

        Args:
            host (str): Cluster member to query.

        Returns:
            str: The raw XML text representation of the device's configuration data.
        """
        url = (
            f"{RESTCONF_ROOT}/data/network-topology:network-topology/topology="
            f"topology-netconf/node={DEVICE_NAME}/yang-ext:mount?content=config"
        )
        headers = {"Accept": "application/yang-data+xml"}
        return templated_requests.get_from_uri(url, headers=headers, host=host).text

    def check_config_data(self, host: str, expected: str):
        """Validates the mounted device's configuration data as seen by one node.

        Args:
            host (str): Cluster member to query.
            expected (str): The expected string to validate against.

        Returns:
            None
        """
        data = self.get_config_data(host)
        assert expected == data

    def verify_single_netconf_connector(self, host: str):
        """Verifies that exactly one Netconf connector exists for the test device.

        Args:
            host (str): Cluster member to query.

        Returns:
            None
        """
        count = netconf.count_netconf_connectors_for_device(DEVICE_NAME, host=host)
        assert count == 1

    @pytest.fixture()
    def netconf_testtool(self, allure_step_with_separate_logging):
        """Starts and manages the underlying Netconf testtool simulator process.

        Args:
            allure_step_with_separate_logging: Fixture used to log distinct steps
                into the Allure report.

        This fixture handles the lifecycle of the simulator process. It
        starts the netconf testtool with the required schemas and RPC
        configurations, yields the running process and guarantees the process
        is terminated after execution.

        Yields:
            subprocess.Popen: The running testtool process handler.
        """
        with allure_step_with_separate_logging("step_start_netconf_testtool"):
            # Start test tool, then wait for all its devices to become online.
            testtool_process = netconf.start_testtool(
                "build_tools/netconf-testtool.jar",
                device_count=1,
                schemas="variables/netconf/CRUD/schemas",
            )
        yield testtool_process
        with allure_step_with_separate_logging("step_stop_netconf_testtool"):
            # Stop testtool and store its log.
            netconf.stop_testtool(testtool_process)

    @pytest.fixture
    def configured_device(self, netconf_testtool, allure_step_with_separate_logging):
        """Mounts the device via the configurer node and manages its lifecycle.

        Configures the device through CONFIGURER_IP and waits until it becomes
        visible on all 3 nodes, mirroring how a real client would only ever
        talk to one cluster member while data propagates to the others.

        Args:
            netconf_testtool: Fixture that starts the netconf testtool.
            allure_step_with_separate_logging: Fixture used to log distinct steps
                into the Allure report.

        Yields:
            None: This fixture manages ODL connection state; it does not return
            an object to the test.
        """
        with allure_step_with_separate_logging(
            "step_check_device_is_not_mounted_at_beginning"
        ):
            # Sanity check making sure our device is not there. Fail if found.
            netconf.check_device_has_no_netconf_connector(
                DEVICE_NAME, host=CONFIGURER_IP
            )

        with allure_step_with_separate_logging("step_configure_device_via_configurer"):
            # Make request to configure a testtool device via configurer node.
            with utils.report_known_bug_on_failure("5089"):
                netconf.configure_device_in_netconf(
                    DEVICE_NAME, device_type=DEVICE_TYPE, host=CONFIGURER_IP
                )

        with allure_step_with_separate_logging(
            "step_check_configurer_has_netconf_connector_for_device"
        ):
            # Get the list of mounts and search for our device there. Fail if not found.
            utils.wait_until_function_pass(
                10, 1, self.verify_single_netconf_connector, CONFIGURER_IP
            )

        with allure_step_with_separate_logging(
            "step_wait_for_device_to_become_visible_for_configurer"
        ):
            # Wait until the device becomes visible on configurer node.
            netconf.wait_device_connected(
                DEVICE_NAME, host=CONFIGURER_IP, timeout=DEVICE_CHECK_TIMEOUT
            )

        with allure_step_with_separate_logging(
            "step_wait_for_device_to_become_visible_for_checker"
        ):
            # Wait until the device becomes visible on checker node.
            netconf.wait_device_connected(
                DEVICE_NAME, host=CHECKER_IP, timeout=DEVICE_CHECK_TIMEOUT
            )

        with allure_step_with_separate_logging(
            "step_wait_for_device_to_become_visible_for_setter"
        ):
            # Wait until the device becomes visible on setter node.
            netconf.wait_device_connected(
                DEVICE_NAME, host=SETTER_IP, timeout=DEVICE_CHECK_TIMEOUT
            )

        yield

        with allure_step_with_separate_logging(
            "step_deconfigure_device_via_configurer"
        ):
            # Make request to deconfigure the device on Netconf connector.
            netconf.remove_device_from_netconf(DEVICE_NAME, host=CONFIGURER_IP)

        with allure_step_with_separate_logging(
            "step_check_device_deconfigured_on_configurer"
        ):
            # Check that the device is really going to be gone. Fail if still there
            # after default timeout.This is an expected behavior as the unmount
            # request is sent to the config subsystem which then triggers asynchronous
            # disconnection of the device which is reflected in the operational data
            # once completed. This test makes sure this asynchronous operation
            # does not take unreasonable amount of time.
            netconf.wait_device_fully_removed(DEVICE_NAME, host=CONFIGURER_IP)

        with allure_step_with_separate_logging(
            "step_check_device_deconfigured_on_checker"
        ):
            # Check that the device is going to be gone from the checker node. Fail if
            # still there after default timeout.
            netconf.wait_device_fully_removed(DEVICE_NAME, host=CHECKER_IP)

        with allure_step_with_separate_logging(
            "step_check_device_deconfigured_on_setter"
        ):
            # Check that the device is going to be gone from the setter node. Fail if
            # still there after default timeout.
            netconf.wait_device_fully_removed(DEVICE_NAME, host=SETTER_IP)

    @allure.description(
        textwrap.dedent(
            """
            **Test suite to perform CRUD operations across an ODL cluster.**

            Perform basic operations (Create, Read, Update and Delete or CRUD) on \
            device data mounted onto a netconf connector and see if they work.

            The suite recognizes 3 nodes, "CONFIGURER" (the node that configures the \
            device at the beginning and then deconfigures it at the end), "SETTER" \
            (the node that manipulates the data on the device) and "CHECKER" (the node \
            that checks the data on the device). The configured device and the results \
            of each data operation on it is expected to be visible on all nodes so \
            after each operation three test cases make sure they can see the result on \
            their respective nodes.

            The suite checks the integrity of the presence of the device and the data \
            seen on the device only for nodes that have at least one of the roles \
            ("CONFIGURER", "SETTER" and "CHECKER") assigned. A better design would \
            have a "checker list" of sorts and have only one checking test case that \
            runs through the check list and performs the test on each node listed. \
            However this currently has fairly low priority due to Beryllium delivery \
            date so it was left out.
            """
        )
    )
    def test_crud_clustering(
        self, configured_device, allure_step_with_separate_logging
    ):
        mapping = {"DEVICE_NAME": DEVICE_NAME, "RESTCONF_ROOT": RESTCONF_ROOT}

        with allure_step_with_separate_logging(
            "step_check_device_data_is_seen_as_empty_on_configurer"
        ):
            # Get the device data as seen by configurer and make sure it is empty.
            utils.wait_until_function_pass(
                DEVICE_CHECK_TIMEOUT,
                1,
                self.check_config_data,
                CONFIGURER_IP,
                EMPTY_DATA,
            )

        with allure_step_with_separate_logging(
            "step_check_device_data_is_seen_as_empty_on_checker"
        ):
            # Get the device data as seen by checker and make sure it is empty.
            utils.wait_until_function_pass(
                DEVICE_CHECK_TIMEOUT, 1, self.check_config_data, CHECKER_IP, EMPTY_DATA
            )

        with allure_step_with_separate_logging(
            "step_check_device_data_is_seen_as_empty_on_setter"
        ):
            # Get the device data as seen by setter and make sure it is empty.
            utils.wait_until_function_pass(
                DEVICE_CHECK_TIMEOUT, 1, self.check_config_data, SETTER_IP, EMPTY_DATA
            )

        with allure_step_with_separate_logging("step_create_device_data_via_setter"):
            # Send some sample test data into the device and check that the request
            # went OK.
            templated_requests.post_templated_request(
                f"{DIRECTORY_WITH_TEMPLATE_FOLDERS}/dataorig",
                mapping,
                json=False,
                host=SETTER_IP,
            )

        with allure_step_with_separate_logging(
            "step_check_new_device_data_is_visible_on_setter"
        ):
            # Get the device data and make sure it contains the created content.
            utils.wait_until_function_pass(
                DEVICE_CHECK_TIMEOUT,
                1,
                self.check_config_data,
                SETTER_IP,
                ORIGINAL_DATA,
            )

        with allure_step_with_separate_logging(
            "step_check_new_device_data_is_visible_on_checker"
        ):
            # Check that the created device data make their way into the checker node.
            utils.wait_until_function_pass(
                DEVICE_CHECK_TIMEOUT,
                1,
                self.check_config_data,
                CHECKER_IP,
                ORIGINAL_DATA,
            )

        with allure_step_with_separate_logging(
            "step_check_new_device_data_is_visible_on_configurer"
        ):
            # Check that the created device data make their way into the configurer
            # node.
            utils.wait_until_function_pass(
                DEVICE_CHECK_TIMEOUT,
                1,
                self.check_config_data,
                CONFIGURER_IP,
                ORIGINAL_DATA,
            )

        with allure_step_with_separate_logging("step_modify_device_data_via_setter"):
            # Send a request to change the sample test data and check that the request
            # went OK.
            with utils.report_known_bug_on_failure("4968"):
                templated_requests.put_templated_request(
                    f"{DIRECTORY_WITH_TEMPLATE_FOLDERS}/datamod1",
                    mapping,
                    json=False,
                    host=SETTER_IP,
                )

        with allure_step_with_separate_logging("step_check_device_data_is_modified"):
            # Get the device data and make sure it contains the modified content.
            with utils.report_known_bug_on_failure("4968"):
                self.check_config_data(SETTER_IP, MODIFIED_DATA)

        with allure_step_with_separate_logging(
            "step_check_modified_device_data_is_visible_on_checker"
        ):
            # Check that the modified device data make their way into the checker node.
            with utils.report_known_bug_on_failure("4968"):
                utils.wait_until_function_pass(
                    DEVICE_CHECK_TIMEOUT,
                    1,
                    self.check_config_data,
                    CHECKER_IP,
                    MODIFIED_DATA,
                )

        with allure_step_with_separate_logging(
            "step_check_modified_device_data_is_visible_on_configurer"
        ):
            # Check that the modified device data make their way into the configurer
            # node.
            with utils.report_known_bug_on_failure("4968"):
                utils.wait_until_function_pass(
                    DEVICE_CHECK_TIMEOUT,
                    1,
                    self.check_config_data,
                    CONFIGURER_IP,
                    MODIFIED_DATA,
                )

        with allure_step_with_separate_logging("step_delete_device_data_via_setter"):
            # Send a request to delete the sample test data on the device and check
            # that the request went OK.
            with utils.report_known_bug_on_failure("4968"):
                templated_requests.delete_templated_request(
                    f"{DIRECTORY_WITH_TEMPLATE_FOLDERS}/datamod1",
                    mapping,
                    host=SETTER_IP,
                )

        with allure_step_with_separate_logging("step_check_device_data_is_deleted"):
            # Get the device data and make sure it is empty again.
            with utils.report_known_bug_on_failure("4968"):
                self.check_config_data(SETTER_IP, EMPTY_DATA)

        with allure_step_with_separate_logging(
            "step_check_device_data_deletion_is_visible_on_checker"
        ):
            # Check that the device data deletion makes its way into the checker node.
            with utils.report_known_bug_on_failure("4968"):
                utils.wait_until_function_pass(
                    DEVICE_CHECK_TIMEOUT,
                    1,
                    self.check_config_data,
                    CHECKER_IP,
                    EMPTY_DATA,
                )

        with allure_step_with_separate_logging(
            "step_check_device_data_deletion_is_visible_on_configurer"
        ):
            # Check that the device data deletion makes its way into the configurer
            # node.
            with utils.report_known_bug_on_failure("4968"):
                utils.wait_until_function_pass(
                    DEVICE_CHECK_TIMEOUT,
                    1,
                    self.check_config_data,
                    CONFIGURER_IP,
                    EMPTY_DATA,
                )
