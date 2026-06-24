#
# Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License v1.0 which accompanies this distribution,
# and is available at http://www.eclipse.org/legal/epl-v10.html
#
# Based on the original Robot Framework integration test:
# https://github.com/opendaylight/integration-test/blob/901c7e139945b436d95a44b3b592904c3d7a4f9f/csit/suites/netconf/CRUD-ACTION/CRUD-ACTION.robot
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
DEVICE_TYPE_RPC = "rpc-device"
DEVICE_TYPE_RPC_CREATE = "rpc-create-device"
DEVICE_TYPE_RPC_DELETE = "rpc-delete-device"
USE_NETCONF_CONNECTOR = variables.USE_NETCONF_CONNECTOR
DELETE_LOCATION = "delete_location"
RPC_FILE = "variables/netconf/CRUD/customaction/customaction.xml"
ODL_NETCONF_NAMESPACE = variables.ODL_NETCONF_NAMESPACE
REST_API = variables.REST_API
RESTCONF_ROOT = variables.RESTCONF_ROOT

log = logging.getLogger(__name__)


@pytest.mark.crud
@pytest.mark.testtool
@pytest.mark.functional
@pytest.mark.smoke
@pytest.mark.single_device
@pytest.mark.usefixtures("preconditions")
@pytest.mark.usefixtures("log_test_suite_start_end_to_karaf")
@pytest.mark.usefixtures("log_test_case_start_end_to_karaf")
@pytest.mark.run(order=SuiteOrder.CRUD_ACTION)
class TestCrudAction:

    def get_config_data(self) -> str:
        """Get and return the config data from the device.

        Returns:
            str: The raw XML text representation of the device's configuration data.
        """
        url = (
            f"{REST_API}/network-topology:network-topology/topology=topology-netconf/"
            f"node={DEVICE_NAME}/yang-ext:mount?content=config"
        )
        headers = {"Accept": "application/yang-data+xml"}
        data = templated_requests.get_from_uri(url, headers=headers).text

        return data

    def check_config_data(
        self, expected: str, regex: bool = False, contains: bool = False
    ):
        """Validates the mounted device's configuration data against an expected value.

        Args:
            expected (str): The expected string, regex pattern or substring
                to validate against.
            regex (bool): If True, treats `expected` as a regular expression pattern.
            contains (bool): If True, asserts that `expected` is a substring
                found anywhere within the config data.

        Returns:
            None
        """
        data = self.get_config_data()
        if regex:
            assert re.match(expected, data) is not None
        elif contains:
            assert expected in data
        else:
            assert expected == data

    @pytest.fixture()
    def netconf_testtool(self, allure_step_with_separate_logging):
        """Starts and manages the underlying Netconf testtool simulator process.

        This fixture handles the lifecycle of the simulator process. It
        starts the netconf testtool with the required schemas and RPC
        configurations, yields the running process and guarantees the process
        is terminated after execution.

        Yields:
            subprocess.Popen: The running testtool process handler.
        """
        with allure_step_with_separate_logging("step_start_netconf_testtool"):
            """Start netconf testtool."""
            testtool_process = netconf.start_testtool(
                "build_tools/netconf-testtool.jar",
                device_count=1,
                schemas="variables/netconf/CRUD/schemas",
                rpc_config=RPC_FILE,
                mdsal=True,
        )
        yield testtool_process
        with allure_step_with_separate_logging("step_stop_netconf_testtool"):
            """Stop netconf testtool."""
            netconf.stop_testtool(testtool_process)

    @pytest.fixture
    def connected_netconf_testtools(
        self, netconf_testtool, allure_step_with_separate_logging
    ):
        """Mounts the testtool simulator into ODL and manages the connection lifecycle.

        By requiring the `netconf_testtool` fixture, this setup ensures the
        simulator process is running first. It then manages the connection between ODL
        and the netconf testtool simulator.

        Args:
            netconf_testtool: Fixture that starts the netconf testtool.
            allure_step_with_separate_logging: Fixture used to log distinct steps into
                the Allure report.

        Yields:
            None: This fixture manages ODL connection state and environment setup;
            it does not return an object to the test.
        """
        with allure_step_with_separate_logging(
            "step_check_device_is_not_configured_at_beginning"
        ):
            """Sanity check making sure our device is not there. Fail if found."""
            utils.wait_until_function_pass(
                5, 20, netconf.check_device_has_no_netconf_connector, DEVICE_NAME
            )

        with allure_step_with_separate_logging("step_configure_device_on_netconf"):
            """Make request to configure a testtool device on Netconf connector."""
            global DEVICE_TYPE_RPC
            DEVICE_TYPE_RPC = "default" if USE_NETCONF_CONNECTOR else DEVICE_TYPE_RPC
            netconf.configure_device_in_netconf(
                DEVICE_NAME,
                device_type=DEVICE_TYPE_RPC_CREATE,
                http_timeout=2,
                http_method="post",
            )

        with allure_step_with_separate_logging(
            "step_check_ODL_has_netconf_connector_for_device"
        ):
            count = netconf.count_netconf_connectors_for_device(DEVICE_NAME)
            assert count == 1

        with allure_step_with_separate_logging(
            "step_wait_for_device_to_become_connected"
        ):
            """Wait until the device becomes available through Netconf."""
            netconf.wait_device_connected(DEVICE_NAME)

        yield

        with allure_step_with_separate_logging("step_deconfigure_device_from_netconf"):
            """Make request to deconfigure the testtool device on Netconf connector."""
            netconf.configure_device_in_netconf(
                DEVICE_NAME,
                device_type=DEVICE_TYPE_RPC_DELETE,
                http_timeout=2,
                http_method="post",
            )

        with allure_step_with_separate_logging(
            "step_check_device_going_to_be_gone_after_deconfiguring"
        ):
            """Check that the device is really going to be gone. Fail
            if found after one minute. This is an expected behavior as the
            delete request is sent to the config subsystem which then triggers
            asynchronous destruction of the netconf connector referring to the
            device and the device's data. This test makes sure this
            asynchronous operation does not take unreasonable amount of time
            by making sure that both the netconf connector and the device's
            data is gone before reporting success."""
            netconf.wait_device_fully_removed(DEVICE_NAME)

    @allure.description(
        textwrap.dedent(
            """
            **Test suite to perform basic CRUD operations.**

            Perform basic operations (Create, Read, Update and Delete or CRUD) \
            on device data mounted onto a netconf connector using RPC for node \
            supporting Yang 1.1 addition and see if invoking Action Operation work.
            """
        )
    )
    def test_crud_action(
        self, connected_netconf_testtools, allure_step_with_separate_logging
    ):

        with allure_step_with_separate_logging("step_check_device_data_is_empty"):
            """Get the device data and make sure it is empty."""
            escaped = re.escape(ODL_NETCONF_NAMESPACE)
            self.check_config_data(rf'<data xmlns="{escaped}"(\/>|><\/data>)', True)

        with allure_step_with_separate_logging(
            "step_invoke_yang1.1_action_via_xml_post"
        ):
            """Send a sample test data label into the device and check that
            the request went OK."""
            mapping = {"DEVICE_NAME": DEVICE_NAME, "RESTCONF_ROOT": RESTCONF_ROOT}
            templated_requests.post_templated_request(
                f"{DIRECTORY_WITH_TEMPLATE_FOLDERS}/dataorigaction", mapping, json=False
            )

        with allure_step_with_separate_logging(
            "step_invoke_yang1.1_action_via_json_post"
        ):
            """Send a sample test data label into the device and check that
            the request went OK."""
            mapping = {"DEVICE_NAME": DEVICE_NAME, "RESTCONF_ROOT": RESTCONF_ROOT}
            templated_requests.post_as_json_rfc8040_templated(
                f"{DIRECTORY_WITH_TEMPLATE_FOLDERS}/dataorigaction", mapping, json=False
            )

        with allure_step_with_separate_logging(
            "step_invoke_yang1.1_augmentation_via_xml_post"
        ):
            """Send a sample test data label into the device and check that
            the request went OK."""
            mapping = {"DEVICE_NAME": DEVICE_NAME, "RESTCONF_ROOT": RESTCONF_ROOT}
            templated_requests.post_templated_request(
                f"{DIRECTORY_WITH_TEMPLATE_FOLDERS}/augment", mapping, json=False
            )

        with allure_step_with_separate_logging(
            "step_invoke_yang1.1_augmentation_via_json_post"
        ):
            """Send a sample test data label into the device and check that
            the request went OK."""
            mapping = {"DEVICE_NAME": DEVICE_NAME, "RESTCONF_ROOT": RESTCONF_ROOT}
            templated_requests.post_as_json_rfc8040_templated(
                f"{DIRECTORY_WITH_TEMPLATE_FOLDERS}/augment", mapping, json=False
            )
