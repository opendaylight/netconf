#
# Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License v1.0 which accompanies this distribution,
# and is available at http://www.eclipse.org/legal/epl-v10.html
#
# Based on the original Robot Framework integration test:
# https://github.com/opendaylight/integration-test/blob/901c7e139945b436d95a44b3b592904c3d7a4f9f/csit/suites/netconf/clustering/outages.robot
#

from collections.abc import Callable
import contextlib
from dataclasses import dataclass
import logging
import textwrap

import allure
import pytest

from libraries import cluster
from libraries import netconf
from libraries import templated_requests
from libraries import utils
from libraries.variables import variables
from suites.suite_order import SuiteOrder

DEVICE_CHECK_TIMEOUT = 60
DEVICE_BOOT_TIMEOUT = 100
DEVICE_NAME = "netconf-test-device"
DEVICE_TYPE = "configure-via-topology"
DIRECTORY_WITH_TEMPLATE_FOLDERS = "variables/netconf/CRUD"

RESTCONF_ROOT = variables.RESTCONF_ROOT
ODL_NETCONF_NAMESPACE = variables.ODL_NETCONF_NAMESPACE

# The suite mirrors the original Robot "node1/node2/node3" naming: the device is
# configured and deconfigured through node 1, and each of the three data
# operations is routed through a node that is still up while another one is
# down.
NODE_IPS = variables.CLUSTER_MEMBER_IPS
CONFIGURER_IP = NODE_IPS[0]

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


@dataclass(frozen=True)
class OutageCycle:
    """One node outage and the data operation performed during it.

    Attributes:
        node (int): 1-based number of the cluster member killed for this cycle.
        writer_node (int): 1-based number of the member the data operation is
            routed through; always a member that stays up.
        operation (str): Name of the data operation ("create", "modify",
            "delete"), used to build the Allure step names.
        result (str): Name of the operation's result ("new_device_data",
            "modified_device_data", "device_data_removal"), used to build the
            Allure step names of the checks.
        data_operation (Callable): Library function performing the data
            operation.
        template_dir (str): Template folder the operation reads its payload
            (or, for a delete, its location) from.
        expected_data (str): Config data every member is expected to report
            once the operation has propagated.
        operation_bug (str | None): Bug id the operation and the propagation
            check on the surviving members are attributed to, or None when the
            original Robot test case reported no known bug.
        catchup_bug (str): Bug id the check on the restarted member is
            attributed to.
    """

    node: int
    writer_node: int
    operation: str
    result: str
    data_operation: Callable
    template_dir: str
    expected_data: str
    operation_bug: str | None
    catchup_bug: str


# Each of the three CRUD operations is done once, while a different member is
# down: "create" with node 1 down, "modify" with node 2 down and "delete" with
# node 3 down. The data operation is always routed through the next member up,
# matching the sessions the original Robot suite used.
OUTAGE_CYCLES = (
    OutageCycle(
        node=1,
        writer_node=2,
        operation="create",
        result="new_device_data",
        data_operation=cluster.create_device_data,
        template_dir=f"{DIRECTORY_WITH_TEMPLATE_FOLDERS}/dataorig",
        expected_data=ORIGINAL_DATA,
        operation_bug=None,
        catchup_bug="5761",
    ),
    OutageCycle(
        node=2,
        writer_node=3,
        operation="modify",
        result="modified_device_data",
        data_operation=cluster.modify_device_data,
        template_dir=f"{DIRECTORY_WITH_TEMPLATE_FOLDERS}/datamod1",
        expected_data=MODIFIED_DATA,
        operation_bug="5762",
        catchup_bug="5761",
    ),
    OutageCycle(
        node=3,
        writer_node=1,
        operation="delete",
        result="device_data_removal",
        data_operation=cluster.delete_device_data,
        template_dir=f"{DIRECTORY_WITH_TEMPLATE_FOLDERS}/datamod1",
        expected_data=EMPTY_DATA,
        operation_bug="5762",
        catchup_bug="5761",
    ),
)


