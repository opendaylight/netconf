/*
 * Copyright © 2019 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.common.configuration;

import com.google.common.base.Preconditions;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nonnull;

/**
 * RESTCONF settings that reflects file "/etc/restconf.cfg".
 */
public class RestconfConfigurationHolder {

    /**
     * Security type - current possible settings - disabled, TLS authentication using certificates and privacy using
     * DH-generated keys.
     */
    public enum SecurityType {
        TLS_AUTH_PRIV("tls-auth-priv"),
        DISABLED("disabled");

        private final String name;

        SecurityType(@Nonnull final String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        @Nonnull
        public static Optional<SecurityType> forName(@Nonnull final String name) {
            Preconditions.checkNotNull(name);
            if (name.equalsIgnoreCase(TLS_AUTH_PRIV.name)) {
                return Optional.of(SecurityType.TLS_AUTH_PRIV);
            } else if (name.equalsIgnoreCase(DISABLED.name)) {
                return Optional.of(SecurityType.DISABLED);
            } else {
                return Optional.empty();
            }
        }

        @Override
        public String toString() {
            return "SecurityType{"
                    + "name='" + name + '\''
                    + '}';
        }
    }

    private final Boolean webSocketEnabled;
    private final Integer webSocketPort;
    private final SecurityType webSocketSecurityType;

    /**
     * Creation of the RESTCONF configuration holder.
     *
     * @param webSocketEnabled      Statement that defines whether web-socket server is enabled.
     * @param webSocketPort         Port on which web-socket server listens.
     * @param webSocketSecurityType Protection level of web-socket server.
     */
    RestconfConfigurationHolder(@Nonnull final Boolean webSocketEnabled,
                                @Nonnull final Integer webSocketPort,
                                @Nonnull final SecurityType webSocketSecurityType) {
        this.webSocketEnabled = Preconditions.checkNotNull(webSocketEnabled);
        this.webSocketPort = Preconditions.checkNotNull(webSocketPort);
        this.webSocketSecurityType = Preconditions.checkNotNull(webSocketSecurityType);
    }

    /**
     * Statement that defines whether web-socket server is enabled.
     */
    @Nonnull
    public Boolean isWebSocketEnabled() {
        return webSocketEnabled;
    }

    /**
     * Web-socket listening TCP port.
     */
    @Nonnull
    public Integer getWebSocketPort() {
        return webSocketPort;
    }

    /**
     * Protection level of web-socket server.
     */
    @Nonnull
    public SecurityType getWebSocketSecurityType() {
        return webSocketSecurityType;
    }

    @Override
    public String toString() {
        return "RestconfConfigurationHolder{"
                + "webSocketEnabled=" + webSocketEnabled
                + ", webSocketPort=" + webSocketPort
                + ", webSocketSecurityType=" + webSocketSecurityType
                + '}';
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        RestconfConfigurationHolder that = (RestconfConfigurationHolder) object;
        return Objects.equals(webSocketEnabled, that.webSocketEnabled)
                && Objects.equals(webSocketPort, that.webSocketPort)
                && webSocketSecurityType == that.webSocketSecurityType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(webSocketEnabled, webSocketPort, webSocketSecurityType);
    }
}