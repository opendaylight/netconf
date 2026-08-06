#
# Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License v1.0 which accompanies this distribution,
# and is available at http://www.eclipse.org/legal/epl-v10.html
#
# Based on the original Robot Framework integration test:
# https://github.com/opendaylight/integration-test/blob/901c7e139945b436d95a44b3b592904c3d7a4f9f/csit/suites/netconf/clustering/entity.robot
#

import logging
import textwrap

import allure
import pytest

from libraries import cluster
from libraries import netconf
from libraries import utils
from libraries.variables import variables
from suites.suite_order import SuiteOrder

DEVICE_CHECK_TIMEOUT = 60
CLUSTER_RECOVERY_TIMEOUT = 120
DEVICE_NAME = "netconf-test-device"
DEVICE_TYPE = "configure-via-topology"
DIRECTORY_WITH_TEMPLATE_FOLDERS = "variables/netconf/CRUD"

ODL_NETCONF_NAMESPACE = variables.ODL_NETCONF_NAMESPACE

# The suite mirrors the original Robot "node1/node2/node3" roles: the device is
# configured and deconfigured through node 1, data is created through node 2,
# and every node is expected to see each result.
NODE_IPS = variables.CLUSTER_MEMBER_IPS
CONFIGURER_IP = NODE_IPS[0]
SETTER_IP = NODE_IPS[1]

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
MODIFIED_DATA_2 = (
    f'<data xmlns="{ODL_NETCONF_NAMESPACE}">'
    f'<cont xmlns="urn:opendaylight:test:netconf:crud">'
    f"<l>Another Modified Content</l></cont></data>"
)

log = logging.getLogger(__name__)


