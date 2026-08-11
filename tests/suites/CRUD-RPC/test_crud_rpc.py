#
# Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License v1.0 which accompanies this distribution,
# and is available at http://www.eclipse.org/legal/epl-v10.html
#
# Based on the original Robot Framework integration test:
# https://github.com/opendaylight/integration-test/blob/901c7e139945b436d95a44b3b592904c3d7a4f9f/csit/suites/netconf/CRUD/CRUD-RPC.robot
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
ODL_NETCONF_NAMESPACE = variables.ODL_NETCONF_NAMESPACE
RESTCONF_ROOT = variables.RESTCONF_ROOT

log = logging.getLogger(__name__)


@pytest.mark.crud
@pytest.mark.testtool
@pytest.mark.functional
@pytest.mark.smoke
@pytest.mark.single_device
@pytest.mark.usefixtures("odl_standalone")
@pytest.mark.run(order=SuiteOrder.CRUD_RPC)
class TestCrudRpc:

    def count_netconf_connectors(self):
        """Assert that exactly one netconf connector exists for the device.

        Returns:
            None
        """
        count = netconf.count_netconf_connectors_for_device(DEVICE_NAME)
        assert count == 1

    @pytest.fixture()
    def netconf_testtool(self, allure_step_with_separate_logging):
        """Start the netconf testtool simulator and stop it after the test.

        Yields:
            subprocess.Popen: The running testtool process handler.
        """
        with allure_step_with_separate_logging("step_start_netconf_testtool"):
            testtool_process = netconf.start_testtool(
                "build_tools/netconf-testtool.jar",
                device_count=1,
                schemas="variables/netconf/CRUD/schemas",
                mdsal=True,
            )
        yield testtool_process
        with allure_step_with_separate_logging("step_stop_netconf_testtool"):
            netconf.stop_testtool(testtool_process)

    @pytest.fixture
    def connected_netconf_testtools(
        self, netconf_testtool, allure_step_with_separate_logging
    ):
        """Mount the testtool device into ODL and unmount it after the test.

        Yields:
            None
        """
        with allure_step_with_separate_logging(
            "step_check_device_is_not_configured_at_beginning"
        ):
            utils.wait_until_function_pass(
                5, 20, netconf.check_device_has_no_netconf_connector, DEVICE_NAME
            )

        with allure_step_with_separate_logging("step_configure_device_on_netconf"):
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
            utils.wait_until_function_pass(5, 1, self.count_netconf_connectors)

        with allure_step_with_separate_logging(
            "step_wait_for_device_to_become_connected"
        ):
            netconf.wait_device_connected(DEVICE_NAME)

        yield

        with allure_step_with_separate_logging("step_deconfigure_device_from_netconf"):
            netconf.configure_device_in_netconf(
                DEVICE_NAME,
                device_type=DEVICE_TYPE_RPC_DELETE,
                http_timeout=2,
                http_method="post",
            )

        with allure_step_with_separate_logging(
            "step_check_device_going_to_be_gone_after_deconfiguring"
        ):
            netconf.wait_device_fully_removed(DEVICE_NAME)

    @allure.description(
        textwrap.dedent(
            """
            **Test suite to perform basic CRUD operations.**

            Perform basic operations (Create, Read, Update and Delete or CRUD) \
            on device data mounted onto a netconf connector using RPC for node \
            addition and see if they work.
            """
        )
    )
    def test_crud_rpc(
        self, connected_netconf_testtools, allure_step_with_separate_logging
    ):

        with allure_step_with_separate_logging("step_check_device_data_is_empty"):
            netconf.check_device_data_is_empty(DEVICE_NAME)

        with allure_step_with_separate_logging("step_create_device_data_label_via_xml"):
            mapping = {"DEVICE_NAME": DEVICE_NAME, "RESTCONF_ROOT": RESTCONF_ROOT}
            templated_requests.post_templated_request(
                f"{DIRECTORY_WITH_TEMPLATE_FOLDERS}/dataorig", mapping, json=False
            )

        with allure_step_with_separate_logging(
            "step_check_device_data_label_is_created"
        ):
            netconf.check_device_config_data(
                DEVICE_NAME,
                f'<data xmlns="{ODL_NETCONF_NAMESPACE}">'
                f'<cont xmlns="urn:opendaylight:test:netconf:crud">'
                f"<l>Content</l></cont></data>",
            )

        with allure_step_with_separate_logging("step_modify_device_data_label_via_xml"):
            mapping = {"DEVICE_NAME": DEVICE_NAME, "RESTCONF_ROOT": RESTCONF_ROOT}
            templated_requests.put_templated_request(
                f"{DIRECTORY_WITH_TEMPLATE_FOLDERS}/datamod1", mapping, json=False
            )

        with allure_step_with_separate_logging(
            "step_check_device_data_label_is_modified"
        ):
            netconf.check_device_config_data(
                DEVICE_NAME,
                f'<data xmlns="{ODL_NETCONF_NAMESPACE}">'
                f'<cont xmlns="urn:opendaylight:test:netconf:crud">'
                f"<l>Modified Content</l></cont></data>",
            )

        with allure_step_with_separate_logging(
            "step_deconfigure_device_from_netconf_temporarily"
        ):
            netconf.configure_device_in_netconf(
                DEVICE_NAME,
                device_type=DEVICE_TYPE_RPC_DELETE,
                http_timeout=2,
                http_method="post",
            )

        with allure_step_with_separate_logging("step_wait_for_device_to_be_gone"):
            netconf.wait_device_fully_removed(DEVICE_NAME)

        with allure_step_with_separate_logging("step_configure_the_device_back"):
            netconf.configure_device_in_netconf(
                DEVICE_NAME,
                device_type=DEVICE_TYPE_RPC_CREATE,
                http_timeout=2,
                http_method="post",
            )

        with allure_step_with_separate_logging("step_wait_for_device_to_reconnect"):
            netconf.wait_device_connected(DEVICE_NAME)

        with allure_step_with_separate_logging(
            "step_check_modified_device_data_is_still_there"
        ):
            utils.wait_until_function_pass(
                60,
                1,
                netconf.check_device_config_data,
                DEVICE_NAME,
                f'<data xmlns="{ODL_NETCONF_NAMESPACE}">'
                f'<cont xmlns="urn:opendaylight:test:netconf:crud">'
                f"<l>Modified Content</l></cont></data>",
            )

        with allure_step_with_separate_logging("step_modify_device_data_again"):
            mapping = {"DEVICE_NAME": DEVICE_NAME, "RESTCONF_ROOT": RESTCONF_ROOT}
            templated_requests.put_templated_request(
                f"{DIRECTORY_WITH_TEMPLATE_FOLDERS}/datamod2", mapping, json=False
            )

        with allure_step_with_separate_logging(
            "step_check_device_data_is_modified_again"
        ):
            netconf.check_device_config_data(
                DEVICE_NAME,
                f'<data xmlns="{ODL_NETCONF_NAMESPACE}">'
                f'<cont xmlns="urn:opendaylight:test:netconf:crud">'
                f"<l>Another Modified Content</l></cont></data>",
            )

        with allure_step_with_separate_logging(
            "step_modify_device_data_label_via_json"
        ):
            mapping = {"DEVICE_NAME": DEVICE_NAME, "RESTCONF_ROOT": RESTCONF_ROOT}
            templated_requests.put_templated_request(
                f"{DIRECTORY_WITH_TEMPLATE_FOLDERS}/datamodjson", mapping, json=True
            )

        with allure_step_with_separate_logging(
            "step_check_device_data_label_is_modified_via_json"
        ):
            netconf.check_device_config_data(
                DEVICE_NAME,
                f'<data xmlns="{ODL_NETCONF_NAMESPACE}">'
                f'<cont xmlns="urn:opendaylight:test:netconf:crud">'
                f"<l>Content Modified via JSON</l></cont></data>",
            )

        with allure_step_with_separate_logging("step_create_car_list"):
            mapping = {"DEVICE_NAME": DEVICE_NAME, "RESTCONF_ROOT": RESTCONF_ROOT}
            templated_requests.post_templated_request(
                f"{DIRECTORY_WITH_TEMPLATE_FOLDERS}/cars", mapping, json=False
            )

        with allure_step_with_separate_logging("step_check_car_list_created"):
            data = netconf.get_device_config_data(DEVICE_NAME)
            assert "<id>KEEP</id>" in data
            assert "<id>SMALL</id>" not in data
            assert "<model>Isetta</model>" not in data
            assert "<manufacturer>BMW</manufacturer>" not in data
            assert "<year>1953</year>" not in data
            assert "<category>microcar</category>" not in data
            assert "<id>TOYOTA</id>" not in data
            assert "<model>Camry</model>" not in data
            assert "<manufacturer>Toyota</manufacturer>" not in data
            assert "<year>1982</year>" not in data
            assert "<category>sedan</category>" not in data

        with allure_step_with_separate_logging(
            "step_add_device_data_item1_via_xml_post"
        ):
            mapping = {"DEVICE_NAME": DEVICE_NAME, "RESTCONF_ROOT": RESTCONF_ROOT}
            templated_requests.post_templated_request(
                f"{DIRECTORY_WITH_TEMPLATE_FOLDERS}/item1", mapping, json=False
            )

        with allure_step_with_separate_logging("step_check_item1_is_created"):
            data = netconf.get_device_config_data(DEVICE_NAME)
            assert "<id>SMALL</id>" in data
            assert "<model>Isetta</model>" in data
            assert "<manufacturer>BMW</manufacturer>" in data
            assert "<year>1953</year>" in data
            assert "<category>microcar</category>" in data
            assert "<id>TOYOTA</id>" not in data
            assert "<model>Camry</model>" not in data
            assert "<manufacturer>Toyota</manufacturer>" not in data
            assert "<year>1982</year>" not in data
            assert "<category>sedan</category>" not in data

        with allure_step_with_separate_logging(
            "step_add_device_data_item2_via_json_post"
        ):
            mapping = {"DEVICE_NAME": DEVICE_NAME, "RESTCONF_ROOT": RESTCONF_ROOT}
            templated_requests.post_templated_request(
                f"{DIRECTORY_WITH_TEMPLATE_FOLDERS}/item2", mapping
            )

        with allure_step_with_separate_logging("step_check_item2_is_created"):
            data = netconf.get_device_config_data(DEVICE_NAME)
            assert "<id>SMALL</id>" in data
            assert "<model>Isetta</model>" in data
            assert "<manufacturer>BMW</manufacturer>" in data
            assert "<year>1953</year>" in data
            assert "<category>microcar</category>" in data
            assert "<id>TOYOTA</id>" in data
            assert "<model>Camry</model>" in data
            assert "<manufacturer>Toyota</manufacturer>" in data
            assert "<year>1982</year>" in data
            assert "<category>sedan</category>" in data

        with allure_step_with_separate_logging("step_delete_device_data"):
            mapping = {"DEVICE_NAME": DEVICE_NAME, "RESTCONF_ROOT": RESTCONF_ROOT}
            templated_requests.delete_templated_request(
                f"{DIRECTORY_WITH_TEMPLATE_FOLDERS}/datamod1", mapping
            )
            templated_requests.delete_templated_request(
                f"{DIRECTORY_WITH_TEMPLATE_FOLDERS}/item1", mapping
            )

        with allure_step_with_separate_logging("step_check_device_data_is_deleted"):
            escaped = re.escape(ODL_NETCONF_NAMESPACE)
            netconf.check_device_config_data(
                DEVICE_NAME,
                rf'<data xmlns="{escaped}"(\/>|><\/data>)',
                regex=True,
            )
