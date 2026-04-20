#
# Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License v1.0 which accompanies this distribution,
# and is available at http://www.eclipse.org/legal/epl-v10.html
#

import logging
import textwrap
import time

import allure
import pytest
import xml.etree.ElementTree as ET

from libraries import infra
from libraries import restconf
from libraries import templated_requests
from libraries import utils
from libraries.variables import variables


RESTCONF_ROOT = variables.RESTCONF_ROOT
XML_MESSAGE_HEADERS = variables.XML_MESSAGE_HEADERS
TEMPLATE_FOLDER = "suites/notifications/templates"
RFC8040_STREAMS_URI = (
    f"{RESTCONF_ROOT}/data/ietf-restconf-monitoring:restconf-state/streams"
)
NODES_STREAM_PATH = (
    "network-topology:network-topology/datastore=CONFIGURATION/scope=BASE"
)
RESTCONF_SUBSCRIBE_DATA = "subscribe.xml"
RESTCONF_CONFIG_DATA = "config_data.xml"
RECEIVER_LOG_FILE = "receiver.log"
CONTROLLER_LOG_LEVEL = "INFO"


log = logging.getLogger(__name__)


@pytest.mark.usefixtures("preconditions")
@pytest.mark.usefixtures("log_test_suite_start_end_to_karaf")
@pytest.mark.usefixtures("log_test_case_start_end_to_karaf")
@pytest.mark.run(order=3)
class TestCallHome:

    def log_response(self, resp):
        """Log response.

        Args:
            resp (requests.Response): The HTTP response object returned
                by the requests library.
        Returns:
            None
        """
        log.info(f"{resp=}")
        log.info(f"{resp.headers=}")
        log.info(f"{resp.text=}")

    @allure.description(
        textwrap.dedent(
            """
            **Basic tests for RESTCONF Data Change Notifications (DCN).

            Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.

            This program and the accompanying materials are made available under the \
            terms of the Eclipse Public License v1.0 which accompanies \
            this distribution, and is available at \
            http://www.eclipse.org/legal/epl-v10.html

            Test suite performs basic subscribtion case for data store notifications. \
            For procedure description see the \
            https://wiki.opendaylight.org/view/OpenDaylight_Controller\
            :MD-SAL:Restconf:Change_event_notification_subscription


            This suite uses inventory (config part) as an area to make dummy writes \
            into, just to trigger data change listener to produce a notification. \
            Openflowplugin may have some data there, and before Boron, \
            netconf-connector was also exposing some data in inventory.

            To avoid unexpected responses, this suite depetes all data from config \
            inventory, so this suite should not be followed by any suite expecting \
            default data there.

            Covered bugs:
            *Bug 3934* - Websockets: Scope ONE doesn't work correctly

            *TODO*: Use cars/people model for data
            """
        )
    )
    def test_data_change_notification(
        self,
        allure_step_with_separate_logging,
    ):
        with allure_step_with_separate_logging("step_set_controller_log_level"):
            """Set controller log level."""
            infra.execute_karaf_command(f"log:set {CONTROLLER_LOG_LEVEL}")

        with allure_step_with_separate_logging("step_create_dcn_stream"):
            """Create DCN subscription."""
            body = infra.get_file_content(
                f"{TEMPLATE_FOLDER}/{RESTCONF_SUBSCRIBE_DATA}"
            )
            uri = restconf.generate_uri(
                "sal-remote:create-data-change-event-subscription", "rpc"
            )
            resp = templated_requests.post_to_uri(
                uri=uri, headers=XML_MESSAGE_HEADERS, data=body
            )
            self.log_response(resp)
            assert resp.status_code in templated_requests.ALLOWED_STATUS_CODES
            root = ET.fromstring(resp.content)
            ns = {
                "remote": "urn:opendaylight:params:xml:ns:yang:controller:md:sal:remote"
            }
            stream_name = root.find(".//remote:stream-name", namespaces=ns).text
            RFC8040_DCN_STREAM_URI = f"{RFC8040_STREAMS_URI}/stream={stream_name}"
            log.info(f"{RFC8040_DCN_STREAM_URI=}")
            self.RFC8040_DCN_STREAM_URI = RFC8040_DCN_STREAM_URI

        with allure_step_with_separate_logging("step_subscribe_to_dcn_stream"):
            """Subscribe to DCN streams."""
            resp = templated_requests.get_from_uri(
                self.RFC8040_DCN_STREAM_URI, headers=XML_MESSAGE_HEADERS
            )
            self.log_response(resp)
            assert resp.status_code in templated_requests.ALLOWED_STATUS_CODES
            new_root = ET.fromstring(resp.content)
            ns = {"monitoring": "urn:ietf:params:xml:ns:yang:ietf-restconf-monitoring"}
            STREAM_LOCATION = (
                new_root.findall(".//monitoring:access", namespaces=ns)[1]
                .find("monitoring:location", namespaces=ns)
                .text
            )
            log.info(f"{STREAM_LOCATION=}")
            self.STREAM_LOCATION = STREAM_LOCATION

        with allure_step_with_separate_logging("step_list_dcn_streams"):
            """List DCN streams."""
            resp = templated_requests.get_from_uri(
                self.RFC8040_DCN_STREAM_URI, headers=XML_MESSAGE_HEADERS
            )
            self.log_response(resp)
            assert resp.status_code in templated_requests.ALLOWED_STATUS_CODES
            # Stream only shows in RFC URL.
            assert STREAM_LOCATION in resp.text

        with allure_step_with_separate_logging("step_start_receiver"):
            """Start the WSS/SSE listener."""
            infra.shell(
                f"python3 tools/wstools/ssereceiver.py --uri {STREAM_LOCATION} "
                f"--logfile {RECEIVER_LOG_FILE}",
                run_in_background=True,
            )
            time.sleep(2)
            output = infra.get_file_content(RECEIVER_LOG_FILE)
            log.info(f"{output=}")

        with allure_step_with_separate_logging("step_change_ds_config"):
            """Make a change in DS configuration."""
            body = infra.get_file_content(f"{TEMPLATE_FOLDER}/{RESTCONF_CONFIG_DATA}")
            uri = f"{RESTCONF_ROOT}/data/network-topology:network-topology"
            resp = templated_requests.put_to_uri_request(
                uri=uri, headers=XML_MESSAGE_HEADERS, data=body
            )
            self.log_response(resp)
            assert resp.status_code in templated_requests.ALLOWED_STATUS_CODES

            uri = (
                f"{RESTCONF_ROOT}/data/network-topology:network-topology"
                "/topology=netconf-notif"
            )
            resp = templated_requests.delete_from_uri_request(uri=uri)
            self.log_response(resp)
            assert resp.status_code in templated_requests.ALLOWED_STATUS_CODES

        with allure_step_with_separate_logging("step_check_notification"):
            """Check the WSS/SSE listener log for a change notification."""
            rc, notification = infra.shell(f"cat {RECEIVER_LOG_FILE}")
            log.info(f"Notification: {notification}")
            self.notification = notification
            assert "<notification xmlns=" in self.notification
            assert "<eventTime>" in self.notification
            assert "<data-changed-notification xmlns=" in self.notification
            assert "<operation>created</operation>" in self.notification
            assert "</data-change-event>" in self.notification
            assert "</data-changed-notification>" in self.notification
            assert "</notification>" in self.notification

        with allure_step_with_separate_logging("step_check_delete_notification"):
            """Check the WSS/SSE listener log for a delete notification."""
            assert "<operation>deleted</operation>" in self.notification

        with allure_step_with_separate_logging("step_check_bug_3934"):
            """Check the WSS/SSE listener log for the bug correction."""
            with utils.report_known_bug_on_failure("3934"):
                data = infra.get_file_content(
                    f"{TEMPLATE_FOLDER}/{RESTCONF_CONFIG_DATA}"
                )
                log.info(f"{data=}")
                log.info(f"Notification: {self.notification}")
                packed_data = data.replace("\n", "")
                packed_data = packed_data.replace(" ", "")
                packed_notification = notification.replace("\n", "")
                packed_notification = packed_notification.replace("\\n", "")
                packed_notification = packed_notification.replace(" ", "")
                assert packed_data in packed_notification
