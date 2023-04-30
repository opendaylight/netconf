/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.mdsal.operations;

import org.opendaylight.netconf.server.api.operations.NetconfOperationServiceFactory;
import org.opendaylight.netconf.server.api.operations.NetconfOperationServiceFactoryListener;
import org.opendaylight.netconf.server.osgi.AggregatedNetconfOperationServiceFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;

@Component(service = { NetconfOperationServiceFactory.class, NetconfOperationServiceFactoryListener.class },
           property = "type=mapper-aggregator-registry", immediate = true)
public final class MapperAggregatorRegistry extends AggregatedNetconfOperationServiceFactory {
    @Activate
    public MapperAggregatorRegistry() {
        super();
    }

    @Override
    @Deactivate
    public void close() {
        super.close();
    }
}
