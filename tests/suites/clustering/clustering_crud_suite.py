#
# Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License v1.0 which accompanies this distribution,
# and is available at http://www.eclipse.org/legal/epl-v10.html
#
# Shared body of the clustered CRUD suites. The netconf clustering CRUD and
# Bug 8086 CSIT suites run the very same steps against the very same device
# and differ only in how that device is configured and which known bugs their
# steps are attributed to, so the flow lives here once and each suite
# subclasses it. The module is deliberately not named test_* so pytest does
# not collect the base class itself.
#

import contextlib

import pytest

from libraries import cluster
from libraries import netconf
from libraries import utils
from libraries.variables import variables

DEVICE_CHECK_TIMEOUT = 20
DEVICE_NAME = "netconf-test-device"
# Attempts a data operation gets before the step fails. A write to a freshly
# mounted clustered device routed through a member that is not the device's
# owner answers "Commit of operation failed" while the owner's device session
# is still settling, which took two or three attempts to clear when measured.
# Deliberately small: the modify and delete steps are attributed to a known
# bug, and a budget of minutes would turn an intermittent regression of it into
# a green run.
DATA_OP_RETRY_COUNT = 10
DIRECTORY_WITH_TEMPLATE_FOLDERS = "variables/netconf/CRUD"

CONFIGURER_IP = variables.CLUSTER_MEMBER_IPS[0]
SETTER_IP = variables.CLUSTER_MEMBER_IPS[1]
CHECKER_IP = variables.CLUSTER_MEMBER_IPS[2]

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


