package org.opendaylight.netconf.topology.singleton.messages;

import akka.util.Timeout;
import java.io.Serializable;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologySetup;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaRepository;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceRegistry;

public class RefreshActor implements Serializable {

    private final SchemaRepository schemaRepository;
    private final RemoteDeviceId id;
    private final SchemaSourceRegistry schemaRegistry;
    private final NetconfTopologySetup setup;
    private final Timeout actorResponseWaitTime;

    public RefreshActor(final NetconfTopologySetup setup, final RemoteDeviceId id,
                        final SchemaSourceRegistry schemaRegistry, final SchemaRepository schemaRepository,
                        final Timeout actorResponseWaitTime) {
        this.setup = setup;
        this.id = id;
        this.schemaRegistry = schemaRegistry;
        this.schemaRepository = schemaRepository;
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
