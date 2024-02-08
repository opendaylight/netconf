/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static java.util.Objects.requireNonNull;

import java.util.Map;
import java.util.stream.Collectors;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.crypt.hash.rev140806.CryptHash;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev240208.password.grouping.password.type.CleartextPasswordBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208.http.client.identity.grouping.ClientIdentity;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208.http.client.identity.grouping.ClientIdentityBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.http.server.grouping.ClientAuthentication;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.http.server.grouping.ClientAuthenticationBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.http.server.grouping.client.authentication.users.user.auth.type.basic.basic.PasswordBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IetfInetUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yangtools.yang.common.Uint16;

public final class ConfigUtils {

    private ConfigUtils() {
        // utility class
    }

    public static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208
        .http.server.stack.grouping.Transport serverTransportTcp(final @NonNull String host, final int port,
            final @Nullable Map<String, String> userCryptHashMap) {

        final var tcpParams = new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208
            .http.server.stack.grouping.transport.tcp.tcp.TcpServerParametersBuilder()
            .setLocalAddress(IetfInetUtil.ipAddressFor(requireNonNull(host)))
            .setLocalPort(new PortNumber(Uint16.valueOf(port))).build();
        final var httpParams = new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208
            .http.server.stack.grouping.transport.tcp.tcp.HttpServerParametersBuilder()
            .setClientAuthentication(clientAuthentication(userCryptHashMap)).build();
        return serverTransportTcp(tcpParams, httpParams);
    }

    public static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208
        .http.server.stack.grouping.Transport serverTransportTcp(
            final org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208
                .http.server.stack.grouping.transport.tcp.tcp.@NonNull TcpServerParameters tcpParams,
            final org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208
                .http.server.stack.grouping.transport.tcp.tcp.@Nullable HttpServerParameters httpParams) {

        final var tcp = new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208
            .http.server.stack.grouping.transport.tcp.TcpBuilder()
            .setTcpServerParameters(tcpParams).setHttpServerParameters(httpParams).build();
        return new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208
            .http.server.stack.grouping.transport.TcpBuilder().setTcp(tcp).build();
    }

    public static @Nullable ClientAuthentication clientAuthentication(
            final @Nullable Map<String, String> userCryptHashMap) {
        if (userCryptHashMap == null || userCryptHashMap.isEmpty()) {
            return null;
        }
        final var userMap = userCryptHashMap.entrySet().stream()
            .map(entry -> new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208
                .http.server.grouping.client.authentication.users.UserBuilder()
                .setUserId(entry.getKey())
                .setAuthType(
                    new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208
                        .http.server.grouping.client.authentication.users.user.auth.type.BasicBuilder().setBasic(
                            new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208
                                .http.server.grouping.client.authentication.users.user.auth.type.basic.BasicBuilder()
                                .setUsername(entry.getKey())
                                .setPassword(new PasswordBuilder()
                                    .setHashedPassword(new CryptHash(entry.getValue())).build()).build()
                    ).build()).build())
            .collect(Collectors.toMap(user -> user.key(), user -> user));
        return new ClientAuthenticationBuilder()
            .setUsers(new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208
                .http.server.grouping.client.authentication.UsersBuilder().setUser(userMap).build()).build();
    }

    public static @Nullable ClientIdentity clientIdentity(final @Nullable String username,
            final @Nullable String password) {
        if (username == null || password == null) {
            return null;
        }
        return new ClientIdentityBuilder().setAuthType(
            new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208
                .http.client.identity.grouping.client.identity.auth.type.BasicBuilder()
                .setBasic(new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208
                    .http.client.identity.grouping.client.identity.auth.type.basic.BasicBuilder().setUserId(username)
                    .setPasswordType(new CleartextPasswordBuilder().setCleartextPassword(password).build())
                    .build()).build()).build();
    }

    public static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208
        .http.client.stack.grouping.Transport clientTransportTcp(final @NonNull String host, final int port,
            final @Nullable String username, final @Nullable String password) {

        final var tcpParams = new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208
            .http.client.stack.grouping.transport.tcp.tcp.TcpClientParametersBuilder()
            .setRemoteAddress(new Host(IetfInetUtil.ipAddressFor(requireNonNull(host))))
            .setRemotePort(new PortNumber(Uint16.valueOf(port))).build();
        final var httpParams = new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208
            .http.client.stack.grouping.transport.tcp.tcp.HttpClientParametersBuilder()
            .setClientIdentity(clientIdentity(username, password)).build();
        return clientTransportTcp(tcpParams, httpParams);
    }

    public static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208
        .http.client.stack.grouping.Transport clientTransportTcp(
            final org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208
                .http.client.stack.grouping.transport.tcp.tcp.@NonNull TcpClientParameters tcpParams,
            final org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208
                .http.client.stack.grouping.transport.tcp.tcp.@Nullable HttpClientParameters httpParams) {

        final var tcp = new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208
            .http.client.stack.grouping.transport.tcp.TcpBuilder()
            .setTcpClientParameters(tcpParams).setHttpClientParameters(httpParams).build();
        return new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208
            .http.client.stack.grouping.transport.TcpBuilder().setTcp(tcp).build();
    }

    public static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208
        .http.server.stack.grouping.Transport serverTransportTls(
            final org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208
                .http.server.stack.grouping.transport.tls.tls.@NonNull TcpServerParameters tcpParams,
            final org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208
                .http.server.stack.grouping.transport.tls.tls.@NonNull TlsServerParameters tlsParams,
            final org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208
                .http.server.stack.grouping.transport.tls.tls.@Nullable HttpServerParameters httpParams) {

        final var tls = new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208
            .http.server.stack.grouping.transport.tls.TlsBuilder()
            .setTcpServerParameters(tcpParams).setTlsServerParameters(tlsParams)
            .setHttpServerParameters(httpParams).build();
        return new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208
            .http.server.stack.grouping.transport.TlsBuilder().setTls(tls).build();
    }

    public static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208
        .http.client.stack.grouping.Transport clientTransportTls(
        final org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208
            .http.client.stack.grouping.transport.tls.tls.@NonNull TcpClientParameters tcpParams,
        final org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208
            .http.client.stack.grouping.transport.tls.tls.@NonNull TlsClientParameters tlsParams,
        final org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208
            .http.client.stack.grouping.transport.tls.tls.@Nullable HttpClientParameters httpParams) {

        final var tls = new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208
            .http.client.stack.grouping.transport.tls.TlsBuilder()
            .setTcpClientParameters(tcpParams).setTlsClientParameters(tlsParams)
            .setHttpClientParameters(httpParams).build();
        return new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208
            .http.client.stack.grouping.transport.TlsBuilder().setTls(tls).build();
    }
}
