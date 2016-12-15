/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.callhome.mount;

import com.google.common.annotations.VisibleForTesting;
import java.net.InetSocketAddress;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.netconf.callhome.protocol.CallHomeAuthorizationProvider;
import org.opendaylight.netconf.callhome.protocol.NetconfCallHomeServer;
import org.opendaylight.netconf.callhome.protocol.NetconfCallHomeServerBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netconf.callhome.server.rev161109.Devices;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IetfZeroTouchCallHomeServerProvider implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(IetfZeroTouchCallHomeServerProvider.class);
    private static final String appName = "CallHomeServer";

    static final InstanceIdentifier<Devices> ALL_DEVICES = InstanceIdentifier.builder(Devices.class).build();


    private final DataBroker dataBroker;
    private final CallHomeMountDispatcher mountDispacher;

    protected NetconfCallHomeServer server;


    private static final String CALL_HOME_PORT_KEY = "DefaultCallHomePort";
    static String configurationPath = "etc/ztp-callhome-config.cfg";

    private int port = 0; // 0 = use default in NetconfCallHomeBuilder

    public IetfZeroTouchCallHomeServerProvider(DataBroker dataBroker, CallHomeMountDispatcher mountDispacher) {
        this.dataBroker = dataBroker;
        this.mountDispacher = mountDispacher;
    }

    public void init() {
        // Register itself as a listener to changes in Devices subtree
        try {
            LOG.info("Initializing provider for {}", appName);
            loadConfigurableValues(configurationPath);
            initializeServer();
            LOG.info("Initialization complete for {}", appName);
        } catch (IndexOutOfBoundsException | Configuration.ConfigurationException e) {
            LOG.error("Unable to successfully initialize", e);
        }
    }

    void loadConfigurableValues(String configurationPath) throws Configuration.ConfigurationException {
        try {
            Configuration configuration = new Configuration(configurationPath);
            port = configuration.getAsPort(CALL_HOME_PORT_KEY);
        } catch (Exception e) {
            LOG.error("Problem trying to load configuration values from {}", configurationPath, e);
        }
    }

    private CallHomeAuthorizationProvider getCallHomeAuthorization() {
        return new CallHomeAuthProviderImpl(dataBroker);
    }

    private void initializeServer() {
        LOG.info("Initializing Call Home server instance");
        CallHomeAuthorizationProvider auth = new DuplicateSessionRejectingAuthProvider(
                mountDispacher.getSessionManager(), getCallHomeAuthorization());
        NetconfCallHomeServerBuilder builder = new NetconfCallHomeServerBuilder(auth, mountDispacher);

        if (port > 0)
            builder.setBindAddress(new InetSocketAddress(port));
        server = builder.build();
        server.bind();
        mountDispacher.createTopology();
        LOG.info("Initialization complete for Call Home server instance");
    }

    @VisibleForTesting
    void assertValid(Object obj, String description) {
        if (obj == null)
            throw new RuntimeException(
                    String.format("Failed to find %s in IetfZeroTouchCallHomeProvider.initialize()", description));
    }

    @Override
    public void close() throws Exception {
        if (server != null) {
            server.close();
        }

        LOG.info("Successfully closed provider for {}", appName);
    }

}
