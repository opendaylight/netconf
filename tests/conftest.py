#
# Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License v1.0 which accompanies this distribution,
# and is available at http://www.eclipse.org/legal/epl-v10.html
#

import logging

import pytest

import controller_testlib.fixtures
from controller_testlib import infra
from netconf_testlib.variables import variables

ODL_IP = variables.ODL_IP
TOOLS_IP = variables.TOOLS_IP
KARAF_LOG_LEVEL = variables.KARAF_LOG_LEVEL
ODL_FEATRUES = [
    "odl-infrautils-ready",
    "odl-restconf-nb",
    "odl-netconf-mdsal",
    "odl-restconf-openapi",
    "odl-clustering-test-app",
    "odl-netconf-topology",
    "odl-netconf-callhome-ssh"
]

log = logging.getLogger(__name__)


def pytest_addoption(parser):
    """Adds custom command-line options to pytest."""
    parser.addoption(
        "--step-include",
        action="store",
        default=None,
        help="Comma-separated list of step tags to run",
    )
    parser.addoption(
        "--step-exclude",
        action="store",
        default=None,
        help="Comma-separated list of step tags to skip",
    )


allure_step_with_separate_logging = (
    controller_testlib.fixtures.allure_step_with_separate_logging
)
step_tag_checker = controller_testlib.fixtures.step_tag_checker
log_test_suite_start_end_to_karaf = (
    controller_testlib.fixtures.log_test_suite_start_end_to_karaf
)
log_test_case_start_end_to_karaf = (
    controller_testlib.fixtures.log_test_case_start_end_to_karaf
)
preconditions = controller_testlib.fixtures.make_preconditions_fixture(
    ODL_FEATRUES, karaf_log_level=KARAF_LOG_LEVEL
)


@pytest.fixture(scope="class")
def teardown_kill_all_running_ssereceiver_processes():
    """Fixture to stop ssereceiver instaces at the end of test class execution

    Args:
        None

    Returns:
        None
    """
    yield
    infra.shell(
        (
            r"pkill -f '^(.*/)?python3?\s+.*ssereceiver.py' || "
            r"echo 'No running instance of ssereceiver.py script.'"
        )
    )
