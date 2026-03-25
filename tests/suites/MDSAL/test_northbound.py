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
from libraries.NetconfSSH import NetconfSSH
from libraries import utils
from libraries.variables import variables

ODL_NETCONF_MDSAL_PORT = variables.ODL_NETCONF_MDSAL_PORT
ODL_NETCONF_PASSWORD = variables.ODL_NETCONF_PASSWORD
ODL_NETCONF_PROMPT = variables.ODL_NETCONF_PROMPT
ODL_NETCONF_USER = variables.ODL_NETCONF_USER

DATADIR = "variables/netconf/MDSAL"
DATAEXT = "msg"

log = logging.getLogger(__name__)


@pytest.mark.usefixtures("preconditions")
@pytest.mark.usefixtures("log_test_suite_start_end_to_karaf")
@pytest.mark.usefixtures("log_test_case_start_end_to_karaf")
@pytest.mark.run(order=1)
class TestNorthbound:

    ssh_netconf_pid = None

    def get_data(self, name: str) -> str:
        """Load the specified data from the data directory and return it.

        Args:
            name (str): The name of the data file (without extension).

        Returns:
            str: The content of the loaded data file.
        """
        data = infra.get_file_content(f"{DATADIR}/{name}.{DATAEXT}")
        return data

    def reopen_odl_netconf_connection(self) -> str:
        """Reopen a closed netconf connection.

        Returns:
            str: The hello message received from the Netconf server upon connection.
        """
        self.netconf_ssh = NetconfSSH(
            host="127.0.0.1",
            port=ODL_NETCONF_MDSAL_PORT,
            user=ODL_NETCONF_USER,
            password=ODL_NETCONF_PASSWORD,
        )
        hello = self.netconf_ssh.connect()

        return hello

    def open_odl_netconf_conenction(self) -> str:
        """Open a prepared netconf connection.

        Returns:
            str: The hello message received from the Netconf server.
        """
        hello = self.reopen_odl_netconf_connection()
        hello_message = self.get_data("hello")
        self.transmit_message(hello_message)
        return hello

    def transmit_message(self, message: str):
        """Transmit message to Netconf connection and discard the echo
        of the message.

        Args:
            message (str): The payload message to transmit.

        Returns:
            None
        """
        self.netconf_ssh.write(message + ODL_NETCONF_PROMPT)

    def send_message(self, message: str):
        """Send message to Netconf connection and get the reply.

        Args:
            message (str): The payload message to transmit.

        Returns:
            str: The reply read from the Netconf connection until the prompt.
        """
        self.transmit_message(message)
        reply = self.netconf_ssh.read_until_prompt()

        return reply

    def prepare_for_search(self, searched_string: str) -> str:
        """Prepare the specified string for searching in Netconf connection replies.
        The string passed to this keyword is coming from a data
        file which has different end of line conventions than
        the actual Netconf reply. This keyword patches the string
        to match what Netconf actually returns.

        Args:
            searched_string (str): The string to format.

        Returns:
            str: The correctly formatted string with patched line endings.
        """
        result = "\\r\\n".join(searched_string.split("\\n"))

        return result

    def load_and_send_message(self, name: str) -> str:
        """Load a message from the data file set, send it to Netconf and return
        the reply.

        Args:
            name (str): The base name of the request data file.

        Returns:
            str: The reply received from the Netconf connection.
        """
        request = self.get_data(name + "-request")
        reply = self.send_message(request)

        return reply

    def load_expected_reply(self, name: str) -> str:
        """Load the expected reply from the data file set and return it.

        Args:
            name (str): The base name of the expected reply data file.

        Returns:
            str: The content of the expected reply data file.
        """
        expected_reply = self.get_data(name + "-reply")

        return expected_reply

    def abort_odl_netconf_connection(self):
        """Correctly close the Netconf connection and make sure it is really dead.

        Returns:
            None
        """
        if not self.ssh_netconf_pid:
            return

        infra.shell("kill " + self.ssh_netconf_pid)
        self.ssh_netconf_pid = None

    def close_odl_netconf_connection_gracefully(self):
        """Close the session cleanly through a standard Netconf test message
        and then abort the connection.

        Returns:
            None
        """
        self.perform_test("close-session")
        self.abort_odl_netconf_connection()

    def teardown_everything(self):
        """Standard teardown operation to abort connection.

        Returns:
            None
        """
        self.abort_odl_netconf_connection()

    def check_first_batch_data(self, reply, function):
        """Check the first batch of XML data against a specified evaluation function.

        Args:
            reply (str): The Netconf reply string to evaluate.
            function (Callable): The assertion function
                (e.g., contains or does_not_contain) to execute against the data.

        Returns:
            None
        """
        function(reply, "<id>TOY001</id>")
        function(reply, "<id>CUST001</id>")
        function(reply, "<car-id>TOY001</car-id>")
        function(reply, "<person-id>CUST001</person-id>")

    def check_first_batch_data_present(self, reply):
        """Verify the first batch of data is present in the reply.

        Args:
            reply (str): The Netconf reply to evaluate.

        Returns:
            None
        """

        def contains(reply, expected_text):
            assert expected_text in reply

        self.check_first_batch_data(reply, contains)

    def check_first_batch_data_not_present(self, reply):
        """Verify the first batch of data is absent from the reply.

        Args:
            reply (str): The Netconf reply to evaluate.

        Returns:
            None
        """

        def does_not_contain(reply, expected_text):
            assert expected_text not in reply

        self.check_first_batch_data(reply, does_not_contain)

    def check_second_batch_data(self, reply, function):
        """Check the second batch of XML data against a specified evaluation function.

        Args:
            reply (str): The Netconf reply string to evaluate.
            function (Callable): The assertion function to execute against the data.

        Returns:
            None
        """
        function(reply, "<id>OLD001</id>")
        function(reply, "<id>CUST002</id>")
        function(reply, "<car-id>OLD001</car-id>")
        function(reply, "<person-id>CUST002</person-id>")

    def check_second_batch_data_present(self, reply):
        """Verify the second batch of data is present in the reply.

        Args:
            reply (str): The Netconf reply to evaluate.

        Returns:
            None
        """

        def contains(reply, expected_text):
            assert expected_text in reply

        self.check_second_batch_data(reply, contains)

    def check_multiple_batch_data(self, reply, function):
        """Check a large multiple-batch of XML data against a specified evaluation
        function.

        Args:
            reply (str): The Netconf reply string to evaluate.
            function (Callable): The assertion function to execute against the data.

        Returns:
            None
        """
        function(reply, "<id>CAROLD</id>")
        function(reply, "<id>CUSTOLD</id>")
        function(reply, "<car-id>CAROLD</car-id>")
        function(reply, "<person-id>CUSTOLD</person-id>")
        function(reply, "<id>CARYOUNG</id>")
        function(reply, "<id>CUSTYOUNG</id>")
        function(reply, "<car-id>CARYOUNG</car-id>")
        function(reply, "<person-id>CUSTYOUNG</person-id>")
        function(reply, "<id>CARMID</id>")
        function(reply, "<id>CUSTMID</id>")
        function(reply, "<car-id>CARMID</car-id>")
        function(reply, "<person-id>CUSTMID</person-id>")
        function(reply, "<id>CAROLD2</id>")
        function(reply, "<id>CUSTOLD2</id>")
        function(reply, "<car-id>CAROLD2</car-id>")
        function(reply, "<person-id>CUSTOLD2</person-id>")

    def check_multiple_batch_data_absent(self, reply):
        """Verify the multiple batch data elements are absent from the reply.

        Args:
            reply (str): The Netconf reply to evaluate.

        Returns:
            None
        """

        def contains(reply, expected_text):
            assert expected_text not in reply

        self.check_multiple_batch_data(reply, contains)

    def check_multiple_batch_data_present(self, reply):
        """Verify the multiple batch data elements are present in the reply.

        Args:
            reply (str): The Netconf reply to evaluate.

        Returns:
            None
        """

        def does_not_contain(reply, expected_text):
            assert expected_text in reply

        self.check_multiple_batch_data(reply, does_not_contain)

    def check_axuluriary_data(self, reply, function):
        """Check auxiliary data objects against a specified evaluation function.

        Args:
            reply (str): The Netconf reply string to evaluate.
            function (Callable): The assertion function to execute.

        Returns:
            None
        """
        function(reply, "<id>CUSTBAD</id>")
        function(reply, "<id>test</id>")

    def check_test_object_absent(self, reply):
        """Check that none of the testing objects exist in the provided reply.

        Args:
            reply (str): The Netconf reply to evaluate.

        Returns:
            None
        """
        self.check_first_batch_data_not_present(reply)

        def does_not_contain(reply, expected_text):
            assert expected_text not in reply

        self.check_second_batch_data(reply, does_not_contain)
        self.check_multiple_batch_data_absent(reply)
        self.check_axuluriary_data(reply, does_not_contain)
        does_not_contain(reply, "<id>test</id>")

    def check_test_object_not_present_in_config(self, name) -> str:
        """Use dataset with the specified name to get the configuration and check
        that none of our test objects are there.

        Args:
            name (str): The base name of the request data file to load and send.

        Returns:
            str: The evaluated Netconf reply.
        """
        reply = self.load_and_send_message(name)
        self.check_test_object_absent(reply)

        def does_not_contain(reply, expected_text):
            assert expected_text not in reply

        does_not_contain(reply, "<id>REPLACE</id>")

        return reply

    def perform_test(self, name: str) -> str:
        """Load and send the request from the dataset and compare the returned reply
        to the one stored in the dataset.

        Args:
            name (str): The base name of the request and expected reply data files.

        Returns:
            str: The actual formatted response returned by the server.
        """
        actual = self.load_and_send_message(name)
        expected = self.load_expected_reply(name)
        actual = actual.replace("]]>]]>", "").strip()
        utils.verify_xmls_match(
            actual, expected, "Actual response", "Expected response"
        )

        return actual

    def send_and_check(self, name: str, expected: str):
        """Send a configured message and strictly assert the reply matches expected.

        Args:
            name (str): The base name of the request data file.
            expected (str): The precise expected string reply.

        Returns:
            None
        """
        actual = self.load_and_send_message(name)
        assert expected == actual

    @allure.description(
        textwrap.dedent(
            """
            **Metconf MDSAL Northbound test suite**

            The request produced by test cases "Get Config Running", "Get Config \
            Running To Confirm No_Edit Before Commit", "Get Config Running To Confirm \
            Delete After Commit" and "Get Config Candidate To Confirm Discard" all use \
            the same message id ("empty") for their requests. This is possible because \
            the requests produced by this suite are strictly serialized. The RFC 6241 \
            does not state that these IDs are unique, it only requires that each ID \
            used is "XML attribute normalized" if the client wants it to be returned \
            unmodified. The RFC specifically says that "the content of this attribute \
            is not interpreted in any way, it only is stored to be returned with \
            the reply to the request. The reuse of the "empty" string for the 4 test \
            cases was chosen for simplicity.

            **TODO:** Change the 4 testcases to use unique message IDs.

            **TODO:** There are many sections with too many "Should_[Not_]Contain" \
            keyword invocations (see Check_Multiple_Modules_Merge_Replace for \
            a particularly bad example). Create a resource that will be able \
            to extract the data from them requests and search for them in \
            the response, then convert to usage of thismresource (think "Thou \
            shall not repeat yourself"). The following resource was found when doing \
            research on this:
            *http://robotframework.org/robotframework/latest/libraries/XML.html*
        """
        )
    )
    def test_northbound(self, allure_step_with_separate_logging):

        with allure_step_with_separate_logging("step_connect_to_ODL_netconf"):
            """Connect to ODL Netconf and fail if that is not possible."""
            self.open_odl_netconf_conenction()

        with allure_step_with_separate_logging("step_get_config_running"):
            """Make sure the configuration has only the default elements in it."""
            self.check_test_object_not_present_in_config("get-config")

        with allure_step_with_separate_logging("step_missing_message_id_attribute"):
            """Check that messages with missing "message-ID" attribute are rejected
            with the correct error (RFC 6241, section 4.1)."""
            self.perform_test("missing-attr")

        with allure_step_with_separate_logging("step_additional_attributes_in_message"):
            """Check that additional attributes in messages are returned properly
            (RFC 6241, sections 4.1 and 4.2)."""
            reply = self.load_and_send_message("additional-attr")
            assert 'xmlns="urn:ietf:params:xml:ns:netconf:base:1.0"' in reply
            assert 'message-id="1"' in reply
            assert 'attribute="something"' in reply
            assert 'additional="otherthing"' in reply
            assert (
                'xmlns:prefix="http://www.example.com/my-schema-example.html"' in reply
            )

        with allure_step_with_separate_logging(
            "step_send_stuff_in_undefined_namespace"
        ):
            """Try to send something within an undefined namespace and check the reply
            complains about the nonexistent namespace and element."""
            reply = self.load_and_send_message("merge-nonexistent-namespace")
            assert (
                "java.lang.NullPointerException" not in reply
            ), "Failed on know bug: 5125"
            assert "urn:this:is:in:a:nonexistent:namespace" in reply
            assert "<rpc-error>" in reply

        with allure_step_with_separate_logging("step_edit_config_first_batch_merge"):
            """Request a "merge" operation adding an element in candidate configuration
            and check the reply."""
            self.perform_test("merge-1")

        with allure_step_with_separate_logging(
            "step_get_config_running_to_confirm_no_edit_before_commit"
        ):
            """Make sure the running configuration is still unchanged as the change
            was not commited yet."""
            self.check_test_object_not_present_in_config(
                "get-config-no-edit-before-commit"
            )

        with allure_step_with_separate_logging("step_commit_edit"):
            """Commit the change and check the reply."""
            self.perform_test("commit-edit")

        with allure_step_with_separate_logging(
            "step_first_batch_in_config_running_to_confirm_edit_after_commit"
        ):
            """Check that the change is now in the configuration."""
            reply = self.load_and_send_message("get-config-edit-after-commit")
            self.check_first_batch_data_present(reply)

        with allure_step_with_separate_logging("step_terminate_connection_gracefully"):
            """Close the session and disconnect."""
            self.close_odl_netconf_connection_gracefully()

        with allure_step_with_separate_logging(
            "step_reconnect_to_odl_netconf_after_graceful_session_end"
        ):
            """Close the session and disconnect."""
            self.open_odl_netconf_conenction()

        with allure_step_with_separate_logging(
            "step_first_batch_in_config_running_after_reconnect"
        ):
            """Check that the change is now in the configuration."""
            reply = self.load_and_send_message("get-config-edit-after-commit")
            self.check_first_batch_data_present(reply)

        with allure_step_with_separate_logging(
            "step_edit_config_create_shall_fail_now"
        ):
            """Request a "create" operation of an element that already exists and check
            that it fails with the correct error
            (RFC 6241, section 7.2, operation "create")."""
            self.perform_test("create")

        with allure_step_with_separate_logging("step_delete_first_batch"):
            """Delete the created element from the candidate configuration and check
            the reply."""
            self.perform_test("delete")

        with allure_step_with_separate_logging(
            "step_get_config_running_to_confirm_no_delete_before_commit"
        ):
            """Make sure the element is still present in the running configuration
            as the delete command was not committed yet."""
            reply = self.load_and_send_message("get-config-no-delete-before-commit")
            self.check_first_batch_data_present(reply)

        with allure_step_with_separate_logging("step_commit_delete"):
            """Commit the deletion of the element and check the reply."""
            self.perform_test("commit-delete")

        with allure_step_with_separate_logging(
            "step_get_config_running_to_confirm_delete_after_commit"
        ):
            """Check that the element is gone."""
            self.check_test_object_not_present_in_config(
                "get-config-delete-after-commit"
            )

        with allure_step_with_separate_logging("step_commit_no_transaction"):
            """Attempt to perform "commit" when there are no changes in the candidate
            configuration and check that it returns OK status."""
            self.perform_test("commit-no-transaction")
            # report_failure_due_to_bug()

        with allure_step_with_separate_logging("step_edit_config_second_batch_merge"):
            """Create an element to be discarded and check the reply."""
            self.perform_test("merge-2")

        with allure_step_with_separate_logging(
            "step_get_and_check_config_candidate_for_discard"
        ):
            """Check that the element to be discarded is present in the candidate
            configuration."""
            reply = self.load_and_send_message("get-config-candidate")
            self.check_first_batch_data_not_present(reply)
            self.check_second_batch_data_present(reply)

        with allure_step_with_separate_logging(
            "step_discard_changes_using_discard_request"
        ):
            """Ask the server to discard the candidate and check the reply."""
            self.perform_test("commit-discard")

        with allure_step_with_separate_logging(
            "step_get_and_check_config_candidate_to_confirm_discard"
        ):
            """Check that the element was really discarded."""
            self.check_test_object_not_present_in_config("get-config-candidate-discard")

        with allure_step_with_separate_logging(
            "step_edit_config_mutliple_batch_merge_create"
        ):
            """Use a create request with the third batch to create
            the infrastructure."""
            self.perform_test("merge-multiple-create")

        with allure_step_with_separate_logging(
            "step_edit_config_mutliple_batch_merge_third"
        ):
            """Use a create request with the third batch to create
            the infrastructure."""
            self.perform_test("merge-multiple-1")

        with allure_step_with_separate_logging(
            "step_edit_config_mutliple_batch_merge_fourth"
        ):
            """Use a merge request with the third batch to create the infrastructure."""
            self.perform_test("merge-multiple-2")

        with allure_step_with_separate_logging(
            "step_edit_config_mutliple_batch_merge_fifth"
        ):
            """Add a "name4" subelement to the element and check the reply."""
            self.perform_test("merge-multiple-3")

        with allure_step_with_separate_logging("step_commit_multiple_merge"):
            """Commit the changes and check the reply."""
            self.perform_test("merge-multiple-commit")

        with allure_step_with_separate_logging(
            "step_multiple_batch_data_in_running_config"
        ):
            """Check that the 3 subelements are now present in the running
            configuration."""
            reply = self.load_and_send_message("merge-multiple-check")
            self.check_multiple_batch_data_present(reply)

        with allure_step_with_separate_logging(
            "step_abbort_connection_to_simulate_session_failure"
        ):
            """Simulate session failure by disconnecting without terminating
            the session."""
            self.abort_odl_netconf_connection()

        with allure_step_with_separate_logging(
            "step_reconnect_to_odl_netconf_after_session_failure"
        ):
            """Reconnect to ODL Netconf and fail if that is not possible."""
            self.open_odl_netconf_conenction()

        with allure_step_with_separate_logging(
            "step_mutlitple_batch_data_in_running_config_after_session_failure"
        ):
            """Check that the 3 subelements are now present in the running
            configuration."""
            reply = self.load_and_send_message("merge-multiple-check")
            self.check_multiple_batch_data_present(reply)

        with allure_step_with_separate_logging("step_edit_multiple_merge_data"):
            """Add another subelement named "test" to the element and check
            the reply."""
            self.perform_test("merge-multiple-edit")

        with allure_step_with_separate_logging(
            "step_commit_mutltiple_modules_merge_edit"
        ):
            """Commit the addition of the "test" subelement and check the reply."""
            self.perform_test("merge-multiple-edit-commit")

        with allure_step_with_separate_logging(
            "step_check_mutltiple_modules_merge_edit"
        ):
            """Check that the "test" subelement exists and has correct value for
            "port" subelement."""
            reply = self.load_and_send_message("merge-multiple-edit-check")
            assert "<id>test</id>" in reply
            assert "<model>Dixi</model>" in reply
            assert "<manufacturer>BMW</manufacturer>" in reply
            assert "<year>1928</year>" in reply

        with allure_step_with_separate_logging("step_update_multiple_modules_merge"):
            """Update the value of the "port" subelement of the "test" subelement
            and check the reply."""
            self.perform_test("merge-multiple-update")

        with allure_step_with_separate_logging(
            "step_commit_multiple_modules_merge_update"
        ):
            """Commit the update and check the reply."""
            self.perform_test("merge-multiple-update-commit")

        with allure_step_with_separate_logging(
            "step_check_multiple_modules_merge_update"
        ):
            """Check that the value of the "port" was really updated."""
            reply = self.load_and_send_message("merge-multiple-update-check")
            assert "<id>test</id>" in reply
            assert "<model>Bentley Speed Six</model>" in reply
            assert "<manufacturer>Bentley</manufacturer>" in reply
            assert "<year>1930</year>" in reply
            assert "<model>Dixi</model>" not in reply
            assert "<manufacturer>BMW</manufacturer>" not in reply
            assert "<year>1928</year>" not in reply

        with allure_step_with_separate_logging("step_replace_multiple_modules_merge"):
            """Replace the content of the "test" with another completely different
            and check the reply."""
            self.perform_test("merge-multiple-replace")

        with allure_step_with_separate_logging(
            "step_commit_multiple_modules_merge_replace"
        ):
            """Commit the replace and check the reply."""
            self.perform_test("merge-multiple-replace-commit")

        with allure_step_with_separate_logging(
            "step_check_multiple_modules_merge_replace"
        ):
            """Check that the new content is there and the old content is gone."""
            reply = self.load_and_send_message("merge-multiple-replace-check")
            assert "<id>REPLACE</id>" in reply
            assert "<manufacturer>FIAT</manufacturer>" in reply
            assert "<model>Panda</model>" in reply
            assert "<year>2003</year>" in reply
            assert "<car-id>REPLACE</car-id>" in reply
            assert "<id>TOY001</id>" not in reply
            assert "<id>CUST001</id>" not in reply
            assert "<car-id>TOY001</car-id>" not in reply
            assert "<person-id>CUST001</person-id>" not in reply
            assert "<id>OLD001</id>" not in reply
            assert "<id>CUST002</id>" not in reply
            assert "<car-id>OLD001</car-id>" not in reply
            assert "<person-id>CUST002</person-id>" not in reply
            assert "<id>CAROLD</id>" not in reply
            assert "<id>CUSTOLD</id>" in reply
            assert "<car-id>CAROLD</car-id>" not in reply
            assert "<person-id>CUSTOLD</person-id>" not in reply
            assert "<id>CARYOUNG</id>" not in reply
            assert "<id>CUSTYOUNG</id>" in reply
            assert "<car-id>CARYOUNG</car-id>" not in reply
            assert "<person-id>CUSTYOUNG</person-id>" in reply
            assert "<id>CARMID</id>" not in reply
            assert "<id>CUSTMID</id>" in reply
            assert "<car-id>CARMID</car-id>" not in reply
            assert "<person-id>CUSTMID</person-id>" not in reply
            assert "<id>CAROLD2</id>" not in reply
            assert "<id>CUSTOLD2</id>" in reply
            assert "<car-id>CAROLD2</car-id>" not in reply
            assert "<person-id>CUSTOLD2</person-id>" not in reply
            assert "<id>CUSTBAD</id>" not in reply
            assert "<id>test</id>" not in reply

        with allure_step_with_separate_logging("step_remove_test_data"):
            """Remove the testing elements and all their subelements and check
            the reply."""
            self.perform_test("merge-multiple-remove")

        with allure_step_with_separate_logging("step_commit_test_data_removal"):
            """Commit the removal and check the reply."""
            self.perform_test("merge-multiple-remove-commit")

        with allure_step_with_separate_logging("step_connector_simpliefied_pattern"):
            """Several requests in a (simplified) pattern typical for requests from
            netconf-connector."""
            self.perform_test("none-replace")
            self.perform_test("commit-edit")
            self.perform_test("delete")
            self.perform_test("commit-edit")

        with allure_step_with_separate_logging("step_test_bug_7791"):
            """Send (checking replies) series of netconf messages to trigger
            https://bugs.opendaylight.org/show_bug.cgi?id=7791"""
            with utils.report_known_bug_on_failure("7791"):
                self.perform_test("bug7791-1")
                self.perform_test("bug7791-2")
                self.perform_test("commit-edit")
                self.perform_test("delete")
                self.perform_test("commit-edit")

        with allure_step_with_separate_logging("step_delete_not_existing_element"):
            """Attempt to delete the elements again and check that it fails with
            the correct error."""
            self.perform_test("delete-not-existing")

        with allure_step_with_separate_logging(
            "step_commit_delete_not_existing_module"
        ):
            """Attempt to commit and check the reply."""
            with utils.report_known_bug_on_failure("4455"):
                self.perform_test("commit-no-transaction")

        with allure_step_with_separate_logging("step_remove_not_existing_module"):
            """Attempt to remove the "module" element again and check that
            the operation is "silently ignored"."""
            self.perform_test("remove-not-existing")

        with allure_step_with_separate_logging(
            "step_commit_remove_not_existing_module"
        ):
            """Attempt to commit and check the reply."""
            with utils.report_known_bug_on_failure("4455"):
                self.perform_test("remove-not-existing-commit")

        with allure_step_with_separate_logging("step_close_session"):
            """Close the session and check that it was closed properly."""
            self.perform_test("close-session")
