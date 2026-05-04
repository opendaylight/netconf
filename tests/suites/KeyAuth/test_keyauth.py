#
# Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License v1.0 which accompanies this distribution,
# and is available at http://www.eclipse.org/legal/epl-v10.html
#

import logging
import os
import textwrap

import allure
import pytest

from libraries import netconf
from libraries import templated_requests
from libraries import utils
from libraries.variables import variables

DIRECTORY_WITH_KEYAUTH_TEMPLAE = "variables/netconf/KeyAuth"
PK_PASSPHRASE = "topsecret"
DEVICE_NAME = "netconf-test-device"
DEVICE_TYPE_PASSWD = "full-uri-device"
DEVICE_TYPE_KEY = "full-uri-device-key"
NETOPEER_PORT = 830
NETOPEER_USERNAME = "netconf"
NETOPEER_PASSWORD = "wrong"
NETOPEER_KEY = "device-key"
USE_NETCONF_CONNECTOR = False
MODULES_API = variables.MODULES_API
RESTCONF_ROOT = variables.RESTCONF_ROOT

log = logging.getLogger(__name__)


@pytest.mark.usefixtures("preconditions")
@pytest.mark.usefixtures("log_test_suite_start_end_to_karaf")
@pytest.mark.usefixtures("log_test_case_start_end_to_karaf")
@pytest.mark.run(order=4)
class TestKeyAuth:

    def add_netcong_key(self):
        """Add Netconf Southbound key containing details about device
        private key and passphrase
        """
        mapping = {"DEVICE_KEY": NETOPEER_KEY, "RESTCONF_ROOT": RESTCONF_ROOT}
        templated_requests.post_templated_request(
            DIRECTORY_WITH_KEYAUTH_TEMPLAE, mapping, json=False
        )

    def get_controller_modules(self):
        resp = templated_requests.get_from_uri(MODULES_API)
        log.info(f"Response content: {resp.content}")
        assert resp.status_code == 200
        assert "ietf-restconf" in resp.content

    @pytest.fixture(scope="class", autouse=True)
    def suite_setup(self):
        """Get the suite ready for keyauth test cases."""
        self.device_type_passw = (
            "default" if USE_NETCONF_CONNECTOR else DEVICE_TYPE_PASSWD
        )
        self.add_netcong_key()
        yield

    @allure.description(
        textwrap.dedent(
            """
            **Test suite to verify the device mount using public key based auth.**
            """
        )
    )
    def test_keyauth(self, allure_step_with_separate_logging):

        with allure_step_with_separate_logging(
            "step_check_device_is_not_configured_at_begining"
        ):
            """Sanity check making sure our device is not there. Fail if found."""
            utils.wait_until_function_pass(
                5, 20, netconf.check_device_has_no_netconf_connector, DEVICE_NAME
            )

        with allure_step_with_separate_logging("step_configure_device_on_netconf"):
            """Make request to configure netconf netopeer with wrong password.
            Correct auth is netconf/netconf. ODL should connect to device using
            public key auth as password auth will fail.
            """
            netconf.configure_device_in_netconf(
                DEVICE_NAME,
                device_type=DEVICE_TYPE_KEY,
                device_port=NETOPEER_PORT,
                device_user=NETOPEER_USERNAME,
                device_password=NETOPEER_PASSWORD,
                device_key=NETOPEER_KEY,
                http_timeout=2,
            )

        with allure_step_with_separate_logging(
            "step_wait_for_device_to_become_connected"
        ):
            """Wait until the device becomes available through Netconf."""
            netconf.wait_device_connected(DEVICE_NAME)

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
