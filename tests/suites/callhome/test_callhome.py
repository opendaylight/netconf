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

NETOPEER_PUB_KEY = None
INCORRECT_PUB_KEY = None


log = logging.getLogger(__name__)


@pytest.fixture(scope="class")
def suite_setup():
    """Get the suite ready for callhome test cases."""
    infra.copy_file(
        src_dir="variables/netconf/callhome/",
        src_file_name="docker-compose.yaml",
        dst_dir="tmp/",
    )
    infra.copy_file(
        src_dir="variables/netconf/callhome/",
        src_file_name="init_configuration.sh",
        dst_dir="tmp/",
    )
    infra.shell("chmod +x tmp/init_configuration.sh")
    netconf_cl_ssh_port = 4334
    infra.shell(f"sed -i -e 's/ODL_SYSTEM_IP/{ODL_IP}/g' tmp/docker-compose.yaml")
    infra.shell(
        f"sed -i -e 's/NETCONF_CH_SSH/{netconf_cl_ssh_port}/g' tmp/docker-compose.yaml"
    )
    infra.shell("sed -i -e 's/NETCONF_CH_TLS/4335/g' tmp/docker-compose.yaml")
    infra.shell(
        "ssh-keygen -q -t rsa -b 2048 -N '' -m pem -f tmp/incorrect_ssh_host_rsa_key"
    )
    rc, incorrect_public_key = infra.shell(
        "awk '{print $2}' tmp/incorrect_ssh_host_rsa_key.pub"
    )
    global INCORRECT_PUB_KEY
    INCORRECT_PUB_KEY = incorrect_public_key
    yield


