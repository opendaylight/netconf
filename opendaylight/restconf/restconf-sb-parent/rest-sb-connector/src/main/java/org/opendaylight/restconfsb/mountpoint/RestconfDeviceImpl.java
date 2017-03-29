/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.mountpoint;

import com.google.common.collect.ImmutableList;
import java.util.List;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMNotificationService;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.restconfsb.communicator.api.http.ConnectionListener;
import org.opendaylight.restconfsb.communicator.api.http.Sender;
import org.opendaylight.restconfsb.communicator.api.stream.Stream;
import org.opendaylight.restconfsb.communicator.impl.RestconfFacadeImpl;
import org.opendaylight.restconfsb.communicator.impl.sender.NodeConnectionException;
import org.opendaylight.restconfsb.mountpoint.sal.RestconfDataBroker;
import org.opendaylight.restconfsb.mountpoint.sal.RestconfNotificationService;
import org.opendaylight.restconfsb.mountpoint.sal.RestconfRpcService;
import org.opendaylight.restconfsb.mountpoint.schema.DirectorySchemaContextCache;
import org.opendaylight.restconfsb.mountpoint.schema.SchemaContextResolver;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.RestconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.node.status.Module;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class RestconfDeviceImpl implements RestconfDevice {

    private static final Logger LOG = LoggerFactory.getLogger(RestconfDeviceImpl.class);

    private static final String CACHE = "cache/schema/";
    private static final DirectorySchemaContextCache cache = new DirectorySchemaContextCache(CACHE);

    private final RestconfDeviceId deviceId;
    private final SchemaContext schemaContext;
    private final List<Module> supportedModules;
    private final DOMDataBroker dataBroker;
    private final RestconfRpcService rpcService;
    private final RestconfNotificationService notificationService;
    private final RestconfFacadeImpl restconfFacade;

    /**
     * Create new RestconfDeviceImpl.
     *
     * @param sender sender
     * @param connectionListener connection listener
     * @param node node
     * @throws NodeConnectionException
     */
    RestconfDeviceImpl(final Sender sender, final ConnectionListener connectionListener,
                       final Node node)
            throws NodeConnectionException {
        final String nodeName = node.getNodeId().getValue();
        this.deviceId = new RestconfDeviceId(nodeName);
        LOG.debug("{}: Create sender", nodeName);
        sender.registerConnectionListener(connectionListener);
        final SchemaContextResolver resolver = new SchemaContextResolver(cache);
        final RestconfDeviceInfo monitoring = new RestconfDeviceInfo(sender);
        this.supportedModules = ImmutableList.copyOf(monitoring.getModules(node.getAugmentation(RestconfNode.class)));
        LOG.debug("{}: Create schema context", nodeName);
        this.schemaContext = resolver.createSchemaContext(supportedModules);
        this.restconfFacade = RestconfFacadeImpl.createXmlRestconfFacade(schemaContext, sender);
        final List<Stream> streams = monitoring.getStreams();
        for (final Stream stream : streams) {
            sender.subscribeToStream(stream.getLocation());
        }
        this.notificationService = new RestconfNotificationService();
        restconfFacade.registerNotificationListener(notificationService);
        this.dataBroker = new RestconfDataBroker(restconfFacade);
        this.rpcService = new RestconfRpcService(restconfFacade);
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

    @Override
    public void close() throws Exception {
        restconfFacade.close();
    }
}