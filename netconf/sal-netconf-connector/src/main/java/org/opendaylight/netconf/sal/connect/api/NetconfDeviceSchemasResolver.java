/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.connect.api;

import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.netconf.sal.NetconfDeviceRpc;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

/**
 * Factory for netconf device schemas.
 */
public interface NetconfDeviceSchemasResolver {
    NetconfDeviceSchemas resolve(
            NetconfDeviceRpc deviceRpc, NetconfSessionPreferences remoteSessionCapabilities, RemoteDeviceId id,
            SchemaContext schemaContext);
}
