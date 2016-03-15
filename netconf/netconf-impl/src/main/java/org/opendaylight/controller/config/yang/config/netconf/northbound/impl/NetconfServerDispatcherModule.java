/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.controller.config.yang.config.netconf.northbound.impl;

import org.opendaylight.controller.config.api.JmxAttributeValidationException;
import org.opendaylight.netconf.impl.NetconfServerSessionNegotiatorFactoryBuilder;
import org.opendaylight.netconf.mapping.api.NetconfOperationServiceFactory;
import org.opendaylight.netconf.api.monitoring.NetconfMonitoringService;
import org.opendaylight.netconf.impl.NetconfServerDispatcherImpl;
import org.opendaylight.netconf.impl.NetconfServerSessionNegotiatorFactory;
import org.opendaylight.netconf.impl.SessionIdProvider;
import org.opendaylight.netconf.impl.osgi.AggregatedNetconfOperationServiceFactory;

public class NetconfServerDispatcherModule extends AbstractNetconfServerDispatcherModule {
    public NetconfServerDispatcherModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver) {
        super(identifier, dependencyResolver);
    }

    public NetconfServerDispatcherModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver, NetconfServerDispatcherModule oldModule, AutoCloseable oldInstance) {
        super(identifier, dependencyResolver, oldModule, oldInstance);
    }

    @Override
    public void customValidation() {
        JmxAttributeValidationException.checkCondition(getConnectionTimeoutMillis() > 0, "Invalid connection timeout", connectionTimeoutMillisJmxAttribute);
    }

    @Override
    public AutoCloseable createInstance() {

        final AggregatedNetconfOperationServiceFactory aggregatedOpProvider = getAggregatedOpProvider();
        final NetconfMonitoringService monitoringService = getServerMonitorDependency();

        final NetconfServerSessionNegotiatorFactory serverNegotiatorFactory = new NetconfServerSessionNegotiatorFactoryBuilder()
                .setAggregatedOpService(aggregatedOpProvider)
                .setTimer(getTimerDependency())
                .setIdProvider(new SessionIdProvider())
                .setMonitoringService(monitoringService)
                .setConnectionTimeoutMillis(getConnectionTimeoutMillis())
                .build();
        final NetconfServerDispatcherImpl.ServerChannelInitializer serverChannelInitializer = new NetconfServerDispatcherImpl.ServerChannelInitializer(
                serverNegotiatorFactory);

        return new NetconfServerDispatcherImpl(serverChannelInitializer, getBossThreadGroupDependency(), getWorkerThreadGroupDependency()) {

            @Override
            public void close() {
                // NOOP, close should not be present here, the deprecated method closes injected evet loop groups
            }
        };

    }

    private AggregatedNetconfOperationServiceFactory getAggregatedOpProvider() {
        final AggregatedNetconfOperationServiceFactory netconfOperationProvider = new AggregatedNetconfOperationServiceFactory();
        for (final NetconfOperationServiceFactory netconfOperationServiceFactory : getMappersDependency()) {
            netconfOperationProvider.onAddNetconfOperationServiceFactory(netconfOperationServiceFactory);
        }
        return netconfOperationProvider;
    }


}
