/*
 * Copyright © 2019 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.common.configuration;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Set;
import javax.annotation.Nonnull;
import org.osgi.service.cm.ManagedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration manager for RESTCONF.
 */
public class RestconfConfigurationImpl implements RestconfConfiguration, ManagedService, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(RestconfConfigurationImpl.class);
    private static final String WEB_SOCKET_ENABLED = "web-socket-enabled";
    private static final String WEB_SOCKET_TCP_PORT = "web-socket-tcp-port";
    private static final String WEB_SOCKET_SECURITY_TYPE = "web-socket-security-type";

    private final Set<RestconfConfigurationListener> listeners = Sets.newHashSet();

    private RestconfConfigurationHolder retsconfConfigurationHolder;

    /**
     * Initialization of RESTCONF configuration.
     *
     * @param webSocketPort         Port on which web-socket server listens.
     * @param webSocketSecurityType Security type of web-socket server.
     */
    public RestconfConfigurationImpl(@Nonnull final String webSocketEnabled,
                                     @Nonnull final String webSocketPort,
                                     @Nonnull final String webSocketSecurityType) {
        final Dictionary<String, String> dictionaryConfig = new Hashtable<>();
        dictionaryConfig.put(WEB_SOCKET_ENABLED, Preconditions.checkNotNull(webSocketEnabled));
        dictionaryConfig.put(WEB_SOCKET_TCP_PORT, Preconditions.checkNotNull(webSocketPort));
        dictionaryConfig.put(WEB_SOCKET_SECURITY_TYPE, Preconditions.checkNotNull(webSocketSecurityType));
        setInitialSettings(dictionaryConfig);
    }

    private synchronized void setInitialSettings(final Dictionary<String, ?> properties) {
        retsconfConfigurationHolder = new RestconfConfigurationHolderBuilder()
                .setEnabledWebSocketServer(properties.get(WEB_SOCKET_ENABLED))
                .setWebSocketSecurityType(properties.get(WEB_SOCKET_SECURITY_TYPE))
                .setWebSocketTcpPort(properties.get(WEB_SOCKET_TCP_PORT))
                .build();
    }

    @Override
    public synchronized void updated(final Dictionary<String, ?> properties) {
        if (properties == null) {
            LOG.trace("RESTCONF configuration cannot be updated as passed dictionary is null "
                    + "- the previous configuration is kept unmodified: {}.", retsconfConfigurationHolder);
        } else {
            final RestconfConfigurationHolder newConfigurationHolder
                    = new RestconfConfigurationHolderBuilder(retsconfConfigurationHolder)
                    .setEnabledWebSocketServer(properties.get(WEB_SOCKET_ENABLED))
                    .setWebSocketSecurityType(properties.get(WEB_SOCKET_SECURITY_TYPE))
                    .setWebSocketTcpPort(properties.get(WEB_SOCKET_TCP_PORT))
                    .build();

            if (!newConfigurationHolder.equals(retsconfConfigurationHolder)) {
                retsconfConfigurationHolder = newConfigurationHolder;
                listeners.forEach(listener -> listener.updateConfiguration(newConfigurationHolder));
                LOG.trace("RESTCONF configuration has been updated: {}.", properties.toString());
            }
        }
    }

    @Override
    public synchronized void close() {
        listeners.forEach(listener -> listener.updateConfiguration(null));
        listeners.clear();
        retsconfConfigurationHolder = null;
    }

    @Override
    public synchronized RestconfConfigurationHolder getActualConfiguration() {
        return retsconfConfigurationHolder;
    }

    @Override
    public synchronized void registerListener(@Nonnull RestconfConfigurationListener listener) {
        listeners.add(Preconditions.checkNotNull(listener));
        listener.updateConfiguration(retsconfConfigurationHolder);
    }

    @Override
    public synchronized void releaseListener(@Nonnull RestconfConfigurationListener listener) {
        listeners.remove(Preconditions.checkNotNull(listener));
    }
}