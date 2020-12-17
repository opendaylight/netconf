/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.spi;

import com.google.common.annotations.Beta;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.RpcProviderService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

@Beta
@Component(immediate = true)
// FIXME: unify with SimpleNetconfNodeTopologyService when we have constructor injection
public final class OSGiNetconfNodeTopologyService {
    @Reference
    DataBroker dataBroker;
    @Reference
    AAAEncryptionService encryptionService;
    @Reference
    RpcProviderService rpcProvider;

    private SimpleNetconfNodeTopologyService delegate;

    @Activate
    void activate() {
        this.delegate = new SimpleNetconfNodeTopologyService(dataBroker, encryptionService, rpcProvider);
    }

    @Deactivate
    void deactivate() {
        delegate.destroy();
        delegate = null;
    }
}
