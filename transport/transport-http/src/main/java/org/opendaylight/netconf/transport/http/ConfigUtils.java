/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static java.util.Objects.requireNonNull;

import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.util.Map;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.crypt.hash.rev140806.CryptHash;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010.TrustAnchorCertCms;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010.password.grouping.password.type.CleartextPasswordBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208.http.client.identity.grouping.ClientIdentity;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208.http.client.identity.grouping.ClientIdentityBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208.http.client.identity.grouping.client.identity.auth.type.basic.BasicBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208.http.client.stack.grouping.Transport;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208.http.client.stack.grouping.transport.tcp.tcp.TcpClientParametersBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev251111.http.server.listen.stack.grouping.transport.HttpOverTcp;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev251111.http.server.listen.stack.grouping.transport.HttpOverTls;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IetfInetUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.client.rev241010.tls.client.grouping.ServerAuthentication;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.client.rev241010.tls.client.grouping.ServerAuthenticationBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.client.rev241010.tls.client.grouping.server.authentication.EeCertsBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.truststore.rev241010.inline.or.truststore.certs.grouping.inline.or.truststore.InlineBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.truststore.rev241010.inline.or.truststore.certs.grouping.inline.or.truststore.inline.InlineDefinitionBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.truststore.rev241010.inline.or.truststore.certs.grouping.inline.or.truststore.inline.inline.definition.CertificateBuilder;
import org.opendaylight.yangtools.binding.util.BindingMap;
import org.opendaylight.yangtools.yang.common.Uint16;

/**
 * Collection of methods to simplify HTTP transport configuration building.
 */
public final class ConfigUtils {
    private ConfigUtils() {
        // utility class
    }

