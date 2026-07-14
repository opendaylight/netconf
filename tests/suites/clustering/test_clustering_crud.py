#
# Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License v1.0 which accompanies this distribution,
# and is available at http://www.eclipse.org/legal/epl-v10.html
#
# Based on the original Robot Framework integration test:
# https://github.com/opendaylight/integration-test/blob/master/csit/suites/netconf/clustering/CRUD.robot
#

import logging
import re
import textwrap

import allure
import pytest

from libraries import netconf
from libraries import templated_requests
from libraries import utils
from libraries.variables import variables
from suites.suite_order import SuiteOrder

DIRECTORY_WITH_TEMPLATE_FOLDERS = "variables/netconf/CRUD"
DEVICE_NAME = "netconf-test-device"
DEVICE_TYPE = "configure-via-topology"
RESTCONF_ROOT = variables.RESTCONF_ROOT
ODL_NETCONF_NAMESPACE = variables.ODL_NETCONF_NAMESPACE

# The suite exercises the same device from 3 different roles: CONFIGURER
# (mounts/unmounts the device), SETTER (writes its data) and CHECKER (only
# observes). Every step is checked from all 3 nodes to confirm the change
# propagated across the cluster, not just on the node that made it.
CONFIGURER_IP = variables.CLUSTER_MEMBER_IPS[0]
SETTER_IP = variables.CLUSTER_MEMBER_IPS[1]
CHECKER_IP = variables.CLUSTER_MEMBER_IPS[2]

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

    def check_config_data(self, host: str, expected: str, regex: bool = False):
        """Validates the mounted device's configuration data as seen by one node.

        Args:
            host (str): Cluster member to query.
            expected (str): The expected string or regex pattern to validate against.
            regex (bool): If True, treats `expected` as a regular expression pattern.

        Returns:
            None
        """
        data = self.get_config_data(host)
        if regex:
            assert re.match(expected, data) is not None
        else:
            assert expected == data

    def count_netconf_connectors(self, host: str):
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

        Yields:
            subprocess.Popen: The running testtool process handler.
        """
        with allure_step_with_separate_logging("step_start_netconf_testtool"):
            testtool_process = netconf.start_testtool(
                "build_tools/netconf-testtool.jar",
                device_count=1,
                schemas="variables/netconf/CRUD/schemas",
            )
        yield testtool_process
        with allure_step_with_separate_logging("step_stop_netconf_testtool"):
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
            netconf.check_device_has_no_netconf_connector(
                DEVICE_NAME, host=CONFIGURER_IP
            )

        with allure_step_with_separate_logging("step_configure_device_via_configurer"):
            netconf.configure_device_in_netconf(
                DEVICE_NAME, device_type=DEVICE_TYPE, host=CONFIGURER_IP
            )

        with allure_step_with_separate_logging(
            "step_check_configurer_has_netconf_connector_for_device"
        ):
            utils.wait_until_function_pass(
                10, 1, self.count_netconf_connectors, CONFIGURER_IP
            )

        with allure_step_with_separate_logging(
            "step_wait_for_device_to_become_visible_for_configurer"
        ):
            netconf.wait_device_connected(DEVICE_NAME, host=CONFIGURER_IP)

        with allure_step_with_separate_logging(
            "step_wait_for_device_to_become_visible_for_checker"
        ):
            netconf.wait_device_connected(DEVICE_NAME, host=CHECKER_IP)

        with allure_step_with_separate_logging(
            "step_wait_for_device_to_become_visible_for_setter"
        ):
            netconf.wait_device_connected(DEVICE_NAME, host=SETTER_IP)

        yield

        with allure_step_with_separate_logging(
            "step_deconfigure_device_via_configurer"
        ):
            netconf.remove_device_from_netconf(DEVICE_NAME, host=CONFIGURER_IP)

        with allure_step_with_separate_logging(
            "step_check_device_deconfigured_on_configurer"
        ):
            netconf.wait_device_fully_removed(DEVICE_NAME, host=CONFIGURER_IP)

        with allure_step_with_separate_logging(
            "step_check_device_deconfigured_on_checker"
        ):
            netconf.wait_device_fully_removed(DEVICE_NAME, host=CHECKER_IP)

        with allure_step_with_separate_logging(
            "step_check_device_deconfigured_on_setter"
        ):
            netconf.wait_device_fully_removed(DEVICE_NAME, host=SETTER_IP)

    @allure.description(
        textwrap.dedent(
            """
            **Test suite to perform CRUD operations across an ODL cluster.**

            Perform basic operations (Create, Read, Update and Delete or CRUD) on
            device data mounted onto a netconf connector, and verify each change is
            visible from every cluster member -- not just the one that made it.

            The device is configured through node 1 (CONFIGURER), its data is
            written through node 2 (SETTER), and every step is verified as seen
            from all 3 nodes.
            """
        )
    )
    def test_crud_clustering(
        self, configured_device, allure_step_with_separate_logging
    ):
        escaped = re.escape(ODL_NETCONF_NAMESPACE)
        empty_data_pattern = rf'<data xmlns="{escaped}"(\/>|><\/data>)'
        mapping = {"DEVICE_NAME": DEVICE_NAME, "RESTCONF_ROOT": RESTCONF_ROOT}

        with allure_step_with_separate_logging(
            "step_check_device_data_is_seen_as_empty_on_configurer"
        ):
            utils.wait_until_function_pass(
                10,
                1,
                self.check_config_data,
                CONFIGURER_IP,
                empty_data_pattern,
                regex=True,
            )

        with allure_step_with_separate_logging(
            "step_check_device_data_is_seen_as_empty_on_checker"
        ):
            utils.wait_until_function_pass(
                10, 1, self.check_config_data, CHECKER_IP, empty_data_pattern, regex=True
            )

        with allure_step_with_separate_logging(
            "step_check_device_data_is_seen_as_empty_on_setter"
        ):
            utils.wait_until_function_pass(
                10, 1, self.check_config_data, SETTER_IP, empty_data_pattern, regex=True
            )

        with allure_step_with_separate_logging("step_create_device_data_via_setter"):
            # Send sample test data into the device through the setter node.
            templated_requests.post_templated_request(
                f"{DIRECTORY_WITH_TEMPLATE_FOLDERS}/dataorig",
                mapping,
                json=False,
                host=SETTER_IP,
            )

        with allure_step_with_separate_logging(
            "step_check_new_device_data_is_visible_on_setter"
        ):
            utils.wait_until_function_pass(
                10, 1, self.check_config_data, SETTER_IP, ORIGINAL_DATA
            )

        with allure_step_with_separate_logging(
            "step_check_new_device_data_is_visible_on_checker"
        ):
            utils.wait_until_function_pass(
                10, 1, self.check_config_data, CHECKER_IP, ORIGINAL_DATA
            )

        with allure_step_with_separate_logging(
            "step_check_new_device_data_is_visible_on_configurer"
        ):
            utils.wait_until_function_pass(
                10, 1, self.check_config_data, CONFIGURER_IP, ORIGINAL_DATA
            )

        with allure_step_with_separate_logging("step_modify_device_data_via_setter"):
            # Change the sample test data through the setter node.
            templated_requests.put_templated_request(
                f"{DIRECTORY_WITH_TEMPLATE_FOLDERS}/datamod1",
                mapping,
                json=False,
                host=SETTER_IP,
            )

        with allure_step_with_separate_logging("step_check_device_data_is_modified"):
            self.check_config_data(SETTER_IP, MODIFIED_DATA)

        with allure_step_with_separate_logging(
            "step_check_modified_device_data_is_visible_on_checker"
        ):
            utils.wait_until_function_pass(
                60, 1, self.check_config_data, CHECKER_IP, MODIFIED_DATA
            )

        with allure_step_with_separate_logging(
            "step_check_modified_device_data_is_visible_on_configurer"
        ):
            utils.wait_until_function_pass(
                60, 1, self.check_config_data, CONFIGURER_IP, MODIFIED_DATA
            )

        with allure_step_with_separate_logging("step_delete_device_data_via_setter"):
            # Delete the sample test data through the setter node.
            templated_requests.delete_templated_request(
                f"{DIRECTORY_WITH_TEMPLATE_FOLDERS}/datamod1", mapping, host=SETTER_IP
            )

        with allure_step_with_separate_logging("step_check_device_data_is_deleted"):
            self.check_config_data(SETTER_IP, empty_data_pattern, regex=True)

        with allure_step_with_separate_logging(
            "step_check_device_data_deletion_is_visible_on_checker"
        ):
            utils.wait_until_function_pass(
                60, 1, self.check_config_data, CHECKER_IP, empty_data_pattern, regex=True
            )

        with allure_step_with_separate_logging(
            "step_check_device_data_deletion_is_visible_on_configurer"
        ):
            utils.wait_until_function_pass(
                60,
                1,
                self.check_config_data,
                CONFIGURER_IP,
                empty_data_pattern,
                regex=True,
            )
