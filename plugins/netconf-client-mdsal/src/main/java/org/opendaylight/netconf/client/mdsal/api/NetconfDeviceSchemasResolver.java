/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.api;

import org.opendaylight.netconf.client.mdsal.spi.NetconfDeviceRpc;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

/**
 * Factory for netconf device schemas.
 */
public interface NetconfDeviceSchemasResolver {
    // FIXME: document this method
    NetconfDeviceSchemas resolve(NetconfDeviceRpc deviceRpc, NetconfSessionPreferences remoteSessionCapabilities,
        RemoteDeviceId id, EffectiveModelContext schemaContext);
}
