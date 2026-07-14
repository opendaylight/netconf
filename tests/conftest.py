#
# Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License v1.0 which accompanies this distribution,
# and is available at http://www.eclipse.org/legal/epl-v10.html
#

import pytest

from controller_testlib import infra
from controller_testlib.fixtures import make_preconditions_fixture

from libraries.variables import variables

pytest_plugins = ["controller_testlib.fixtures"]

preconditions = make_preconditions_fixture(
    variables.ODL_FEATURES, karaf_log_level=variables.KARAF_LOG_LEVEL
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
