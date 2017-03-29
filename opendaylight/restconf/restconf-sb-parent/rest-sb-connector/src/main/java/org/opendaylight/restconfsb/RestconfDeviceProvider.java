/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.controller.config.threadpool.ThreadPool;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPointService;
import org.opendaylight.controller.sal.core.api.Broker;
import org.opendaylight.controller.sal.core.api.Provider;
import org.opendaylight.restconfsb.communicator.api.http.ConnectionListener;
import org.opendaylight.restconfsb.communicator.impl.sender.SenderFactory;
import org.opendaylight.restconfsb.mountpoint.RestconfDeviceManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.node.status.Module;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RestconfDeviceProvider creates restconf connector when spawning via config subsystem.
 */
public class RestconfDeviceProvider implements Provider, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(RestconfDeviceProvider.class);
    private final SenderFactory senderFactory;

    private final Node node;
    private final ThreadPool processingExecutor;
    private final ScheduledThreadPool reconnectExecutor;

    public RestconfDeviceProvider(final Node node, final ThreadPool processingExecutor, final ScheduledThreadPool reconnectExecutor,
                                  final SenderFactory senderFactory) {
        this.node = node;
        this.processingExecutor = processingExecutor;
        this.reconnectExecutor = reconnectExecutor;
        this.senderFactory = senderFactory;
    }

    @Override
    public void onSessionInitiated(final Broker.ProviderSession session) {
        final DOMMountPointService service = session.getService(DOMMountPointService.class);
        final RestconfDeviceManager manager = new RestconfDeviceManager(node, senderFactory, processingExecutor, reconnectExecutor, service);
        final ListenableFuture<List<Module>> modules = manager.connect(new ConnectionListener() {
            @Override
            public void onConnectionReestablished() {

            }

            @Override
            public void onConnectionFailed(final Throwable t) {

            }
        });
        Futures.addCallback(modules, new FutureCallback<List<Module>>() {
            @Override
            public void onSuccess(@Nullable final List<Module> result) {
                LOG.info("Restconf connector created");
            }

            @Override
            public void onFailure(final Throwable t) {
                LOG.error("Can't create restconf connector", t);
            }
        });

    }

    @Override
    public Collection<ProviderFunctionality> getProviderFunctionality() {
        return Collections.emptySet();
    }

    @Override
    public void close() throws Exception {

    }
}