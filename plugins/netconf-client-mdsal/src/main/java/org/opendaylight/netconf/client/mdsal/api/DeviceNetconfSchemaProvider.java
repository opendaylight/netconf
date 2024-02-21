/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.api;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.Executor;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.model.api.source.SourceRepresentation;
import org.opendaylight.yangtools.yang.model.repo.spi.PotentialSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceProvider;

/**
 * A provider of {@link DeviceNetconfSchema}.
 */
@NonNullByDefault
public interface DeviceNetconfSchemaProvider {

    ListenableFuture<DeviceNetconfSchema> deviceNetconfSchemaFor(RemoteDeviceId deviceId,
        NetconfSessionPreferences sessionPreferences, NetconfRpcService deviceRpc, BaseNetconfSchema baseSchema,
        // FIXME: this parameter should not be here
        Executor processingExecutor);

    // FIXME: This supports external URL-based pre-registration of schema sources from topology. This part should really
    //        be catered through deviceNetconfSchemaFor() with the sources being registered only for the duration of
    //        schema assembly
    <T extends SourceRepresentation> Registration registerSchemaSource(SchemaSourceProvider<T> provider,
        PotentialSchemaSource<T> source);
}
