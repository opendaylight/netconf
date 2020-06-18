/*
 * Copyright (c) 2020 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.nb.rfc8639.layer.web.jetty.server.config.base;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Configuration of Jetty server instance used to run Lighty Restconf Northbound.
 */
public final class JettyServerConfiguration {
    private final List<ServerConnectorConfig> connectors;

    /**
     * Initialization of default config values.
     */
    private JettyServerConfiguration(final List<ServerConnectorConfig> connectors) {
        this.connectors = ImmutableList.copyOf(Objects.requireNonNull(connectors));
    }

    public List<ServerConnectorConfig> getServerConnectorsConfig() {
        return connectors;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static class Builder {
        private final List<ServerConnectorConfig> connectors;

        Builder() {
            this.connectors = new ArrayList<>();
        }

        public Builder addServerConnector(final ServerConnectorConfig connector) {
            this.connectors.add(connector);
            return this;
        }

        public JettyServerConfiguration build() {
            return new JettyServerConfiguration(connectors);
        }
    }
}