@pytest.mark.cluster
@pytest.mark.testtool
@pytest.mark.functional
@pytest.mark.usefixtures("odl_three_node_cluster")
@pytest.mark.run(order=SuiteOrder.CLUSTERING_ENTITY)
class TestClusteringEntity:

    @pytest.fixture()
    def netconf_testtool(self, allure_step_with_separate_logging):
        """Starts and manages the underlying Netconf testtool simulator process.

        Args:
            allure_step_with_separate_logging: Fixture used to log distinct steps
                into the Allure report.

        This fixture handles the lifecycle of the simulator process. It starts
        the netconf testtool with the required schemas, yields the running
        process and guarantees the process is terminated after execution.

        Yields:
            subprocess.Popen: The running testtool process handler.
        """
        with allure_step_with_separate_logging("step_start_netconf_testtool"):
            # Deploy and start test tool, then wait for its device to become online.
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
        """Mounts the device via node 1 and manages its lifecycle across the cluster.

        Configures the device through CONFIGURER_IP and waits until it becomes
        visible on all 3 nodes, then deconfigures it through the same node at
        the end and waits until it is gone from all 3 nodes.

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
            # Sanity check making sure our device is not there on any node.
            for host in NODE_IPS:
                netconf.check_device_has_no_netconf_connector(DEVICE_NAME, host=host)

        with allure_step_with_separate_logging("step_configure_device_via_node1"):
            # Use node 1 to configure a testtool device on Netconf connector.
            with utils.report_known_bug_on_failure("5089"):
                netconf.configure_device_in_netconf(
                    DEVICE_NAME, device_type=DEVICE_TYPE, host=CONFIGURER_IP
                )

        with allure_step_with_separate_logging(
            "step_wait_for_device_to_become_visible_for_all_nodes"
        ):
            # Wait for the whole cluster to see the device.
            for host in NODE_IPS:
                netconf.wait_device_connected(
                    DEVICE_NAME, host=host, timeout=DEVICE_CHECK_TIMEOUT
                )

        with allure_step_with_separate_logging(
            "step_wait_for_device_ownership_to_settle"
        ):
            # Being connected on every node does not mean the device's entity
            # ownership has settled; wait for owner + two followers so the
            # initial data operations are not lost to post-mount churn.
            cluster.wait_device_ownership_settled(DEVICE_NAME, host=CONFIGURER_IP)

        yield

        with allure_step_with_separate_logging("step_deconfigure_device_via_node1"):
            # Make request to deconfigure the device on Netconf connector.
            netconf.remove_device_from_netconf(DEVICE_NAME, host=CONFIGURER_IP)

        with allure_step_with_separate_logging(
            "step_wait_for_device_to_be_gone_on_all_nodes"
        ):
            # Check that the device is really going to be gone on every node.
            for host in NODE_IPS:
                netconf.wait_device_fully_removed(DEVICE_NAME, host=host)

    @allure.description(
        textwrap.dedent(
            """
            **Test suite for netconf device entity ownership handling during outages.**

            Configures a testtool device onto a netconf connector on a 3-node \
            cluster, creates data on it, then simulates a failure of the node \
            that owns the entity representing the device by killing it. It \
            verifies a new owner is elected, that the surviving followers can \
            still read and modify the device data, and that once the original \
            owner is restarted it sees the up-to-date data and can modify it \
            again. Finally the device is deconfigured and expected to disappear \
            from every node.
            """
        )
    )
    def test_entity(self, configured_device, allure_step_with_separate_logging):
        with allure_step_with_separate_logging(
            "step_check_config_data_before_data_creation"
        ):
            # Check there really is no data present on any of the nodes.
            for host in NODE_IPS:
                utils.wait_until_function_pass(
                    DEVICE_CHECK_TIMEOUT,
                    1,
                    netconf.check_device_config_data,
                    DEVICE_NAME,
                    EMPTY_DATA,
                    host=host,
                )

        with allure_step_with_separate_logging("step_create_device_data_via_node2"):
            # Create some data on the device and propagate it throughout the cluster.
            cluster.create_device_data(
                DEVICE_NAME,
                f"{DIRECTORY_WITH_TEMPLATE_FOLDERS}/dataorig",
                ORIGINAL_DATA,
                host=SETTER_IP,
            )

        with allure_step_with_separate_logging(
            "step_check_config_data_after_data_creation"
        ):
            # Check the data we just added is visible on every node.
            for host in NODE_IPS:
                utils.wait_until_function_pass(
                    DEVICE_CHECK_TIMEOUT,
                    1,
                    netconf.check_device_config_data,
                    DEVICE_NAME,
                    ORIGINAL_DATA,
                    host=host,
                )

        with allure_step_with_separate_logging(
            "step_find_and_shutdown_device_entity_owner"
        ):
            # Find the owner of the entity representing the device plus its two
            # followers, then simulate a failure of the owner by killing it.
            owner, followers = cluster.get_device_entity_owner_and_followers(
                DEVICE_NAME, host=CONFIGURER_IP
            )
            assert len(followers) == 2, f"expected 2 followers, got {followers}"
            follower1_ip = cluster.get_member_ip(followers[0])
            follower2_ip = cluster.get_member_ip(followers[1])
            original_owner_ip = cluster.get_member_ip(owner)
            log.info(f"entity owner is member-{owner}, followers {followers}")
            cluster.kill_member(owner)

        with allure_step_with_separate_logging("step_wait_for_new_owner_to_appear"):
            # Wait for the cluster to recover and elect a new owner for the entity.
            def new_owner_elected():
                """Assert a new owner, different from the killed one, is elected."""
                new_owner, _ = cluster.get_device_entity_owner_and_followers(
                    DEVICE_NAME, host=follower1_ip
                )
                assert new_owner != owner, f"owner is still member-{owner}"

            utils.wait_until_function_pass(
                CLUSTER_RECOVERY_TIMEOUT, 1, new_owner_elected
            )

        with allure_step_with_separate_logging(
            "step_check_config_data_before_modification_with_original_owner_down"
        ):
            # Check the data is still present and retrievable from the followers.
            with utils.report_known_bug_on_failure("6067"):
                for host in (follower1_ip, follower2_ip):
                    utils.wait_until_function_pass(
                        DEVICE_CHECK_TIMEOUT,
                        1,
                        netconf.check_device_config_data,
                        DEVICE_NAME,
                        ORIGINAL_DATA,
                        host=host,
                    )

        with allure_step_with_separate_logging(
            "step_modify_device_data_when_original_owner_is_down"
        ):
            # Attempt to modify the data via a follower after recovery.
            with utils.report_known_bug_on_failure("4968"):
                cluster.modify_device_data(
                    DEVICE_NAME,
                    f"{DIRECTORY_WITH_TEMPLATE_FOLDERS}/datamod1",
                    MODIFIED_DATA,
                    host=follower1_ip,
                )

        with allure_step_with_separate_logging(
            "step_check_config_data_after_modification_with_original_owner_down"
        ):
            # Check the data was written correctly while the original owner is down.
            with utils.report_known_bug_on_failure("6067"):
                for host in (follower1_ip, follower2_ip):
                    utils.wait_until_function_pass(
                        DEVICE_CHECK_TIMEOUT,
                        1,
                        netconf.check_device_config_data,
                        DEVICE_NAME,
                        MODIFIED_DATA,
                        host=host,
                    )

        with allure_step_with_separate_logging("step_restart_original_entity_owner"):
            # Restart the original entity owner and wait for it to come back.
            cluster.start_member(owner)

        with allure_step_with_separate_logging(
            "step_wait_for_device_ownership_to_restabilize"
        ):
            # When the original owner rejoins, the device's master/slave mount
            # points churn while entity ownership re-settles across the cluster.
            # Wait for the device to be connected again on every node and for all
            # three members to be candidates (owner + two followers) before
            # manipulating the data, so writes are not lost to the churn.
            for host in NODE_IPS:
                netconf.wait_device_connected(
                    DEVICE_NAME, host=host, timeout=DEVICE_CHECK_TIMEOUT
                )
            cluster.wait_device_ownership_settled(
                DEVICE_NAME, host=original_owner_ip
            )

        with allure_step_with_separate_logging(
            "step_check_config_data_after_original_owner_restart"
        ):
            # Sanity check that we can still retrieve the data from the original owner.
            with utils.report_known_bug_on_failure("5761"):
                utils.wait_until_function_pass(
                    DEVICE_CHECK_TIMEOUT,
                    1,
                    netconf.check_device_config_data,
                    DEVICE_NAME,
                    MODIFIED_DATA,
                    host=original_owner_ip,
                )

        with allure_step_with_separate_logging(
            "step_modify_device_data_with_original_owner"
        ):
            # Check that the original owner is still able to modify the data.
            with utils.report_known_bug_on_failure("5761"):
                cluster.modify_device_data(
                    DEVICE_NAME,
                    f"{DIRECTORY_WITH_TEMPLATE_FOLDERS}/datamod2",
                    MODIFIED_DATA_2,
                    host=original_owner_ip,
                )

        with allure_step_with_separate_logging(
            "step_check_config_data_after_modification_with_original_owner_up"
        ):
            # Check the data was written as expected and is visible on every node.
            for host in NODE_IPS:
                utils.wait_until_function_pass(
                    DEVICE_CHECK_TIMEOUT,
                    1,
                    netconf.check_device_config_data,
                    DEVICE_NAME,
                    MODIFIED_DATA_2,
                    host=host,
                )
