/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.Beta;
import java.util.Map;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.crypt.hash.rev140806.CryptHash;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev251111.http.server.listen.stack.grouping.transport.HttpOverTcp;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev251111.http.server.listen.stack.grouping.transport.HttpOverTcpBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev251111.http.server.listen.stack.grouping.transport.http.over.tcp.http.over.tcp.HttpServerParameters;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev251111.http.server.listen.stack.grouping.transport.http.over.tcp.http.over.tcp.HttpServerParametersBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev251111.http.server.listen.stack.grouping.transport.http.over.tcp.http.over.tcp.TcpServerParameters;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev251111.http.server.listen.stack.grouping.transport.http.over.tcp.http.over.tcp.TcpServerParametersBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IetfInetUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.server.rev241010.tcp.server.grouping.LocalBindBuilder;
import org.opendaylight.yangtools.binding.util.BindingMap;
import org.opendaylight.yangtools.yang.common.Uint16;

/**
 * Utility class for creating {@link HttpOverTcp} configurations for {@link HTTPServer}.
 */
@Beta
public final class HTTPServerOverTcp {
    private HTTPServerOverTcp() {
        // Hidden on purpose
    }

    /**
     * Builds transport configuration for {@link HTTPServer} using TCP transport underlay with no authorization.
     *
     * @param host local address
     * @param port local port
     * @return transport configuration
     */
    public static @NonNull HttpOverTcp of(final @NonNull String host, final int port) {
        return of(host, port, null);
    }

    /**
     * Builds transport configuration for {@link HTTPServer} using TCP transport underlay with Basic Authorization.
     *
     * @param host local address
     * @param port local port
     * @param userCryptHashMap user credentials map for Basic Authorization where key is username and value is a
     *      {@link CryptHash} value for user password
     * @return transport configuration
     */
    public static @NonNull HttpOverTcp of(final @NonNull String host, final int port,
            final @Nullable Map<String, String> userCryptHashMap) {
        return of(
            new TcpServerParametersBuilder()
                .setLocalBind(BindingMap.of(new LocalBindBuilder()
                    .setLocalAddress(IetfInetUtil.ipAddressFor(requireNonNull(host)))
                    .setLocalPort(new PortNumber(Uint16.valueOf(port)))
                    .build()))
                .build(),
            new HttpServerParametersBuilder()
                .setClientAuthentication(ConfigUtils.clientAuthentication(userCryptHashMap))
                .build());
    }

    /**
     * Builds transport configuration for {@link HTTPServer} using TCP transport underlay.
     *
     * @param tcpParams TCP layer configuration
     * @param httpParams HTTP layer configuration
     * @return transport configuration
     */
    static @NonNull HttpOverTcp of(final @NonNull TcpServerParameters tcpParams,
            final @Nullable HttpServerParameters httpParams) {
        return new HttpOverTcpBuilder()
            .setHttpOverTcp(new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev251111
                .http.server.listen.stack.grouping.transport.http.over.tcp.HttpOverTcpBuilder()
                .setTcpServerParameters(tcpParams)
                .setHttpServerParameters(httpParams)
                .build())
            .build();
    }
}
