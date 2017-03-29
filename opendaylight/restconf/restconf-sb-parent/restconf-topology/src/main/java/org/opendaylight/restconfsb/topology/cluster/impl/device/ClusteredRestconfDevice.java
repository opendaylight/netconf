/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.topology.cluster.impl.device;

import com.google.common.base.Preconditions;
import java.util.List;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMNotificationService;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.restconfsb.communicator.api.RestconfFacade;
import org.opendaylight.restconfsb.communicator.api.http.Sender;
import org.opendaylight.restconfsb.communicator.api.stream.Stream;
import org.opendaylight.restconfsb.communicator.impl.RestconfFacadeImpl;
import org.opendaylight.restconfsb.communicator.impl.sender.NodeConnectionException;
import org.opendaylight.restconfsb.mountpoint.RestconfDevice;
import org.opendaylight.restconfsb.mountpoint.RestconfDeviceId;
import org.opendaylight.restconfsb.mountpoint.RestconfDeviceInfo;
import org.opendaylight.restconfsb.mountpoint.sal.RestconfDataBroker;
import org.opendaylight.restconfsb.mountpoint.sal.RestconfNotificationService;
import org.opendaylight.restconfsb.mountpoint.sal.RestconfRpcService;
import org.opendaylight.restconfsb.mountpoint.schema.DirectorySchemaContextCache;
import org.opendaylight.restconfsb.mountpoint.schema.SchemaContextResolver;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.node.status.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClusteredRestconfDevice implements RestconfDevice {

    private static final Logger LOG = LoggerFactory.getLogger(ClusteredRestconfDevice.class);

    private static final String CACHE = "cache/schema/";
    private static final DirectorySchemaContextCache cache = new DirectorySchemaContextCache(CACHE);

    private final DOMRpcService rpcService;
    private final DOMDataBroker dataBroker;
    private final RestconfNotificationService notificationService;
    private final SchemaContext schemaContext;
    private final List<Module> supportedModules;
    private final RestconfDeviceId deviceId;
    private final RestconfFacade facade;

    private ClusteredRestconfDevice(final RestconfDeviceId deviceId, final RestconfFacade facade, final List<Module> supportedModules,
                                    final SchemaContext schemaContext) {
        LOG.info("{}: Creating clustered device ", deviceId.getNodeName());
        this.facade = facade;
        this.deviceId = Preconditions.checkNotNull(deviceId);
        this.rpcService = new RestconfRpcService(facade);
        this.dataBroker = new RestconfDataBroker(facade);
        this.notificationService = new RestconfNotificationService();
        facade.registerNotificationListener(notificationService);
        this.supportedModules = supportedModules;
        LOG.debug("{}: Create schema context", deviceId.getNodeName());
        this.schemaContext = schemaContext;
    }


    public static ClusteredRestconfDevice createSlaveDevice(final RestconfDeviceId deviceId, final RestconfFacade facade,
                                                            final List<Module> supportedModules)
            throws NodeConnectionException {
        return new ClusteredRestconfDevice(deviceId, facade, supportedModules, new SchemaContextResolver(cache).createSchemaContext(supportedModules));
    }

    public static ClusteredRestconfDevice createMasterDevice(final RestconfDeviceId deviceId, final Sender sender,
                                                             final List<Module> modules,
                                                             final ScheduledThreadPool reconnectExecutor)
            throws NodeConnectionException {
        final SchemaContextResolver resolver = new SchemaContextResolver(cache);
        final SchemaContext context = resolver.createSchemaContext(modules);
        final RestconfFacadeImpl restconfFacade = RestconfFacadeImpl.createXmlRestconfFacade(context, sender);
        final List<Stream> streams = new RestconfDeviceInfo(sender).getStreams();
        for (final Stream stream : streams) {
            sender.subscribeToStream(stream.getLocation());
        }
        return new ClusteredRestconfDevice(deviceId, restconfFacade, modules, context);
    }

    @Override
    public DOMRpcService getRpcService() {
        return rpcService;
    }

    @Override
    public DOMDataBroker getDataBroker() {
        return dataBroker;
    }

    @Override
    public DOMNotificationService getNotificationService() {
        return notificationService;
    }

    @Override
    public SchemaContext getSchemaContext() {
        return schemaContext;
    }

    @Override
    public RestconfDeviceId getDeviceId() {
        return deviceId;
    }

    @Override
    public List<Module> getSupportedModules() {
        return supportedModules;
    }

    public RestconfFacade getFacade() {
        return facade;
    }

    @Override
    public void close() throws Exception {
        facade.close();
    }
}
