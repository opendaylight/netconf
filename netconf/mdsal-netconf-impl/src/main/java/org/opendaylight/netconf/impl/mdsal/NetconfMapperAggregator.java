/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.impl.mdsal;

import org.opendaylight.netconf.impl.osgi.AggregatedNetconfOperationServiceFactory;
import org.opendaylight.netconf.mapping.api.NetconfOperationServiceFactory;
import org.opendaylight.netconf.mapping.api.NetconfOperationServiceFactoryListener;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;

@Component(service = { NetconfOperationServiceFactory.class, NetconfOperationServiceFactoryListener.class },
           property = NetconfMapperAggregator.OSGI_TYPE, immediate = true)
public final class NetconfMapperAggregator extends AggregatedNetconfOperationServiceFactory {
    static final String OSGI_TYPE = "type=mapper-aggregator-registry";

    @Activate
    public NetconfMapperAggregator() {
        super();
    }

    @Override
    @Deactivate
    public void close() {
        super.close();
    }
}
