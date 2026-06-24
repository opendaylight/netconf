#
# Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License v1.0 which accompanies this distribution,
# and is available at http://www.eclipse.org/legal/epl-v10.html
#
# Based on the original Robot Framework integration test:
# https://github.com/opendaylight/integration-test/blob/901c7e139945b436d95a44b3b592904c3d7a4f9f/csit/suites/netconf/apidocs/apidocs.robot
#

import logging
import textwrap

import allure
import pytest

from libraries import templated_requests
from suites.suite_order import SuiteOrder

VAR_DIR = "variables/apidoc"

log = logging.getLogger(__name__)


@pytest.mark.openapi
@pytest.mark.functional
@pytest.mark.smoke
@pytest.mark.single_device
@pytest.mark.usefixtures("preconditions")
@pytest.mark.usefixtures("log_test_suite_start_end_to_karaf")
@pytest.mark.usefixtures("log_test_case_start_end_to_karaf")
@pytest.mark.run(order=SuiteOrder.APIDOCS)
class TestApidocs:

    @allure.description(
            textwrap.dedent(
                """**Test suite to verify that RESTCONF OpenAPI is working.**"""
            )
    )
    def test_get_apidoc_apis(self, allure_step_with_separate_logging):
        with allure_step_with_separate_logging("step_get_apidoc_apis"):
            """Get the Apidoc Apis list, check 200 status and apis string presence."""
            response = templated_requests.get_templated_request(
                f"{VAR_DIR}/openapi_v3", mapping=None, json=True, http_timeout=90
            )
            response_text = response.text
            assert (
                "api" in response_text
            ), f"Expected string 'api' not found in response: {response_text}"
