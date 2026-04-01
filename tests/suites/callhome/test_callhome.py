#
# Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License v1.0 which accompanies this distribution,
# and is available at http://www.eclipse.org/legal/epl-v10.html
#

import logging
import textwrap

import allure
import pytest

from libraries import infra
from libraries import NetconfCallHome
from libraries import restconf_utils
from libraries import templated_requests
from libraries import utils
from libraries.variables import variables

MOUNT_POINT_URL = (
    "/rests/data/network-topology:network-topology/"
    "topology=topology-netconf?content=nonconfig"
)
NETCONF_MOUNT_EXPECTED_VALUE = (
    '"connection-status":"connected"',
    '"node-id":"netopeer2"',
    '"available-capabilities"',
)
ODL_IP = variables.ODL_IP
WHITELIST = variables.CALLHOME_WHITELIST
NETCONF_KEYSTORE_DATA_URL = variables.NETCONF_KEYSTORE_DATA_URL

log = logging.getLogger(__name__)


@pytest.fixture(scope="class")
def netopeer_pub_key():
    """Get the hostkey used by netopeer."""
    rc, netopeer_public_key = infra.shell(
        "awk '{print $2}' /tmp/configuration-files/ssh_host_rsa_key.pub"
    )
    yield netopeer_public_key


@pytest.fixture(scope="class")
def incorrect_pub_key():
    """Get hostkey which is different than the one used by netopeer."""
    rc, incorrect_public_key = infra.shell(
        "awk '{print $2}' /tmp/incorrect_ssh_host_rsa_key.pub"
    )
    yield incorrect_public_key


@pytest.fixture(scope="function")
def test_teardown():
    """Clear keystore and allowed devices config."""
    yield
    templated_requests.delete_from_uri_request(
        WHITELIST, expected_code=templated_requests.ALLOWED_DELETE_STATUS_CODES
    )
    templated_requests.delete_from_uri_request(
        NETCONF_KEYSTORE_DATA_URL,
        expected_code=templated_requests.ALLOWED_DELETE_STATUS_CODES,
    )


