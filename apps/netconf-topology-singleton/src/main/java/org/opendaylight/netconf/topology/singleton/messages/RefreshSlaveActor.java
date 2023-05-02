/*
 * Copyright (c) 2017 Pantheon Technologies s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.messages;

import akka.util.Timeout;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologySetup;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaRepository;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceRegistry;

public class RefreshSlaveActor {
    private final SchemaRepository schemaRepository;
    private final RemoteDeviceId id;
    private final SchemaSourceRegistry schemaRegistry;
    private final NetconfTopologySetup setup;
    private final Timeout actorResponseWaitTime;

    public RefreshSlaveActor(final NetconfTopologySetup setup, final RemoteDeviceId id,
                             final Timeout actorResponseWaitTime) {
        this.setup = setup;
        this.id = id;
        schemaRegistry = setup.getSchemaResourcesDTO().getSchemaRegistry();
        schemaRepository = setup.getSchemaResourcesDTO().getSchemaRepository();
        this.actorResponseWaitTime = actorResponseWaitTime;
    }

    public Timeout getActorResponseWaitTime() {
        return actorResponseWaitTime;
    }

    public SchemaRepository getSchemaRepository() {
        return schemaRepository;
    }

    public RemoteDeviceId getId() {
        return id;
    }

    public SchemaSourceRegistry getSchemaRegistry() {
        return schemaRegistry;
    }

    public NetconfTopologySetup getSetup() {
        return setup;
    }
}
