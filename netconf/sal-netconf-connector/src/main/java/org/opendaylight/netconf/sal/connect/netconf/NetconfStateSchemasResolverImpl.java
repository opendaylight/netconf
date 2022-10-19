/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf;

import static java.util.Objects.requireNonNull;

import org.opendaylight.netconf.sal.connect.api.NetconfDeviceSchemas;
import org.opendaylight.netconf.sal.connect.api.NetconfDeviceSchemasResolver;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.netconf.sal.NetconfDeviceRpc;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.$YangModuleInfoImpl;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

/**
 * Default implementation resolving schemas QNames from netconf-state or from modules-state.
 */
public final class NetconfStateSchemasResolverImpl implements NetconfDeviceSchemasResolver {
    private static final QName RFC8525_YANG_LIBRARY_CAPABILITY = $YangModuleInfoImpl.getInstance().getName();
    private static final QName RFC7895_YANG_LIBRARY_CAPABILITY = RFC8525_YANG_LIBRARY_CAPABILITY
        .bindTo(QNameModule.create(RFC8525_YANG_LIBRARY_CAPABILITY.getNamespace(), Revision.of("2016-06-21"))).intern();

    private final LibraryModulesSchemaFactory libraryModulesSchemaFactory;

    public NetconfStateSchemasResolverImpl(final LibraryModulesSchemaFactory libraryModulesSchemaFactory) {
        this.libraryModulesSchemaFactory = requireNonNull(libraryModulesSchemaFactory);
    }

    @Override
    public NetconfDeviceSchemas resolve(final NetconfDeviceRpc deviceRpc,
            final NetconfSessionPreferences remoteSessionCapabilities,
            final RemoteDeviceId id, final EffectiveModelContext schemaContext) {
        // FIXME: I think we should prefer YANG library here
        if (remoteSessionCapabilities.isMonitoringSupported()) {
            return NetconfStateSchemas.create(deviceRpc, remoteSessionCapabilities, id, schemaContext);
        }
        if (remoteSessionCapabilities.containsModuleCapability(RFC8525_YANG_LIBRARY_CAPABILITY)
                || remoteSessionCapabilities.containsModuleCapability(RFC7895_YANG_LIBRARY_CAPABILITY)) {
            return libraryModulesSchemaFactory.createLibraryModulesSchema(deviceRpc, id);
        }

        return NetconfStateSchemas.EMPTY;
    }
}
