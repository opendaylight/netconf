/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.controller.config.yang.clustered.netconf.topology;

import org.opendaylight.netconf.topology.impl.ClusteredNetconfTopology;

public class ClusteredNetconfTopologyModule extends org.opendaylight.controller.config.yang.clustered.netconf.topology.AbstractClusteredNetconfTopologyModule {
    public ClusteredNetconfTopologyModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver) {
        super(identifier, dependencyResolver);
    }

    public ClusteredNetconfTopologyModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver, org.opendaylight.controller.config.yang.clustered.netconf.topology.ClusteredNetconfTopologyModule oldModule, java.lang.AutoCloseable oldInstance) {
        super(identifier, dependencyResolver, oldModule, oldInstance);
    }

    @Override
    public void customValidation() {
        // add custom validation form module attributes here.
    }

    @Override
    public java.lang.AutoCloseable createInstance() {
        return new ClusteredNetconfTopology(getTopologyId(),
                getClientDispatcherDependency(),
                getBindingRegistryDependency(),
                getDomRegistryDependency(),
                getEventExecutorDependency(),
                getKeepaliveExecutorDependency(),
                getProcessingExecutorDependency(),
                getSharedSchemaRepositoryDependency(),
                getActorSystemProviderServiceDependency().getActorSystem(),
                getEntityOwnershipServiceDependency());
    }

}
