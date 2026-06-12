#
# Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License v1.0 which accompanies this distribution,
# and is available at http://www.eclipse.org/legal/epl-v10.html
#

from enum import IntEnum


class SuiteOrder(IntEnum):
    """Defines the execution order of all test suites.

    Suites are run in ascending order by pytest-ordering.
    Update this file when adding a new suite or changing the run sequence.
    """

    NORTHBOUND = 1
    CALLHOME = 2
    NOTIFICATIONS = 3
    KEY_AUTH = 4
    CRUD = 5
    CRUD_ACTION = 6
    APIDOCS = 7
    RESTPERFCLIENT_MDSAL = 8