@pytest.mark.usefixtures("preconditions")
@pytest.mark.usefixtures("log_test_suite_start_end_to_karaf")
@pytest.mark.usefixtures("log_test_case_start_end_to_karaf")
@pytest.mark.run(order=2)
class TestCallHome:

    @allure.description(
        textwrap.dedent(
            """
            **Test suite to verify callhome functionality over SSH transport protocol.**

            Registration in OpenDaylight Controller happens via restconf interface.
            Netopeer2-server docker container plays a roleof the netconf device
            with call-home feature. Docker-compose file is used to configure netopeer2
            docker container(netconf configuration templates, host-key).
            """
        )
    )
    def test_callhome_over_ssh_with_correct_device_credentials(
        self, allure_step_with_separate_logging, netopeer_pub_key, test_teardown
    ):
        with allure_step_with_separate_logging(
            "step_callhome_over_ssh_with_correct_device_credentials"
        ):
            """Correct credentials should result to successful mount.
            CONNECTED should be the device status."""
            NetconfCallHome.register_ssh_call_home_device_in_odl_controller(
                device_name="netopeer2",
                hostkey=netopeer_pub_key,
                username="root",
                password="root",
            )
            utils.wait_until_function_pass(
                45, 2, NetconfCallHome.check_device_status, "netopeer2", "CONNECTED"
            )
            utils.wait_until_function_pass(
                15,
                2,
                restconf_utils.check_for_elements_at_uri,
                MOUNT_POINT_URL,
                NETCONF_MOUNT_EXPECTED_VALUE,
            )

    def test_callhome_over_ssh_with_incorrect_device_credentials(
        self, allure_step_with_separate_logging, netopeer_pub_key, test_teardown
    ):
        with allure_step_with_separate_logging(
            "step_callhome_over_ssh_with_incorrect_device_credentials"
        ):
            """Incorrect credentials should result to failed mount.
            FAILED_AUTH_FAILURE should be the device status."""
            NetconfCallHome.register_ssh_call_home_device_in_odl_controller(
                device_name="netopeer2",
                hostkey=netopeer_pub_key,
                username="root",
                password="incorrect",
            )
            utils.wait_until_function_pass(
                45,
                2,
                NetconfCallHome.check_device_status,
                "netopeer2",
                "FAILED_AUTH_FAILURE",
            )
            utils.wait_until_function_pass(
                15,
                2,
                utils.run_function_and_expect_error,
                restconf_utils.check_for_elements_at_uri,
                MOUNT_POINT_URL,
                NETCONF_MOUNT_EXPECTED_VALUE,
            )

    def test_callhome_over_ssh_with_correct_global_credentials(
        self, allure_step_with_separate_logging, netopeer_pub_key, test_teardown
    ):
        with allure_step_with_separate_logging(
            "step_callhome_over_ssh_with_correct_global_credentials"
        ):
            """CallHome SSH device registered with global credentials
            should result to successful mount."""
            NetconfCallHome.register_global_credentials_for_ssh_call_home_devices(
                username="root", password="root"
            )
            NetconfCallHome.register_ssh_call_home_device_in_odl_controller(
                device_name="netopeer2",
                hostkey=netopeer_pub_key,
            )
            utils.wait_until_function_pass(
                45, 2, NetconfCallHome.check_device_status, "netopeer2", "CONNECTED"
            )
            utils.wait_until_function_pass(
                15,
                2,
                restconf_utils.check_for_elements_at_uri,
                MOUNT_POINT_URL,
                NETCONF_MOUNT_EXPECTED_VALUE,
            )

    def test_callhome_over_ssh_with_incorrect_global_credentials(
        self, allure_step_with_separate_logging, netopeer_pub_key, test_teardown
    ):
        with allure_step_with_separate_logging(
            "step_callhome_over_ssh_with_incorrect_global_credentials"
        ):
            """CallHome SSH device registered with wrong global credentials
            should fail to mount."""
            NetconfCallHome.register_global_credentials_for_ssh_call_home_devices(
                username="root", password="incorrect"
            )
            NetconfCallHome.register_ssh_call_home_device_in_odl_controller(
                device_name="netopeer2",
                hostkey=netopeer_pub_key,
            )
            utils.wait_until_function_pass(
                45,
                2,
                NetconfCallHome.check_device_status,
                "netopeer2",
                "FAILED_AUTH_FAILURE",
            )
            utils.wait_until_function_pass(
                15,
                2,
                utils.run_function_and_expect_error,
                restconf_utils.check_for_elements_at_uri,
                MOUNT_POINT_URL,
                NETCONF_MOUNT_EXPECTED_VALUE,
            )

    def test_callhome_over_ssh_with_incorrect_node_id(
        self,
        allure_step_with_separate_logging,
        incorrect_pub_key,
        netopeer_pub_key,
        test_teardown,
    ):
        with allure_step_with_separate_logging(
            "step_callhome_over_ssh_with_incorrect_node_id"
        ):
            """CallHome from device that does not have an entry in per-device
            credential with result to mount point failure."""
            NetconfCallHome.register_ssh_call_home_device_in_odl_controller(
                device_name="incorrect_hostname",
                hostkey=incorrect_pub_key,
                username="root",
                password="root",
            )
            NetconfCallHome.register_ssh_call_home_device_in_odl_controller(
                device_name="netopeer2",
                hostkey=netopeer_pub_key,
            )
            utils.wait_until_function_pass(
                45, 2, NetconfCallHome.check_device_status, "netopeer2", "DISCONNECTED"
            )
            utils.wait_until_function_pass(
                15,
                2,
                NetconfCallHome.check_device_status,
                "incorrect_hostname",
                "DISCONNECTED",
            )
            utils.wait_until_function_pass(
                15,
                2,
                utils.run_function_and_expect_error,
                restconf_utils.check_for_elements_at_uri,
                MOUNT_POINT_URL,
                NETCONF_MOUNT_EXPECTED_VALUE,
            )

    def test_callhome_with_rogue_devices(
        self, allure_step_with_separate_logging, incorrect_pub_key, test_teardown
    ):
        with allure_step_with_separate_logging("step_callhome_with_rogue_devices"):
            """A Rogue Device will fail to callhome and wont be able to mount
            because the keys are not added in whitelist. FAILED_NOT_ALLOWED
            should be the device status."""
            NetconfCallHome.register_ssh_call_home_device_in_odl_controller(
                device_name="netopeer2",
                hostkey=incorrect_pub_key,
                username="root",
                password="root",
            )
            utils.wait_until_function_pass(
                45, 2, NetconfCallHome.check_device_status, None, "FAILED_NOT_ALLOWED"
            )
            utils.wait_until_function_pass(
                15,
                2,
                utils.run_function_and_expect_error,
                restconf_utils.check_for_elements_at_uri,
                MOUNT_POINT_URL,
                NETCONF_MOUNT_EXPECTED_VALUE,
            )

    def test_callhome_over_tls_with_correct_certificate_and_key(
        self, allure_step_with_separate_logging, test_teardown
    ):
        with allure_step_with_separate_logging(
            "step_callhome_over_tls_with_correct_certificate_and_key"
        ):
            """Using correct certificate and key pair should result to successful
            mount. CONNECTED should be the device status."""
            NetconfCallHome.register_keys_and_certificates_in_odl_cotroller()
            NetconfCallHome.register_tls_call_home_device_in_odl_controller(
                device_name="netopeer2",
                key_id="tls-device-key",
                certificate_id="tls-device-certificate",
            )
            utils.wait_until_function_pass(
                45, 2, NetconfCallHome.check_device_status, "netopeer2", "CONNECTED"
            )
            utils.wait_until_function_pass(
                15,
                2,
                restconf_utils.check_for_elements_at_uri,
                MOUNT_POINT_URL,
                NETCONF_MOUNT_EXPECTED_VALUE,
            )