@pytest.mark.cluster
@pytest.mark.crud
@pytest.mark.testtool
@pytest.mark.functional
@pytest.mark.usefixtures("odl_three_node_cluster")
@pytest.mark.run(order=SuiteOrder.CLUSTERING_OUTAGES)
class TestClusteringOutages:

    def report_known_bug(self, bug_id: str | None):
        """Attributes a failure to a known bug, when the step has one.

        Args:
            bug_id (str | None): Bug id to attribute a failure to, or None to
                let the failure be reported as-is.

        Returns:
            The utils.report_known_bug_on_failure context manager for a bug id,
            a null context otherwise.
        """
        if bug_id is None:
            return contextlib.nullcontext()
        return utils.report_known_bug_on_failure(bug_id)

    def dump_topology(self, host: str):
        """Logs both views of the netconf topology as seen by one member.

        Mirrors the original Robot suite dumping the topology of every member it
        restarts, which makes a failed catch-up check easier to diagnose. No
        retrying is needed here because cluster.start_member has already waited
        for the member's RESTCONF to answer.

        Args:
            host (str): Cluster member to query.

        Returns:
            None
        """
        url = (
            f"{RESTCONF_ROOT}/data/network-topology:network-topology/"
            f"topology=topology-netconf"
        )
        for content in ("config", "nonconfig"):
            response = templated_requests.get_from_uri(
                f"{url}?content={content}", host=host
            )
            topology = templated_requests.get_pretty_response(response)
            log.info(f"{content} topology as seen by {host}:\n{topology}")

    def check_device_data_on_nodes(
        self, hosts: list[str], expected: str, timeout: int = DEVICE_CHECK_TIMEOUT
    ):
        """Waits until every given member reports the expected device data.

        Args:
            hosts (list[str]): Cluster members to query.
            expected (str): Config data expected on every member.
            timeout (int): Seconds to wait per member.

        Returns:
            None
        """
        for host in hosts:
            utils.wait_until_function_pass(
                timeout,
                1,
                netconf.check_device_config_data,
                DEVICE_NAME,
                expected,
                host=host,
            )

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
        visible on all 3 nodes, then deconfigures it through the same node at the
        end and waits until it is gone from all 3 nodes. The deconfiguration also
        covers the original suite's expectation that configuring still works
        after all the node outages.

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

        with allure_step_with_separate_logging("step_configure_device_on_netconf"):
            # Use node 1 to configure a testtool device on Netconf connector.
            with utils.report_known_bug_on_failure("5089"):
                netconf.configure_device_in_netconf(
                    DEVICE_NAME, device_type=DEVICE_TYPE, host=CONFIGURER_IP
                )

        with allure_step_with_separate_logging(
            "step_wait_for_device_to_become_visible_for_all_nodes"
        ):
            # Check that the cluster communication about a new Netconf device
            # configuration works.
            for host in NODE_IPS:
                netconf.wait_device_connected(
                    DEVICE_NAME, host=host, timeout=DEVICE_CHECK_TIMEOUT
                )

        with allure_step_with_separate_logging(
            "step_wait_for_device_ownership_to_settle"
        ):
            # Being connected on every node does not mean the device's entity
            # ownership has settled; wait for owner + two followers so the first
            # data operation is not lost to post-mount churn.
            cluster.wait_device_ownership_settled(DEVICE_NAME, host=CONFIGURER_IP)

        yield

        with allure_step_with_separate_logging("step_deconfigure_device_in_netconf"):
            # Make request to deconfigure the device on Netconf connector to clean
            # things up and also check that it still works after all the node
            # outages.
            netconf.remove_device_from_netconf(DEVICE_NAME, host=CONFIGURER_IP)

        with allure_step_with_separate_logging("step_check_device_deconfigured"):
            # Check that the device deconfiguration is propagated throughout the
            # cluster correctly.
            for host in NODE_IPS:
                netconf.wait_device_fully_removed(DEVICE_NAME, host=host)

    def run_outage_cycle(self, cycle: OutageCycle, allure_step_with_separate_logging):
        """Performs one data operation while one cluster member is down.

        Kills the member, routes the data operation through a member that stays
        up, checks the result propagates to the members that survived, restarts
        the killed member and finally checks it catches up with the change made
        while it was down.

        Args:
            cycle (OutageCycle): The outage and the data operation to perform.
            allure_step_with_separate_logging: Fixture used to log distinct steps
                into the Allure report.

        Returns:
            None
        """
        node = cycle.node
        node_ip = cluster.get_member_ip(node)
        writer_ip = cluster.get_member_ip(cycle.writer_node)
        surviving_ips = [host for host in NODE_IPS if host != node_ip]

        with allure_step_with_separate_logging(
            f"step_kill_node{node}_before_{cycle.operation}"
        ):
            # Simulate the node crashing just before the device data operation,
            # fail if the node survives.
            cluster.kill_member(node)

        with allure_step_with_separate_logging(
            f"step_{cycle.operation}_device_data_with_node{node}_down"
        ):
            # Check that the requests work when the node is down. As ODL may be in
            # the process of connecting a possible new master to the device, the
            # operation is retried until the device reflects it.
            with self.report_known_bug(cycle.operation_bug):
                cycle.data_operation(
                    DEVICE_NAME,
                    cycle.template_dir,
                    cycle.expected_data,
                    host=writer_ip,
                )

        with allure_step_with_separate_logging(
            f"step_check_{cycle.result}_is_visible_on_nodes_without_node{node}"
        ):
            # Check that the change is propagated in the cluster even when the
            # node is down.
            with self.report_known_bug(cycle.operation_bug):
                self.check_device_data_on_nodes(surviving_ips, cycle.expected_data)

        with allure_step_with_separate_logging(
            f"step_restart_node{node}_after_{cycle.operation}"
            f"_and_dump_its_topology_data"
        ):
            # Simulate the node being restarted by an admin just after the device
            # data operation and its propagation in the cluster, fail if the node
            # fails to boot.
            cluster.start_member(node)
            self.dump_topology(node_ip)

        with allure_step_with_separate_logging(
            f"step_check_{cycle.result}_is_visible_on_node{node}"
        ):
            # Check that the change made while the node was down is propagated to
            # the restarted node as well.
            with utils.report_known_bug_on_failure(cycle.catchup_bug):
                self.check_device_data_on_nodes(
                    [node_ip], cycle.expected_data, timeout=DEVICE_BOOT_TIMEOUT
                )

        with allure_step_with_separate_logging(
            f"step_wait_for_device_ownership_to_restabilize_after_node{node}_restart"
        ):
            # When the restarted node rejoins, the device's master/slave mount
            # points churn while entity ownership re-settles across the cluster.
            # Wait for the device to be connected again on every node and for all
            # three members to be candidates (owner + two followers), so the next
            # outage cycle does not lose its data operation to the churn.
            for host in NODE_IPS:
                netconf.wait_device_connected(
                    DEVICE_NAME, host=host, timeout=DEVICE_CHECK_TIMEOUT
                )
            cluster.wait_device_ownership_settled(DEVICE_NAME, host=node_ip)

    @allure.description(
        textwrap.dedent(
            """
            **Netconf cluster node outage test suite (CRUD operations).**

            Perform one of the basic operations (Create, Read, Update and Delete \
            or CRUD) on device data mounted onto a netconf connector while one of \
            the nodes is down and see if they work. Then bring the dead node up \
            and check that the operations that were made while it was down are \
            visible on it as well.

            The node is brought down before each of the "Create", "Update" and \
            "Delete" operations and brought back up after these operations. \
            Before the dead node is brought up, a step makes sure the operation \
            is properly propagated within the cluster.

            Currently each of the 3 operations is done once. "Create" is done \
            while node 1 is down, "Update" while node 2 is down and "Delete" \
            while node 3 is down.
            """
        )
    )
    def test_outages(self, configured_device, allure_step_with_separate_logging):
        with allure_step_with_separate_logging(
            "step_check_device_data_is_seen_as_empty_on_all_nodes"
        ):
            # Sanity check against possible data left-overs from previous suites.
            # Also causes the suite to wait until the entire cluster sees the
            # device and its data mount.
            self.check_device_data_on_nodes(NODE_IPS, EMPTY_DATA)

        for cycle in OUTAGE_CYCLES:
            self.run_outage_cycle(cycle, allure_step_with_separate_logging)
