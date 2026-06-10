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

    NETCONF_READY = 1
    NORTHBOUND = 2
    CALLHOME = 3
    NOTIFICATIONS = 4
    KEY_AUTH = 5
    CRUD = 6
    CRUD_ACTION = 7
    APIDOCS = 8
    RESTPERFCLIENT_MDSAL = 9
