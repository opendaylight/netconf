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

from libraries import NetconfCallHome

DATADIR = "variables/netconf/MDSAL"
DATAEXT = "msg"

log = logging.getLogger(__name__)


@pytest.mark.usefixtures("preconditions")
@pytest.mark.usefixtures("log_test_suite_start_end_to_karaf")
@pytest.mark.usefixtures("log_test_case_start_end_to_karaf")
@pytest.mark.run(order=1)
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
    def test_callhome(self, allure_step_with_separate_logging):

        with allure_step_with_separate_logging("step_callhome_over_ssh_with_correct_device_credentials"):
            """Correct credentials should result to successful mount. CONNECTED should be the device status."""
            NetconfCallHome.apply_ssh_based_call_home_configuration()
            NetconfCallHome.register_ssh_call_home_devices_in_odl_controller(
                device_name="netopeer2",
                hostkey=NETOPEER_PUB_KEY,
                username="root",
                password="root"
            )
