/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.connect.netconf;

import org.opendaylight.netconf.sal.connect.api.NetconfDeviceSchemas;
import org.opendaylight.netconf.sal.connect.api.NetconfDeviceSchemasResolver;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.netconf.sal.NetconfDeviceRpc;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160409.ModulesState;
import org.opendaylight.yangtools.yang.common.QName;

/**
 * Default implementation resolving schemas QNames from netconf-state or from modules-state.
 */
public final class NetconfStateSchemasResolverImpl implements NetconfDeviceSchemasResolver {
    private static final QName YANG_LIBRARY_CAPABILITY = QName.create(ModulesState.QNAME, "ietf-yang-library").intern();

    @Override
    public NetconfDeviceSchemas resolve(final NetconfDeviceRpc deviceRpc,
                                        final NetconfSessionPreferences remoteSessionCapabilities,
                                        final RemoteDeviceId id) {
        if (remoteSessionCapabilities.isMonitoringSupported()) {
            return NetconfStateSchemas.create(deviceRpc, remoteSessionCapabilities, id);
        }

        // FIXME: I think we should prefer YANG library here
        return remoteSessionCapabilities.containsModuleCapability(YANG_LIBRARY_CAPABILITY)
            ? LibraryModulesSchemas.create(deviceRpc, id) : NetconfStateSchemas.EMPTY;
    }
}
