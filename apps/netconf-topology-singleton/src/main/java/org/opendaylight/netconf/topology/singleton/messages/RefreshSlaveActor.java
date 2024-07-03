/*
 * Copyright (c) 2017 Pantheon Technologies s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.messages;

import static java.util.Objects.requireNonNull;

import org.apache.pekko.util.Timeout;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologySetup;

public class RefreshSlaveActor {
    private final RemoteDeviceId id;
    private final NetconfTopologySetup setup;
    private final Timeout actorResponseWaitTime;

    public RefreshSlaveActor(final NetconfTopologySetup setup, final RemoteDeviceId id,
            final Timeout actorResponseWaitTime) {
        this.setup = requireNonNull(setup);
        this.id = requireNonNull(id);
        this.actorResponseWaitTime = actorResponseWaitTime;
    }

    public Timeout getActorResponseWaitTime() {
        return actorResponseWaitTime;
    }

    public RemoteDeviceId getId() {
        return id;
    }

    public NetconfTopologySetup getSetup() {
        return setup;
    }
}
