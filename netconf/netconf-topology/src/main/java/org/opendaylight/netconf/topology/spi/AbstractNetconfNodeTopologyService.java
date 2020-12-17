/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.spi;

import static com.google.common.base.Verify.verify;

import com.google.common.annotations.Beta;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.RpcProviderService;
import org.opendaylight.netconf.sal.connect.util.NetconfTopologyRPCProvider;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeTopologyService;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yangtools.concepts.Registration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MD-SAL RPCs corresponding to {@link NetconfNodeTopologyService} on the {@link NetworkTopology} instance identified
 * as {@value #TOPOLOGY_NETCONF}. This class serves as a common building block for implementations of this particular
 * topology.
 */
@Beta
public abstract class AbstractNetconfNodeTopologyService extends NetconfTopologyRPCProvider {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractNetconfNodeTopologyService.class);
    private static final String TOPOLOGY_NETCONF = "topology-netconf";

    private Registration reg;

    AbstractNetconfNodeTopologyService(final DataBroker dataBroker, final AAAEncryptionService encryptionService) {
        super(dataBroker, encryptionService, TOPOLOGY_NETCONF);
    }

    final void start(final RpcProviderService rpcProvider) {
        verify(reg == null, "Attempted to start service twice");
        reg = rpcProvider.registerRpcImplementation(NetconfNodeTopologyService.class, this);
        LOG.info("NetconfNodeTopologyService on {} started", topologyPath());
    }

    final void stop() {
        if (reg != null) {
            reg.close();
            reg = null;
            LOG.info("NetconfNodeTopologyService on {} stopped", topologyPath());
        }
    }
}