    /**
     * Builds transport configuration for {@link HTTPServer} using TCP transport underlay with no authorization.
     *
     * @param host local address
     * @param port local port
     * @return transport configuration
     */
    @Deprecated(since = "10.0.3", forRemoval = true)
    public static HttpOverTcp serverTransportTcp(final @NonNull String host, final int port) {
        return HTTPServerOverTcp.of(host, port, null);
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
    @Deprecated(since = "10.0.3", forRemoval = true)
    public static HttpOverTcp serverTransportTcp(final @NonNull String host, final int port,
            final @Nullable Map<String, String> userCryptHashMap) {
        return HTTPServerOverTcp.of(host, port, userCryptHashMap);
    }

    /**
     * Builds transport configuration for {@link HTTPServer} using TCP transport underlay.
     *
     * @param tcpParams TCP layer configuration
     * @param httpParams HTTP layer configuration
     * @return transport configuration
     */
    @Deprecated(since = "10.0.3", forRemoval = true)
    public static HttpOverTcp serverTransportTcp(
            final org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev251111.http.server.listen
                .stack.grouping.transport.http.over.tcp.http.over.tcp.@NonNull TcpServerParameters tcpParams,
            final org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev251111.http.server.listen
                .stack.grouping.transport.http.over.tcp.http.over.tcp.@Nullable HttpServerParameters httpParams) {
        return HTTPServerOverTcp.of(tcpParams, httpParams);
    }

    /**
     * Builds transport configuration for {@link HTTPClient} using TCP transport underlay with no authorization.
     *
     * @param host remote address
     * @param port remote port
     * @return transport configuration
     */
    public static Transport clientTransportTcp(final @NonNull String host, final int port) {
        return clientTransportTcp(host, port, null, null);
    }

    /**
     * Builds transport configuration for {@link HTTPClient} using TCP transport underlay with Basic Authorization.
     *
     * @param host remote address
     * @param port remote port
     * @param username username
     * @param password password
     * @return transport configuration
     */
    public static Transport clientTransportTcp(final @NonNull String host, final int port,
            final @Nullable String username, final @Nullable String password) {
        return clientTransportTcp(
            new TcpClientParametersBuilder()
                .setRemoteAddress(new Host(IetfInetUtil.ipAddressFor(requireNonNull(host))))
                .setRemotePort(new PortNumber(Uint16.valueOf(port)))
                .build(),
            new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208
                .http.client.stack.grouping.transport.tcp.tcp.HttpClientParametersBuilder()
                .setClientIdentity(clientIdentity(username, password))
                .build());
    }

    /**
     * Builds transport configuration for {@link HTTPClient} using TCP transport underlay with no authorization.
     *
     * @param tcpParams TCP layer configuration
     * @param httpParams HTTP layer configuration
     * @return transport configuration
     */
    public static Transport clientTransportTcp(
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

    /**
     * Builds transport configuration for {@link HTTPServer} using TLS transport underlay with no authorization.
     *
     * @param host local address
     * @param port local port
     * @param certificate server X509 certificate
     * @param privateKey server private key
     * @return transport configuration
     */
    @Deprecated(since = "10.0.3", forRemoval = true)
    public static HttpOverTls serverTransportTls(final @NonNull String host, final int port,
            final @NonNull Certificate certificate, final @NonNull PrivateKey privateKey) {
        return HTTPServerOverTls.of(host, port, certificate, privateKey);
    }

    /**
     * Builds transport configuration for {@link HTTPServer} using TLS transport underlay with Basic Authorization.
     *
     * @param host local address
     * @param port local port
     * @param certificate server X509 certificate
     * @param privateKey server private key
     * @param userCryptHashMap user credentials map for Basic Authorization where key is username and value is a
     *      {@link CryptHash} value for user password
     * @return transport configuration
     */
    @Deprecated(since = "10.0.3", forRemoval = true)
    public static HttpOverTls serverTransportTls(final @NonNull String host, final int port,
            final @NonNull Certificate certificate, final @NonNull PrivateKey privateKey,
            final @Nullable Map<String, String> userCryptHashMap) {
        return HTTPServerOverTls.of(host, port, certificate, privateKey, userCryptHashMap);
    }

    /**
     * Builds transport configuration for {@link HTTPServer} using TLS transport underlay.
     *
     * @param tcpParams TCP layer configuration
     * @param tlsParams TLS layer configuration
     * @param httpParams HTTP layer configuration
     * @return transport configuration
     */
    @Deprecated(since = "10.0.3", forRemoval = true)
    public static HttpOverTls serverTransportTls(
            final org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev251111.http.server.listen
                .stack.grouping.transport.http.over.tls.http.over.tls.@NonNull TcpServerParameters tcpParams,
            final org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev251111.http.server.listen
                .stack.grouping.transport.http.over.tls.http.over.tls.@NonNull TlsServerParameters tlsParams,
            final org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev251111.http.server.listen
                .stack.grouping.transport.http.over.tls.http.over.tls.@Nullable HttpServerParameters httpParams) {
        return HTTPServerOverTls.of(tcpParams, tlsParams, httpParams);
    }

    /**
     * Builds transport configuration for {@link HTTPClient} using TLS transport underlay with no authorization.
     *
     * @param host remote address
     * @param port remote port
     * @param certificate server certificate
     * @return transport configuration
     */
    public static Transport clientTransportTls(final @NonNull String host, final int port,
            final @NonNull Certificate certificate) {
        return clientTransportTls(host, port, certificate, null, null);
    }

    /**
     * Builds transport configuration for {@link HTTPClient} using TLS transport underlay with Basic Authorization.
     *
     * @param host remote address
     * @param port remote port
     * @param certificate server certificate
     * @param username username
     * @param password password
     * @return transport configuration
     */
    public static Transport clientTransportTls(final @NonNull String host, final int port,
            final @NonNull Certificate certificate, final @Nullable String username, final @Nullable String password) {

        final var tcpParams = new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208
            .http.client.stack.grouping.transport.tls.tls.TcpClientParametersBuilder()
            .setRemoteAddress(new Host(IetfInetUtil.ipAddressFor(requireNonNull(host))))
            .setRemotePort(new PortNumber(Uint16.valueOf(port))).build();
        final var tlsParams = new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208
            .http.client.stack.grouping.transport.tls.tls.TlsClientParametersBuilder()
            .setServerAuthentication(serverAuthentication(requireNonNull(certificate))).build();
        final var httpParams = new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208
            .http.client.stack.grouping.transport.tls.tls.HttpClientParametersBuilder()
            .setClientIdentity(clientIdentity(username, password)).build();
        return clientTransportTls(tcpParams, tlsParams, httpParams);
    }

    /**
     * Builds transport configuration for {@link HTTPClient} using TLS transport.
     *
     * @param tcpParams TCP layer configuration
     * @param tlsParams TLS layer configuration
     * @param httpParams HTTP layer configuration
     * @return transport configuration
     */
    public static Transport clientTransportTls(
            final org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208
                .http.client.stack.grouping.transport.tls.tls.@NonNull TcpClientParameters tcpParams,
            final org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208
                .http.client.stack.grouping.transport.tls.tls.@NonNull TlsClientParameters tlsParams,
            final org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208
                .http.client.stack.grouping.transport.tls.tls.@Nullable HttpClientParameters httpParams) {
        return new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208
            .http.client.stack.grouping.transport.TlsBuilder()
            .setTls(new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208.http.client
                .stack.grouping.transport.tls.TlsBuilder()
                .setTcpClientParameters(tcpParams)
                .setTlsClientParameters(tlsParams)
                .setHttpClientParameters(httpParams)
                .build())
            .build();
    }

    private static @Nullable ClientIdentity clientIdentity(final @Nullable String username,
            final @Nullable String password) {
        if (username == null || password == null) {
            return null;
        }
        return new ClientIdentityBuilder()
            .setAuthType(new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208
                .http.client.identity.grouping.client.identity.auth.type.BasicBuilder()
                .setBasic(new BasicBuilder()
                    .setUserId(username)
                    .setPasswordType(new CleartextPasswordBuilder().setCleartextPassword(password).build())
                    .build())
                .build())
            .build();
    }

    private static ServerAuthentication serverAuthentication(final Certificate certificate) {
        return new ServerAuthenticationBuilder()
            .setEeCerts(new EeCertsBuilder()
                .setInlineOrTruststore(new InlineBuilder()
                    .setInlineDefinition(new InlineDefinitionBuilder()
                        .setCertificate(BindingMap.of(new CertificateBuilder()
                            .setName("certificate")
                            .setCertData(new TrustAnchorCertCms(certificateBytes(certificate)))
                            .build()))
                        .build())
                    .build())
                .build())
            .build();
    }

    static byte[] certificateBytes(final Certificate certificate) {
        try {
            return certificate.getEncoded();
        } catch (CertificateEncodingException e) {
            throw new IllegalArgumentException("Certificate bytes are ", e);
        }
    }
}
