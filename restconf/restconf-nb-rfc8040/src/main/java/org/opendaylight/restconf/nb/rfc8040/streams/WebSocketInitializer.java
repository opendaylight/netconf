/*
 * Copyright © 2019 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.nb.rfc8040.streams;

import com.google.common.base.Preconditions;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.annotation.Nonnull;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.aaa.cert.api.ICertificateManager;
import org.opendaylight.restconf.common.configuration.RestconfConfiguration;
import org.opendaylight.restconf.common.configuration.RestconfConfigurationHolder;
import org.opendaylight.restconf.common.configuration.RestconfConfigurationListener;
import org.opendaylight.restconf.nb.rfc8040.streams.websockets.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Initialization and management of web-socket server.
 */
@Singleton
@SuppressWarnings("unused")
public class WebSocketInitializer implements RestconfConfigurationListener {

    private static final Logger LOG = LoggerFactory.getLogger(WebSocketInitializer.class);

    private final ExecutorService webSocketThreadExecutor = Executors.newSingleThreadExecutor();
    private final RestconfConfiguration restconfConfiguration;
    private final ICertificateManager aaaCertificateManager;
    private Integer listeningPort;
    private RestconfConfigurationHolder.SecurityType securityType;
    private Boolean enabledWebSocketServer;
    private Future<?> webSocketServerFuture;
    private WebSocketServer webSocketServer;

    /**
     * Creation of web-socket initializer that manages web-socket server instances.
     *
     * @param restconfConfiguration Restconf configuration that contains defined web-socket port, security level,
     *                              and status.
     * @param aaaCertificateManager AAA certificate manager (required for creation of secured web-socket server).
     */
    @Inject
    public WebSocketInitializer(@Nonnull final RestconfConfiguration restconfConfiguration,
                                @Nonnull final ICertificateManager aaaCertificateManager) {
        this.restconfConfiguration = Preconditions.checkNotNull(restconfConfiguration);
        this.aaaCertificateManager = Preconditions.checkNotNull(aaaCertificateManager);
    }

    /**
     * Starting of web-socket server and registration of listener.
     */
    @PostConstruct
    public synchronized void start() {
        restconfConfiguration.registerListener(this);
        startWebSocketServer();
    }

    private void startWebSocketServer() {
        if (enabledWebSocketServer) {
            webSocketServer = new WebSocketServer(aaaCertificateManager, listeningPort, securityType);
            webSocketServerFuture = webSocketThreadExecutor.submit(webSocketServer);
            LOG.info("Web-socket server has been successfully started on the port {} with security level {}.",
                    listeningPort, securityType);
        }
    }

    /**
     * Closing of web-socket server and releasing of listener.
     */
    @PreDestroy
    public synchronized void close() {
        restconfConfiguration.releaseListener(this);
        closeWebSocketServer();
    }

    private void closeWebSocketServer() {
        if (webSocketServer != null) {
            webSocketServerFuture.cancel(true);
            webSocketServer = null;
            LOG.info("Web-socket server that was listening on port {} with security level {} has been closed.",
                    listeningPort, securityType);
        }
    }

    @Override
    public synchronized void updateConfiguration(final RestconfConfigurationHolder configurationHolder) {
        if (configurationHolder == null) {
            // something is rotten
            closeWebSocketServer();
        } else if (!configurationHolder.getWebSocketPort().equals(this.listeningPort)
                || !configurationHolder.getWebSocketSecurityType().equals(this.securityType)
                || !configurationHolder.isWebSocketEnabled().equals(this.enabledWebSocketServer)) {
            // restart web-socket server with new settings
            this.enabledWebSocketServer = configurationHolder.isWebSocketEnabled();
            this.listeningPort = configurationHolder.getWebSocketPort();
            this.securityType = configurationHolder.getWebSocketSecurityType();
            closeWebSocketServer();
            startWebSocketServer();
        }
    }

    /**
     * Fetching of actual web-socket server instance.
     *
     * @return Actual web-socket server wrapped in {@link Optional} or {@link Optional#empty()} if the server
     *     is not running.
     */
    @Nonnull
    public synchronized Optional<WebSocketServer> getWebSocketServer() {
        return webSocketServer == null ? Optional.empty() : Optional.of(webSocketServer);
    }
}