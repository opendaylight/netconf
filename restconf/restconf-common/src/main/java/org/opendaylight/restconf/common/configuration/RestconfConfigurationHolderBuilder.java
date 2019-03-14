/*
 * Copyright © 2019 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.common.configuration;

import com.google.common.base.Preconditions;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builder that can be used for creation of {@link RestconfConfigurationHolder} object.
 */
public class RestconfConfigurationHolderBuilder {
    private static final Logger LOG = LoggerFactory.getLogger(RestconfConfigurationHolderBuilder.class);
    private static final Integer MINIMUM_PORT_NUMBER = 1024;
    private static final Integer MAXIMUM_PORT_NUMBER = 65535;

    private Boolean originalWebSocketServerEnabled;
    private Integer originalWebSocketTcpPort;
    private RestconfConfigurationHolder.SecurityType originalWebSocketSecurityType;

    private Object webSocketTcpPort;
    private Object webSocketSecurityType;
    private Object webSocketServerEnabled;

    /**
     * Creation of empty builder.
     */
    RestconfConfigurationHolderBuilder() {
    }

    /**
     * Creation of builder using existing instance of {@link RestconfConfigurationHolder}.
     *
     * @param configurationHolder Existing RESTCONF configuration.
     */
    RestconfConfigurationHolderBuilder(final RestconfConfigurationHolder configurationHolder) {
        webSocketServerEnabled = configurationHolder.isWebSocketEnabled();
        webSocketTcpPort = configurationHolder.getWebSocketPort();
        webSocketSecurityType = configurationHolder.getWebSocketSecurityType();

        originalWebSocketTcpPort = (Integer) webSocketTcpPort;
        originalWebSocketSecurityType = (RestconfConfigurationHolder.SecurityType) webSocketSecurityType;
        originalWebSocketServerEnabled = (Boolean) webSocketServerEnabled;
    }

    /**
     * Setting of web-socket running status (TRUE/FALSE).
     *
     * @param enabledWebSocketServer Enabled/disabled web-socket server.
     * @return Updated builder.
     */
    RestconfConfigurationHolderBuilder setEnabledWebSocketServer(final Object enabledWebSocketServer) {
        this.webSocketServerEnabled = enabledWebSocketServer;
        return this;
    }

    /**
     * Setting of web-socket TCP listening port.
     *
     * @param webSocketTcpPort TCP port used by web-socket server.
     * @return Updated builder.
     */
    RestconfConfigurationHolderBuilder setWebSocketTcpPort(final Object webSocketTcpPort) {
        this.webSocketTcpPort = webSocketTcpPort;
        return this;
    }

    /**
     * Setting of web-socket security/protection level.
     *
     * @param webSocketSecurityType Security level that should be utilized by web-socket server.
     * @return Updated builder.
     */
    RestconfConfigurationHolderBuilder setWebSocketSecurityType(final Object webSocketSecurityType) {
        this.webSocketSecurityType = webSocketSecurityType;
        return this;
    }

    /**
     * Building of RESTCONF configuration.
     *
     * @return Created RESTCONF configuration.
     */
    public RestconfConfigurationHolder build() {
        Boolean builtWebSocketServerEnabled = buildWebSocketServerEnabled();
        Integer builtWebSocketTcpPort = buildWebSocketTcpPort();
        RestconfConfigurationHolder.SecurityType builtWebSocketSecurityType = buildWebSocketSecurityType();
        return new RestconfConfigurationHolder(
                builtWebSocketServerEnabled,
                builtWebSocketTcpPort,
                builtWebSocketSecurityType);
    }

    private Boolean buildWebSocketServerEnabled() {
        Boolean builtWebSocketServerEnabled;
        if (this.webSocketServerEnabled == null) {
            builtWebSocketServerEnabled = Preconditions.checkNotNull(originalWebSocketServerEnabled);
            LOG.debug("Web-socket server running status is null - original status {} is inherited.",
                    originalWebSocketServerEnabled);
        } else {
            builtWebSocketServerEnabled = Boolean.parseBoolean(this.webSocketServerEnabled.toString());
        }
        return builtWebSocketServerEnabled;
    }

    private Integer buildWebSocketTcpPort() {
        Integer builtWebSocketTcpPort;
        if (this.webSocketTcpPort == null) {
            builtWebSocketTcpPort = Preconditions.checkNotNull(originalWebSocketTcpPort);
            LOG.debug("Web-socket TCP port is null - original port number {} is kept unmodified.",
                    originalWebSocketTcpPort);
        } else {
            try {
                final Integer port = Integer.parseInt(this.webSocketTcpPort.toString());
                if (port < MINIMUM_PORT_NUMBER) {
                    builtWebSocketTcpPort = originalWebSocketTcpPort;
                    LOG.warn("RESTCONF configuration cannot be updated by new web-socket TCP port number: "
                                    + "privileged port {} (below {}) cannot be used - "
                                    + "the previous port number {} is kept unmodified.",
                            port,
                            MINIMUM_PORT_NUMBER,
                            Preconditions.checkNotNull(builtWebSocketTcpPort));
                } else if (port > MAXIMUM_PORT_NUMBER) {
                    builtWebSocketTcpPort = originalWebSocketTcpPort;
                    LOG.warn("RESTCONF configuration cannot be updated by new web-socket TCP port number: "
                                    + "illegal port number {} (above {}) appeared on input - "
                                    + "the previous port number {} is kept unmodified.",
                            port,
                            MAXIMUM_PORT_NUMBER,
                            Preconditions.checkNotNull(builtWebSocketTcpPort));
                } else {
                    builtWebSocketTcpPort = port;
                }
            } catch (final NumberFormatException exception) {
                builtWebSocketTcpPort = Preconditions.checkNotNull(originalWebSocketTcpPort);
                LOG.debug("Web-socket listening TCP port {} has wrong format "
                                + "- original port number {} is kept unmodified.",
                        this.webSocketTcpPort,
                        originalWebSocketTcpPort);
            }
        }
        return builtWebSocketTcpPort;
    }

    private RestconfConfigurationHolder.SecurityType buildWebSocketSecurityType() {
        RestconfConfigurationHolder.SecurityType builtWebSocketSecurityType;
        if (this.webSocketSecurityType == null) {
            builtWebSocketSecurityType = Preconditions.checkNotNull(originalWebSocketSecurityType);
            LOG.debug("Input security-type is null - original setting {} is kept unmodified.",
                    originalWebSocketSecurityType);
        } else {
            final Optional<RestconfConfigurationHolder.SecurityType> securityType
                    = RestconfConfigurationHolder.SecurityType.forName(this.webSocketSecurityType.toString());
            if (securityType.isPresent()) {
                builtWebSocketSecurityType = securityType.get();
            } else {
                builtWebSocketSecurityType = Preconditions.checkNotNull(originalWebSocketSecurityType);
                LOG.warn("Input web-socket security type {} cannot be recognized "
                                + "- the original setting {} is kept unmodified.",
                        this.webSocketSecurityType,
                        originalWebSocketSecurityType);
            }
        }
        return builtWebSocketSecurityType;
    }
}