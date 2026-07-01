#
# Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License v1.0 which accompanies this distribution,
# and is available at http://www.eclipse.org/legal/epl-v10.html
#

from enum import IntEnum, auto


class SuiteOrder(IntEnum):
    """Defines the execution order of all test suites.

    Suites are run in ascending order by pytest-ordering.
    Add new entries at the desired position in the sequence.
    """

    NETCONF_READY = auto()
    NORTHBOUND = auto()
    CALLHOME = auto()
    NOTIFICATIONS = auto()
    KEY_AUTH = auto()
    CRUD = auto()
    CRUD_ACTION = auto()
    APIDOCS = auto()
    RESTPERFCLIENT_MDSAL = auto()
    RESTPERFCLIENT_PERFORMANCE = auto()
    GETMULTI = auto()
    GETSINGLE = auto()
