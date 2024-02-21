/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.api;

import com.google.common.util.concurrent.ListenableFuture;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

/**
 * Factory for netconf device schemas.
 */
public interface NetconfDeviceSchemasResolver {
    // FIXME: document this method
    ListenableFuture<? extends NetconfDeviceSchemas> resolve(NetconfRpcService deviceRpc,
        NetconfSessionPreferences remoteSessionCapabilities, RemoteDeviceId id, EffectiveModelContext schemaContext);
}
