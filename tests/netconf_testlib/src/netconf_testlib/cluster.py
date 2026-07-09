#
# Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License v1.0 which accompanies this distribution,
# and is available at http://www.eclipse.org/legal/epl-v10.html
#

from controller_testlib.karaf import is_datastore_ready


def is_netconf_mount_datastore_ready(shard_status: dict) -> bool:
    """Check whether the datastore shard backing NETCONF mount points is ready.

    NETCONF mount points are persisted in the MD-SAL datastore; a mount point
    can't be considered usable until its backing shard has completed Raft
    leader election. Delegates that check to controller_testlib, since shard
    leadership is a controller/MD-SAL concept, not a NETCONF one.

    Args:
        shard_status (dict): Parsed JSON body of a shard status query (e.g.
            via the cluster-admin RESTCONF API or the ShardManager JMX/jolokia
            MBean).

    Returns:
        bool: True if the backing shard has an elected leader, False otherwise.
    """
    return is_datastore_ready(shard_status)
