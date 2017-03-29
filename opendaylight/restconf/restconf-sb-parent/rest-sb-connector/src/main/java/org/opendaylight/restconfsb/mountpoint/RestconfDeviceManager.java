/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.mountpoint;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.Nullable;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.controller.config.threadpool.ThreadPool;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPointService;
import org.opendaylight.restconfsb.communicator.api.http.ConnectionListener;
import org.opendaylight.restconfsb.communicator.api.http.Sender;
import org.opendaylight.restconfsb.communicator.impl.sender.SenderFactory;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.RestconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.node.status.Module;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manager is responsible for creating connector and registering restconf device mount point to SAL via provided
 * {@link DOMMountPointService}.
 */
public class RestconfDeviceManager implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(RestconfDeviceManager.class);

    private final SenderFactory senderFactory;
    private final Node node;
    private final ThreadPool processingExecutor;
    private final ScheduledThreadPool reconnectExecutor;
    private final DOMMountPointService mountpointService;
    @Nullable
    private RestconfMount mount;

    public RestconfDeviceManager(final Node node, final SenderFactory senderFactory, final ThreadPool processingExecutor,
                                 final ScheduledThreadPool reconnectExecutor, final DOMMountPointService mountpointService) {
        this.senderFactory = senderFactory;
        this.node = node;
        Preconditions.checkNotNull(node.getAugmentation(RestconfNode.class));
        this.processingExecutor = Preconditions.checkNotNull(processingExecutor);
        this.reconnectExecutor = Preconditions.checkNotNull(reconnectExecutor);
        this.mountpointService = Preconditions.checkNotNull(mountpointService);
    }

    /**
     * Tries to connect to device. If attempt is successful, device mount point is registered to SAL.
     */
    public ListenableFuture<List<Module>> connect(final ConnectionListener listener) {
        return MoreExecutors.listeningDecorator(processingExecutor.getExecutor()).submit(new Callable<List<Module>>() {
            @Override
            public List<Module> call() throws Exception {

                final Sender sender = senderFactory.createSender(node, reconnectExecutor.getExecutor());
                final RestconfDeviceImpl device = new RestconfDeviceImpl(sender, listener, node);
                mount = new RestconfMount(device);
                mount.register(mountpointService);
                LOG.info("{}: Restconf connector initialized successfully", node.getNodeId().getValue());
                return device.getSupportedModules();
            }
        });
    }

    /**
     * Deregisters mount point from SAL.
     */
    public void disconnect() {
        if (mount != null) {
            mount.deregister();
        }
    }

    @Override
    public void close() throws Exception {
        LOG.info("{}: Closing restconf connector", node.getNodeId().getValue());
        if (mount != null) {
            mount.close();
        }
    }

}
