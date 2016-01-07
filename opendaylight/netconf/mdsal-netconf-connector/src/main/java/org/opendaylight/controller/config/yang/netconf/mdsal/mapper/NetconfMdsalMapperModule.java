/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.controller.config.yang.netconf.mdsal.mapper;

import org.opendaylight.netconf.mdsal.connector.MdsalNetconfOperationServiceFactory;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceProvider;

public class NetconfMdsalMapperModule extends org.opendaylight.controller.config.yang.netconf.mdsal.mapper.AbstractNetconfMdsalMapperModule{
    public NetconfMdsalMapperModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver) {
        super(identifier, dependencyResolver);
    }

    public NetconfMdsalMapperModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver, org.opendaylight.controller.config.yang.netconf.mdsal.mapper.NetconfMdsalMapperModule oldModule, java.lang.AutoCloseable oldInstance) {
        super(identifier, dependencyResolver, oldModule, oldInstance);
    }

    @Override
    public void customValidation() {
        // add custom validation form module attributes here.
    }

    @Override
    public java.lang.AutoCloseable createInstance() {
        // TODO use concrete schema source provider for YANG text source to avoid generic interface in config subsystem
        final SchemaSourceProvider<YangTextSchemaSource> sourceProvider =
                (SchemaSourceProvider<YangTextSchemaSource>) getRootSchemaSourceProviderDependency();

        final MdsalNetconfOperationServiceFactory mdsalNetconfOperationServiceFactory =
            new MdsalNetconfOperationServiceFactory(getRootSchemaServiceDependency(), sourceProvider) {
                @Override
                public void close() throws Exception {
                    super.close();
                    getMapperAggregatorDependency().onRemoveNetconfOperationServiceFactory(this);
                }
            };
        getDomBrokerDependency().registerConsumer(mdsalNetconfOperationServiceFactory);
        getMapperAggregatorDependency().onAddNetconfOperationServiceFactory(mdsalNetconfOperationServiceFactory);
        return mdsalNetconfOperationServiceFactory;
    }

}