class ClusteringCrudSuite:
    """Create, read, update and delete device data across an ODL cluster.

    Subclasses supply the device configuration and the bug attribution of the
    steps, then call run_crud_flow from their own test method. Each subclass
    keeps its own marks, execution order and Allure description, so the two
    suites stay one to one with the CSIT suites they were ported from.

    Attributes:
        DEVICE_TYPE (str): Template type the device is configured with.
        CONFIGURE_BUG (str | None): Bug id the device configuration step is
            attributed to, or None when the original suite reported no bug.
        DATA_OP_BUG (str | None): Bug id the modify and delete steps and their
            propagation checks are attributed to, or None as above.
    """

    DEVICE_TYPE: str = "configure-via-topology"
    CONFIGURE_BUG: str | None = None
    DATA_OP_BUG: str | None = None

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

    @pytest.fixture()
    def device_schema_directory(self):
        """Prepares the device's schema cache directory, if the suite needs one.

        Does nothing by default; a suite that configures the device with a
        pre-populated schema cache overrides this fixture.

        Yields:
            None: No schema directory is prepared.
        """
        yield None

    @pytest.fixture()
    def configured_device(
        self,
        netconf_testtool,
        device_schema_directory,
        allure_step_with_separate_logging,
    ):
        """Mounts the device via the configurer node and manages its lifecycle.

        Configures the device through CONFIGURER_IP and waits until it becomes
        visible on all 3 nodes, mirroring how a real client would only ever
        talk to one cluster member while data propagates to the others.

        Args:
            netconf_testtool: Fixture that starts the netconf testtool.
            device_schema_directory: Fixture that prepares the schema cache
                directory the device is configured with and yields its path, or
                yields None when the suite does not use one.
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
            schema_directory = (
                {}
                if device_schema_directory is None
                else {"schema_directory": device_schema_directory}
            )
            with self.report_known_bug(self.CONFIGURE_BUG):
                netconf.configure_device_in_netconf(
                    DEVICE_NAME,
                    device_type=self.DEVICE_TYPE,
                    host=CONFIGURER_IP,
                    **schema_directory,
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
            # after default timeout. This is an expected behavior as the unmount
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

    def run_crud_flow(self, allure_step_with_separate_logging):
        """Runs create, read, update and delete on the device across the cluster.

        Args:
            allure_step_with_separate_logging: Fixture used to log distinct steps
                into the Allure report.

        Returns:
            None
        """
        with allure_step_with_separate_logging(
            "step_check_device_data_is_seen_as_empty_on_configurer"
        ):
            # Get the device data as seen by configurer and make sure it is empty.
            netconf.wait_device_config_data(
                DEVICE_NAME,
                EMPTY_DATA,
                timeout=DEVICE_CHECK_TIMEOUT,
                host=CONFIGURER_IP,
            )

        with allure_step_with_separate_logging(
            "step_check_device_data_is_seen_as_empty_on_checker"
        ):
            # Get the device data as seen by checker and make sure it is empty.
            netconf.wait_device_config_data(
                DEVICE_NAME, EMPTY_DATA, timeout=DEVICE_CHECK_TIMEOUT, host=CHECKER_IP
            )

        with allure_step_with_separate_logging(
            "step_check_device_data_is_seen_as_empty_on_setter"
        ):
            # Get the device data as seen by setter and make sure it is empty.
            netconf.wait_device_config_data(
                DEVICE_NAME, EMPTY_DATA, timeout=DEVICE_CHECK_TIMEOUT, host=SETTER_IP
            )

        with allure_step_with_separate_logging("step_create_device_data_via_setter"):
            # Send some sample test data into the device and check that the request
            # went OK. This is the first write after the mount, and routed through a
            # member that is typically not the device's owner, so it can come back as
            # "Commit of operation failed" while the owner's device session is still
            # settling. The write is reissued and read back until it lands, the same
            # way the entity and outages suites handle it.
            cluster.create_device_data(
                DEVICE_NAME,
                f"{DIRECTORY_WITH_TEMPLATE_FOLDERS}/dataorig",
                ORIGINAL_DATA,
                host=SETTER_IP,
                retry_count=DATA_OP_RETRY_COUNT,
            )

        with allure_step_with_separate_logging(
            "step_check_new_device_data_is_visible_on_checker"
        ):
            # Check that the created device data make their way into the checker node.
            netconf.wait_device_config_data(
                DEVICE_NAME,
                ORIGINAL_DATA,
                timeout=DEVICE_CHECK_TIMEOUT,
                host=CHECKER_IP,
            )

        with allure_step_with_separate_logging(
            "step_check_new_device_data_is_visible_on_configurer"
        ):
            # Check that the created device data make their way into the configurer
            # node.
            netconf.wait_device_config_data(
                DEVICE_NAME,
                ORIGINAL_DATA,
                timeout=DEVICE_CHECK_TIMEOUT,
                host=CONFIGURER_IP,
            )

        with allure_step_with_separate_logging("step_modify_device_data_via_setter"):
            # Send a request to change the sample test data and check that the request
            # went OK. Reissued and read back like the create above, for the same
            # reason.
            with self.report_known_bug(self.DATA_OP_BUG):
                cluster.modify_device_data(
                    DEVICE_NAME,
                    f"{DIRECTORY_WITH_TEMPLATE_FOLDERS}/datamod1",
                    MODIFIED_DATA,
                    host=SETTER_IP,
                    retry_count=DATA_OP_RETRY_COUNT,
                )

        with allure_step_with_separate_logging(
            "step_check_modified_device_data_is_visible_on_checker"
        ):
            # Check that the modified device data make their way into the checker node.
            with self.report_known_bug(self.DATA_OP_BUG):
                netconf.wait_device_config_data(
                    DEVICE_NAME,
                    MODIFIED_DATA,
                    timeout=DEVICE_CHECK_TIMEOUT,
                    host=CHECKER_IP,
                )

        with allure_step_with_separate_logging(
            "step_check_modified_device_data_is_visible_on_configurer"
        ):
            # Check that the modified device data make their way into the configurer
            # node.
            with self.report_known_bug(self.DATA_OP_BUG):
                netconf.wait_device_config_data(
                    DEVICE_NAME,
                    MODIFIED_DATA,
                    timeout=DEVICE_CHECK_TIMEOUT,
                    host=CONFIGURER_IP,
                )

        with allure_step_with_separate_logging("step_delete_device_data_via_setter"):
            # Send a request to delete the sample test data on the device and check
            # that the request went OK. Reissued and read back like the writes above,
            # and only sent while the data is still there, so a retry after a delete
            # that did land does not fail on data that is already gone.
            with self.report_known_bug(self.DATA_OP_BUG):
                cluster.delete_device_data(
                    DEVICE_NAME,
                    f"{DIRECTORY_WITH_TEMPLATE_FOLDERS}/datamod1",
                    host=SETTER_IP,
                    retry_count=DATA_OP_RETRY_COUNT,
                )

        with allure_step_with_separate_logging(
            "step_check_device_data_deletion_is_visible_on_checker"
        ):
            # Check that the device data deletion makes its way into the checker node.
            with self.report_known_bug(self.DATA_OP_BUG):
                netconf.wait_device_config_data(
                    DEVICE_NAME,
                    EMPTY_DATA,
                    timeout=DEVICE_CHECK_TIMEOUT,
                    host=CHECKER_IP,
                )

        with allure_step_with_separate_logging(
            "step_check_device_data_deletion_is_visible_on_configurer"
        ):
            # Check that the device data deletion makes its way into the configurer
            # node.
            with self.report_known_bug(self.DATA_OP_BUG):
                netconf.wait_device_config_data(
                    DEVICE_NAME,
                    EMPTY_DATA,
                    timeout=DEVICE_CHECK_TIMEOUT,
                    host=CONFIGURER_IP,
                )
