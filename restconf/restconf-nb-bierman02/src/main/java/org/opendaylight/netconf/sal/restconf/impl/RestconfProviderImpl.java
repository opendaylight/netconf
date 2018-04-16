/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.restconf.impl;

import com.google.common.base.Preconditions;
import java.math.BigInteger;
import org.opendaylight.controller.md.sal.common.util.jmx.AbstractMXBean;
import org.opendaylight.netconf.sal.rest.api.RestConnector;
import org.opendaylight.netconf.sal.restconf.impl.jmx.Config;
import org.opendaylight.netconf.sal.restconf.impl.jmx.Delete;
import org.opendaylight.netconf.sal.restconf.impl.jmx.Get;
import org.opendaylight.netconf.sal.restconf.impl.jmx.Operational;
import org.opendaylight.netconf.sal.restconf.impl.jmx.Post;
import org.opendaylight.netconf.sal.restconf.impl.jmx.Put;
import org.opendaylight.netconf.sal.restconf.impl.jmx.RestConnectorRuntimeMXBean;
import org.opendaylight.netconf.sal.restconf.impl.jmx.Rpcs;
import org.opendaylight.netconf.sal.streams.websockets.WebSocketServer;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;

public class RestconfProviderImpl extends AbstractMXBean
        implements AutoCloseable, RestConnector, RestConnectorRuntimeMXBean {
    private final IpAddress websocketAddress;
    private final PortNumber websocketPort;
    private final StatisticsRestconfServiceWrapper stats;
    private Thread webSocketServerThread;

    public RestconfProviderImpl(StatisticsRestconfServiceWrapper stats, IpAddress websocketAddress,
            PortNumber websocketPort) {
        super("Draft02ProviderStatistics", "restconf-connector", null);
        this.stats = Preconditions.checkNotNull(stats);
        this.websocketAddress = Preconditions.checkNotNull(websocketAddress);
        this.websocketPort = Preconditions.checkNotNull(websocketPort);
    }

    public void start() {
        this.webSocketServerThread = new Thread(WebSocketServer.createInstance(
                new String(websocketAddress.getValue()), websocketPort.getValue()));
        this.webSocketServerThread.setName("Web socket server on port " + websocketPort);
        this.webSocketServerThread.start();

        registerMBean();
    }

    @Override
    public void close() {
        WebSocketServer.destroyInstance();
        if (this.webSocketServerThread != null) {
            this.webSocketServerThread.interrupt();
        }

        unregisterMBean();
    }

    @Override
    public Config getConfig() {
        final Config config = new Config();

        final Get get = new Get();
        get.setReceivedRequests(this.stats.getConfigGet());
        get.setSuccessfulResponses(this.stats.getSuccessGetConfig());
        get.setFailedResponses(this.stats.getFailureGetConfig());
        config.setGet(get);

        final Post post = new Post();
        post.setReceivedRequests(this.stats.getConfigPost());
        post.setSuccessfulResponses(this.stats.getSuccessPost());
        post.setFailedResponses(this.stats.getFailurePost());
        config.setPost(post);

        final Put put = new Put();
        put.setReceivedRequests(this.stats.getConfigPut());
        put.setSuccessfulResponses(this.stats.getSuccessPut());
        put.setFailedResponses(this.stats.getFailurePut());
        config.setPut(put);

        final Delete delete = new Delete();
        delete.setReceivedRequests(this.stats.getConfigDelete());
        delete.setSuccessfulResponses(this.stats.getSuccessDelete());
        delete.setFailedResponses(this.stats.getFailureDelete());
        config.setDelete(delete);

        return config;
    }

    @Override
    public Operational getOperational() {
        final BigInteger opGet = this.stats.getOperationalGet();
        final Operational operational = new Operational();
        final Get get = new Get();
        get.setReceivedRequests(opGet);
        get.setSuccessfulResponses(this.stats.getSuccessGetOperational());
        get.setFailedResponses(this.stats.getFailureGetOperational());
        operational.setGet(get);
        return operational;
    }

    @Override
    public Rpcs getRpcs() {
        final BigInteger rpcInvoke = this.stats.getRpc();
        final Rpcs rpcs = new Rpcs();
        rpcs.setReceivedRequests(rpcInvoke);
        return rpcs;
    }
}
