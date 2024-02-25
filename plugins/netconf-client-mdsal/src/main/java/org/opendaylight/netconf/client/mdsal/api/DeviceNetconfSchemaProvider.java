/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.api;

import com.google.common.util.concurrent.ListenableFuture;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.yangtools.yang.model.repo.api.EffectiveModelContextFactory;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaRepository;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceRegistry;

/**
 * A provider of {@link DeviceNetconfSchema}.
 */
@NonNullByDefault
public interface DeviceNetconfSchemaProvider {

    ListenableFuture<DeviceNetconfSchema> deviceNetconfSchemaFor(RemoteDeviceId deviceId,
        NetconfSessionPreferences sessionPreferences, NetconfRpcService deviceRpc, BaseNetconfSchema baseSchema);

    // FIXME: These support:
    //        - external URL-based pre-registration of schema sources from topology, which should really be catered
    //          through deviceNetconfSchemaFor() with the sources being registered only for the duration of schema
    //          assembly
    //        - netconf-topology-singleton lifecycle, which needs to be carefully examined
    @Deprecated
    SchemaRepository repository();

    @Deprecated
    SchemaSourceRegistry registry();

    @Deprecated
    EffectiveModelContextFactory contextFactory();
}
