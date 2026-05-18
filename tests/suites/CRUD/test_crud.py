#
# Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License v1.0 which accompanies this distribution,
# and is available at http://www.eclipse.org/legal/epl-v10.html
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

DIRECTORY_WITH_TEMPLATE_FOLDERS = "variables/netconf/CRUD"
DEVICE_NAME = "netconf-test-device"
DEVICE_TYPE = "full-uri-device"
USE_NETCONF_CONNECTOR = False
ODL_NETCONF_NAMESPACE = variables.ODL_NETCONF_NAMESPACE
REST_API = variables.REST_API
RESTCONF_ROOT = variables.RESTCONF_ROOT

log = logging.getLogger(__name__)


@pytest.mark.usefixtures("preconditions")
@pytest.mark.usefixtures("log_test_suite_start_end_to_karaf")
@pytest.mark.usefixtures("log_test_case_start_end_to_karaf")
@pytest.mark.run(order=5)
class TestCrud:

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
        return templated_requests.get_from_uri(url, headers=headers).text

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
        """
        data = self.get_config_data()
        if regex:
            assert re.match(expected, data) is not None
        elif contains:
            assert expected in data
        else:
            assert expected == data

    def count_netconf_connectors(self):
        """
        """
        count = netconf.count_netconf_connectors_for_device(DEVICE_NAME)
        assert count == 1

    #@pytest.fixture(scope="class", autouse=True)
    #def suite_setup(self):
    #    """Pytest fixture to initialize and tear down the Netconf testtool simulator."""
    #    testtool_process = netconf.start_testtool(
    #        "build_tools/netconf-testtool.jar",
    #        device_count=1,
    #        schemas="variables/netconf/CRUD/schemas",
    #        mdsal=True,
    #    )
    #    yield
    #    netconf.stop_testtool(testtool_process)

    @allure.description(
        textwrap.dedent(
            """
            **Test suite to perform basic CRUD operations.**

            Perform basic operations (Create, Read, Update and Delete or CRUD)
            on device data mounted onto a netconf connector using full URI device
            type for node addition and see if they work.
            """
        )
    )
    def test_crud(self, allure_step_with_separate_logging):

        with allure_step_with_separate_logging("step_start_testtool"):
            """Start test tool, then wait for all its devices to become online."""
            self.testtool_process = netconf.start_testtool(
                "build_tools/netconf-testtool.jar",
                device_count=1,
                schemas="variables/netconf/CRUD/schemas",
                mdsal=True,
            )

        with allure_step_with_separate_logging(
            "step_check_device_is_not_configured_at_beginning"
        ):
            """Sanity check making sure our device is not there. Fail if found."""
            netconf.check_device_has_no_netconf_connector(DEVICE_NAME)

        with allure_step_with_separate_logging("step_configure_device_on_netconf"):
            """Make request to configure a testtool device on Netconf connector."""
            netconf.configure_device_in_netconf(DEVICE_NAME, device_type=DEVICE_TYPE, http_timeout=2)

        with allure_step_with_separate_logging(
            "step_check_ODL_has_netconf_connector_for_device"
        ):
            """Get the list of configured devices and search for our device there. Fail if not found."""
            utils.wait_until_function_pass(5, 1, self.count_netconf_connectors)

        with allure_step_with_separate_logging(
            "step_wait_for_device_to_become_connected"
        ):
            """Wait until the device becomes available through Netconf."""
            netconf.wait_device_connected(DEVICE_NAME)

        with allure_step_with_separate_logging("step_check_device_data_is_empty"):
            """Get the device data and make sure it is empty."""
            escaped = re.escape(ODL_NETCONF_NAMESPACE)
            self.check_config_data(rf'<data xmlns="{escaped}"(\/>|><\/data>)', regex=True)

        with allure_step_with_separate_logging("step_create_device_data_label_via_xml"):
            """Send a sample test data label into the device and check that the request went OK."""
            mapping = {"DEVICE_NAME": DEVICE_NAME, "RESTCONF_ROOT": RESTCONF_ROOT}
            templated_requests.post_templated_request(
                f"{DIRECTORY_WITH_TEMPLATE_FOLDERS}/dataorig", mapping, json=False
            )

        with allure_step_with_separate_logging("step_check_device_data_label_is_created"):
            """Get the device data label and make sure it contains the created content."""
            self.check_config_data(
                f'<data xmlns="{ODL_NETCONF_NAMESPACE}"><cont xmlns="urn:opendaylight:test:netconf:crud">'
                f"<l>Content</l></cont></data>"
            )

        with allure_step_with_separate_logging(
            "step_modify_device_data_label_via_xml"
        ):
            """Send a request to change the sample test data label and check that the request went OK."""
            mapping = {"DEVICE_NAME": DEVICE_NAME, "RESTCONF_ROOT": RESTCONF_ROOT}
            templated_requests.put_templated_request(
                f"{DIRECTORY_WITH_TEMPLATE_FOLDERS}/datamod1", mapping, json=False
            )

        with allure_step_with_separate_logging(
            "step_check_device_data_label_is_modified"
        ):
            """Get the device data label and make sure it contains the modified content."""
            self.check_config_data(
                f'<data xmlns="{ODL_NETCONF_NAMESPACE}"><cont xmlns="urn:opendaylight:test:netconf:crud">'
                f"<l>Modified Content</l></cont></data>"
            )

        with allure_step_with_separate_logging(
            "step_deconfigure_device_from_netconf_temporarily"
        ):
            """Make request to deconfigure the testtool device on Netconf connector.
            This is the first part of the configure/deconfigure cycle of the device.
            The purpose of cycling the device like this is to see that the configuration
            data was really stored in the device."""
            netconf.remove_device_from_netconf(DEVICE_NAME)

        with allure_step_with_separate_logging("step_wait_for_device_to_be_gone"):
            """Wait for the device to completely disappear."""
            netconf.wait_device_fully_removed(DEVICE_NAME)

        with allure_step_with_separate_logging("step_configure_the_device_back"):
            """Configure the device again. This is the second step of the device configuration."""
            netconf.configure_device_in_netconf(DEVICE_NAME, device_type=DEVICE_TYPE)

        with allure_step_with_separate_logging("step_wait_for_device_to_reconnect"):
            """Wait until the device becomes available through Netconf."""
            netconf.wait_device_connected(DEVICE_NAME)

        with allure_step_with_separate_logging(
            "step_check_modified_device_data_is_still_there"
        ):
            """Get the device data and make sure it contains the modified content."""
            utils.wait_until_function_pass(
                60,
                1,
                self.check_config_data,
                f'<data xmlns="{ODL_NETCONF_NAMESPACE}"><cont xmlns="urn:opendaylight:test:netconf:crud">'
                f"<l>Modified Content</l></cont></data>",
            )

        with allure_step_with_separate_logging("step_modify_device_data_again"):
            """Send a request to change the sample test data and check that the request went OK."""
            mapping = {"DEVICE_NAME": DEVICE_NAME, "RESTCONF_ROOT": RESTCONF_ROOT}
            templated_requests.put_templated_request(
                f"{DIRECTORY_WITH_TEMPLATE_FOLDERS}/datamod2", mapping, json=False
            )

        with allure_step_with_separate_logging(
            "step_check_device_data_is_modified_again"
        ):
            """Get the device data and make sure it contains the modified content."""
            self.check_config_data(
                f'<data xmlns="{ODL_NETCONF_NAMESPACE}"><cont xmlns="urn:opendaylight:test:netconf:crud">'
                f"<l>Another Modified Content</l></cont></data>"
            )

        with allure_step_with_separate_logging(
            "step_modify_device_data_label_via_json"
        ):
            """Send a JSON request to change the sample test data label and check that the request went OK."""
            mapping = {"DEVICE_NAME": DEVICE_NAME, "RESTCONF_ROOT": RESTCONF_ROOT}
            templated_requests.put_templated_request(
                f"{DIRECTORY_WITH_TEMPLATE_FOLDERS}/datamodjson", mapping, json=True
            )

        with allure_step_with_separate_logging(
            "step_check_device_data_label_is_modified_via_json"
        ):
            """Get the device data label as XML and make sure it matches the content posted as JSON."""
            self.check_config_data(
                f'<data xmlns="{ODL_NETCONF_NAMESPACE}"><cont xmlns="urn:opendaylight:test:netconf:crud">'
                f"<l>Content Modified via JSON</l></cont></data>"
            )

        with allure_step_with_separate_logging("step_create_car_list"):
            """Send a request to create a list of cars in the sample test data label and check that the request went OK."""
            mapping = {"DEVICE_NAME": DEVICE_NAME, "RESTCONF_ROOT": RESTCONF_ROOT}
            templated_requests.post_templated_request(
                f"{DIRECTORY_WITH_TEMPLATE_FOLDERS}/cars", mapping, json=False
            )

        with allure_step_with_separate_logging("step_check_car_list_created"):
            """Get the device data and make sure the car list was created correctly."""
            data = self.get_config_data()
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
            """Send a request to create a data item in the test list and check that the request went OK."""
            mapping = {"DEVICE_NAME": DEVICE_NAME, "RESTCONF_ROOT": RESTCONF_ROOT}
            templated_requests.post_templated_request(
                f"{DIRECTORY_WITH_TEMPLATE_FOLDERS}/item1", mapping, json=False
            )

        with allure_step_with_separate_logging("step_check_item1_is_created"):
            """Get the device data as XML and make sure item1 was created."""
            data = self.get_config_data()
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
            """Send a JSON request to change the sample test data and check that the request went OK."""
            mapping = {"DEVICE_NAME": DEVICE_NAME, "RESTCONF_ROOT": RESTCONF_ROOT}
            templated_requests.post_templated_request(
                f"{DIRECTORY_WITH_TEMPLATE_FOLDERS}/item2", mapping
            )

        with allure_step_with_separate_logging("step_check_item2_is_created"):
            """Get the device data as XML and make sure item2 matches the content posted as JSON in the previous case."""
            data = self.get_config_data()
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
            """Send a request to delete the sample test data on the device and check that the request went OK."""
            mapping = {"DEVICE_NAME": DEVICE_NAME, "RESTCONF_ROOT": RESTCONF_ROOT}
            templated_requests.delete_templated_request(
                f"{DIRECTORY_WITH_TEMPLATE_FOLDERS}/datamod1", mapping
            )
            templated_requests.delete_templated_request(
                f"{DIRECTORY_WITH_TEMPLATE_FOLDERS}/item1", mapping
            )

        with allure_step_with_separate_logging("step_check_device_data_is_deleted"):
            """Get the device data and make sure it is empty again."""
            escaped = re.escape(ODL_NETCONF_NAMESPACE)
            self.check_config_data(rf'<data xmlns="{escaped}"(\/>|><\/data>)', regex=True)

        with allure_step_with_separate_logging("step_deconfigure_device_from_netconf"):
            """Make request to deconfigure the testtool device on Netconf connector."""
            netconf.remove_device_from_netconf(DEVICE_NAME)

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