@pytest.fixture(scope="function")
def test_setup():
    """Set configuration folder, generates a new host key for the container"""
    infra.shell("rm -rf tmp/configuration-files && mkdir tmp/configuration-files")
    infra.shell(
        "ssh-keygen -q -t rsa -b 2048 -N '' -m pem "
        "-f tmp/configuration-files/ssh_host_rsa_key"
    )
    rc, public_key = infra.shell(
        "cat tmp/configuration-files/ssh_host_rsa_key.pub | awk '{print $2}'"
    )
    global NETOPEER_PUB_KEY
    NETOPEER_PUB_KEY = public_key

    yield

    rc, output = infra.shell(
        "docker-compose --project-directory . -f tmp/docker-compose.yaml logs"
    )
    log.info(output)
    rc, output = infra.shell(
        "docker-compose --project-directory . -f tmp/docker-compose.yaml down"
    )
    log.info(output)
    rc, output = infra.shell("docker ps -a")
    log.info(output)
    infra.shell("rm -rf tmp/configuration-files")
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
        self, allure_step_with_separate_logging, suite_setup, test_setup
    ):
        with allure_step_with_separate_logging(
            "step_callhome_over_ssh_with_correct_device_credentials"
        ):
            """Correct credentials should result to successful mount.
            CONNECTED should be the device status."""
            NetconfCallHome.apply_ssh_based_call_home_configuration()
            NetconfCallHome.register_ssh_call_home_device_in_odl_controller(
                device_name="netopeer2",
                hostkey=NETOPEER_PUB_KEY,
                username="root",
                password="root",
            )
            rc, output = infra.shell(
                "docker-compose --project-directory . -f tmp/docker-compose.yaml up -d"
            )
            assert rc == 0
            utils.wait_until_function_pass(
                45, 2, NetconfCallHome.check_device_status, "CONNECTED"
            )
            utils.wait_until_function_pass(
                15,
                2,
                restconf_utils.check_for_elements_at_uri,
                MOUNT_POINT_URL,
                NETCONF_MOUNT_EXPECTED_VALUE,
            )

    def test_callhome_over_ssh_with_incorrect_device_credentials(
        self, allure_step_with_separate_logging, suite_setup, test_setup
    ):
        with allure_step_with_separate_logging(
            "step_callhome_over_ssh_with_incorrect_device_credentials"
        ):
            """Incorrect credentials should result to failed mount.
            FAILED_AUTH_FAILURE should be the device status."""
            NetconfCallHome.apply_ssh_based_call_home_configuration()
            NetconfCallHome.register_ssh_call_home_device_in_odl_controller(
                device_name="netopeer2",
                hostkey=NETOPEER_PUB_KEY,
                username="root",
                password="incorrect",
            )
            rc, output = infra.shell(
                "docker-compose --project-directory . -f tmp/docker-compose.yaml up -d"
            )
            assert rc == 0
            utils.wait_until_function_pass(
                45, 2, NetconfCallHome.check_device_status, "FAILED_AUTH_FAILURE"
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
        self, allure_step_with_separate_logging, suite_setup, test_setup
    ):
        with allure_step_with_separate_logging(
            "step_callhome_over_ssh_with_correct_global_credentials"
        ):
            """CallHome SSH device registered with global credentials
            should result to successful mount."""
            NetconfCallHome.apply_ssh_based_call_home_configuration()
            NetconfCallHome.register_global_credentials_for_ssh_call_home_devices(
                username="root", password="root"
            )
            NetconfCallHome.register_ssh_call_home_device_in_odl_controller(
                device_name="netopeer2",
                hostkey=NETOPEER_PUB_KEY,
            )
            rc, output = infra.shell(
                "docker-compose --project-directory . -f tmp/docker-compose.yaml up -d"
            )
            assert rc == 0
            utils.wait_until_function_pass(
                45, 2, NetconfCallHome.check_device_status, "CONNECTED"
            )
            utils.wait_until_function_pass(
                15,
                2,
                restconf_utils.check_for_elements_at_uri,
                MOUNT_POINT_URL,
                NETCONF_MOUNT_EXPECTED_VALUE,
            )

    def test_callhome_over_ssh_with_incorrect_global_credentials(
        self, allure_step_with_separate_logging, suite_setup, test_setup
    ):
        with allure_step_with_separate_logging(
            "step_callhome_over_ssh_with_incorrect_global_credentials"
        ):
            """CallHome SSH device registered with wrong global credentials
            should fail to mount."""
            NetconfCallHome.apply_ssh_based_call_home_configuration()
            NetconfCallHome.register_global_credentials_for_ssh_call_home_devices(
                username="root", password="incorrect"
            )
            NetconfCallHome.register_ssh_call_home_device_in_odl_controller(
                device_name="netopeer2",
                hostkey=NETOPEER_PUB_KEY,
            )
            rc, output = infra.shell(
                "docker-compose --project-directory . -f tmp/docker-compose.yaml up -d"
            )
            assert rc == 0
            utils.wait_until_function_pass(
                45, 2, NetconfCallHome.check_device_status, "FAILED_AUTH_FAILURE"
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
        self, allure_step_with_separate_logging, suite_setup, test_setup
    ):
        with allure_step_with_separate_logging(
            "step_callhome_over_ssh_with_incorrect_node_id"
        ):
            """CallHome from device that does not have an entry in per-device
            credential with result to mount point failure."""
            NetconfCallHome.apply_ssh_based_call_home_configuration()
            NetconfCallHome.register_ssh_call_home_device_in_odl_controller(
                device_name="incorrect_hostname",
                hostkey=INCORRECT_PUB_KEY,
                username="root",
                password="root",
            )
            NetconfCallHome.register_ssh_call_home_device_in_odl_controller(
                device_name="netopeer2",
                hostkey=NETOPEER_PUB_KEY,
            )
            rc, output = infra.shell(
                "docker-compose --project-directory . -f tmp/docker-compose.yaml up -d"
            )
            assert rc == 0
            utils.wait_until_function_pass(
                45, 2, NetconfCallHome.check_device_status, "DISCONNECTED"
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
        self, allure_step_with_separate_logging, suite_setup, test_setup
    ):
        with allure_step_with_separate_logging("step_callhome_with_rogue_devices"):
            """A Rogue Device will fail to callhome and wont be able to mount
            because the keys are not added in whitelist. FAILED_NOT_ALLOWED
            should be the device status."""
            NetconfCallHome.apply_ssh_based_call_home_configuration()
            NetconfCallHome.register_ssh_call_home_device_in_odl_controller(
                device_name="netopeer2",
                hostkey=INCORRECT_PUB_KEY,
                username="root",
                password="root",
            )
            rc, output = infra.shell(
                "docker-compose --project-directory . -f tmp/docker-compose.yaml up -d"
            )
            assert rc == 0
            utils.wait_until_function_pass(
                45, 2, NetconfCallHome.check_device_status, "FAILED_NOT_ALLOWED"
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
        self, allure_step_with_separate_logging, suite_setup, test_setup
    ):
        with allure_step_with_separate_logging(
            "step_callhome_over_tls_with_correct_certificate_and_key"
        ):
            """Using correct certificate and key pair should result
            to successful mount. CONNECTED should be the device status."""
            NetconfCallHome.apply_tls_based_call_home_configuration()
            NetconfCallHome.register_keys_and_certificates_in_odl_cotroller()
            NetconfCallHome.register_tls_call_home_device_in_odl_controller(
                device_name="netopeer2",
                key_id="tls-device-key",
                certificate_id="tls-device-certificate",
            )
            rc, output = infra.shell(
                "docker-compose --project-directory . -f tmp/docker-compose.yaml up -d"
            )
            assert rc == 0
            utils.wait_until_function_pass(
                45, 2, NetconfCallHome.check_device_status, "CONNECTED"
            )
            utils.wait_until_function_pass(
                15,
                2,
                restconf_utils.check_for_elements_at_uri,
                MOUNT_POINT_URL,
                NETCONF_MOUNT_EXPECTED_VALUE,
            )
