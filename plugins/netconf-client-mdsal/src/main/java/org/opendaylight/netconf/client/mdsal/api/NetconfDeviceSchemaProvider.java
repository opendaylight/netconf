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
import org.opendaylight.netconf.client.mdsal.NetconfDeviceSchema;

/**
 * A provider of {@link NetconfDeviceSchema}.
 */
@NonNullByDefault
public interface NetconfDeviceSchemaProvider {

    ListenableFuture<NetconfDeviceSchema> deviceNetconfSchemaFor(RemoteDeviceId deviceId,
        NetconfSessionPreferences sessionPreferences, NetconfRpcService deviceRpc, BaseNetconfSchema baseSchema,
        // FIXME: this parameter should not be here
        Executor processingExecutor);
}
