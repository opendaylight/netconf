/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.mount;

import com.google.common.annotations.VisibleForTesting;
import io.netty.util.concurrent.EventExecutor;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.controller.config.threadpool.ThreadPool;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.netconf.client.NetconfClientDispatcher;
import org.opendaylight.netconf.client.mdsal.api.BaseNetconfSchemas;
import org.opendaylight.netconf.client.mdsal.api.CredentialProvider;
import org.opendaylight.netconf.client.mdsal.api.DeviceActionFactory;
import org.opendaylight.netconf.client.mdsal.api.SchemaResourceManager;
import org.opendaylight.netconf.client.mdsal.api.SslHandlerFactoryProvider;
import org.opendaylight.netconf.topology.spi.AbstractNetconfTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;

// Non-final for mocking
public class CallHomeTopology extends AbstractNetconfTopology {
    public CallHomeTopology(final String topologyId, final NetconfClientDispatcher clientDispatcher,
            final EventExecutor eventExecutor, final ScheduledThreadPool keepaliveExecutor,
            final ThreadPool processingExecutor, final SchemaResourceManager schemaRepositoryProvider,
            final DataBroker dataBroker, final DOMMountPointService mountPointService,
            final AAAEncryptionService encryptionService, final BaseNetconfSchemas baseSchemas,
            final DeviceActionFactory deviceActionFactory, final CredentialProvider credentialProvider,
            final SslHandlerFactoryProvider sslHandlerFactoryProvider) {
        super(topologyId, clientDispatcher, eventExecutor, keepaliveExecutor, processingExecutor,
            schemaRepositoryProvider, dataBroker, mountPointService, encryptionService, deviceActionFactory,
            baseSchemas, credentialProvider, sslHandlerFactoryProvider);
    }

    void disconnectNode(final NodeId nodeId) {
        deleteNode(nodeId);
    }

    @VisibleForTesting
    public void connectNode(final Node node) {
        ensureNode(node);
    }
}
