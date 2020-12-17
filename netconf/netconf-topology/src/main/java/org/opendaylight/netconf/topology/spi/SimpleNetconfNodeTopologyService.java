/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.spi;

import com.google.common.annotations.Beta;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.RpcProviderService;

@Beta
@Singleton
public final class SimpleNetconfNodeTopologyService extends AbstractNetconfNodeTopologyService {
    @Inject
    // FIXME: we really should be binding to a concrete NetconfTopology here
    public SimpleNetconfNodeTopologyService(final DataBroker dataBroker, final AAAEncryptionService encryptionService,
            final RpcProviderService rpcProvider) {
        super(dataBroker, encryptionService);
        start(rpcProvider);
    }

    @PreDestroy
    public void destroy() {
        super.stop();
    }
}
