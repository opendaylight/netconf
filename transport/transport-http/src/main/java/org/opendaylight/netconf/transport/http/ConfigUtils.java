/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.util.Map;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.crypt.hash.rev140806.CryptHash;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev240208.EcPrivateKeyFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev240208.EndEntityCertCms;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev240208.RsaPrivateKeyFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev240208.SubjectPublicKeyInfoFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev240208.TrustAnchorCertCms;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev240208._private.key.grouping._private.key.type.CleartextPrivateKeyBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev240208.password.grouping.password.type.CleartextPasswordBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208.http.client.identity.grouping.ClientIdentity;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208.http.client.identity.grouping.ClientIdentityBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.http.server.grouping.ClientAuthentication;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.http.server.grouping.ClientAuthenticationBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.http.server.grouping.client.authentication.users.user.auth.type.basic.basic.PasswordBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IetfInetUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.client.rev240208.tls.client.grouping.ServerAuthenticationBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.client.rev240208.tls.client.grouping.server.authentication.EeCertsBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.server.rev240208.tls.server.grouping.ServerIdentityBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.truststore.rev240208.inline.or.truststore.certs.grouping.inline.or.truststore.inline.inline.definition.CertificateBuilder;
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
    public static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.http.server.stack
            .grouping.transport.Tcp serverTransportTcp(final @NonNull String host, final int port) {
        return serverTransportTcp(host, port, null);
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
    public static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.http.server.stack
            .grouping.transport.Tcp serverTransportTcp(final @NonNull String host, final int port,
                final @Nullable Map<String, String> userCryptHashMap) {
        return new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.http.server.stack
            .grouping.transport.TcpBuilder()
            .setTcp(new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.http.server
                .stack.grouping.transport.tcp.TcpBuilder()
                .setTcpServerParameters(new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server
                    .rev240208.http.server.stack.grouping.transport.tcp.tcp.TcpServerParametersBuilder()
                    .setLocalAddress(IetfInetUtil.ipAddressFor(host))
                    .setLocalPort(new PortNumber(Uint16.valueOf(port)))
                    .build())
                .setHttpServerParameters(new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server
                    .rev240208.http.server.stack.grouping.transport.tcp.tcp.HttpServerParametersBuilder()
                    .setClientAuthentication(clientAuthentication(userCryptHashMap))
                    .build())
                .build())
            .build();
    }

    /**
     * Builds transport configuration for {@link HTTPClient} using TCP transport underlay with no authorization.
     *
     * @param host remote address
     * @param port remote port
     * @return transport configuration
     */
    public static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208.http.client.stack
            .grouping.transport.Tcp clientTransportTcp(final @NonNull String host, final int port) {
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
    public static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208.http.client.stack
            .grouping.transport.Tcp clientTransportTcp(final @NonNull String host, final int port,
                final @Nullable String username, final @Nullable String password) {
        return new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208
            .http.client.stack.grouping.transport.TcpBuilder()
            .setTcp(new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208.http.client
                .stack.grouping.transport.tcp.TcpBuilder()
                .setTcpClientParameters(new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client
                    .rev240208.http.client.stack.grouping.transport.tcp.tcp.TcpClientParametersBuilder()
                    .setRemoteAddress(new Host(IetfInetUtil.ipAddressFor(host)))
                    .setRemotePort(new PortNumber(Uint16.valueOf(port)))
                    .build())
                .setHttpClientParameters(new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client
                    .rev240208.http.client.stack.grouping.transport.tcp.tcp.HttpClientParametersBuilder()
                    .setClientIdentity(clientIdentity(username, password))
                    .build())
                .build())
            .build();
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
    public static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.http.server.stack
            .grouping.transport.Tls serverTransportTls(final @NonNull String host, final int port,
                final @NonNull Certificate certificate, final @NonNull PrivateKey privateKey) {
        return serverTransportTls(host, port, certificate, privateKey, null);
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
    public static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.http.server.stack
            .grouping.transport.Tls serverTransportTls(final @NonNull String host, final int port,
                final @NonNull Certificate certificate, final @NonNull PrivateKey privateKey,
                final @Nullable Map<String, String> userCryptHashMap) {
        return new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.http.server.stack
            .grouping.transport.TlsBuilder()
            .setTls(new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208
                .http.server.stack.grouping.transport.tls.TlsBuilder()
                .setTcpServerParameters(new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server
                    .rev240208.http.server.stack.grouping.transport.tls.tls.TcpServerParametersBuilder()
                    .setLocalAddress(IetfInetUtil.ipAddressFor(host))
                    .setLocalPort(new PortNumber(Uint16.valueOf(port)))
                    .build())
                .setTlsServerParameters(new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server
                    .rev240208.http.server.stack.grouping.transport.tls.tls.TlsServerParametersBuilder()
                    .setServerIdentity(new ServerIdentityBuilder()
                        .setAuthType(new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.server
                            .rev240208.tls.server.grouping.server.identity.auth.type.CertificateBuilder()
                            .setCertificate(new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.server
                                .rev240208.tls.server.grouping.server.identity.auth.type.certificate
                                .CertificateBuilder()
                                .setInlineOrKeystore(new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf
                                    .keystore.rev240208.inline.or.keystore.end.entity.cert.with.key.grouping.inline.or
                                    .keystore.InlineBuilder()
                                    .setInlineDefinition(new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang
                                        .ietf.keystore.rev240208.inline.or.keystore.end.entity.cert.with.key.grouping
                                        .inline.or.keystore.inline.InlineDefinitionBuilder()
                                        .setPublicKeyFormat(SubjectPublicKeyInfoFormat.VALUE)
                                        .setPublicKey(certificate.getPublicKey().getEncoded())
                                        .setPrivateKeyFormat(switch (privateKey.getAlgorithm()) {
                                            case "RSA" -> RsaPrivateKeyFormat.VALUE;
                                            case "EC" -> EcPrivateKeyFormat.VALUE;
                                            default -> throw new IllegalArgumentException(
                                                "Only RSA and EC algorithms are supported for private key");
                                        })
                                        .setPrivateKeyType(new CleartextPrivateKeyBuilder()
                                            .setCleartextPrivateKey(privateKey.getEncoded())
                                            .build())
                                        .setCertData(new EndEntityCertCms(certificateBytes(certificate)))
                                        .build())
                                    .build())
                                .build())
                            .build())
                        .build())
                    .build())
                .setHttpServerParameters(new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server
                    .rev240208.http.server.stack.grouping.transport.tls.tls.HttpServerParametersBuilder()
                    .setClientAuthentication(clientAuthentication(userCryptHashMap))
                    .build())
                .build())
            .build();
    }

    /**
     * Builds transport configuration for {@link HTTPClient} using TLS transport underlay with no authorization.
     *
     * @param host remote address
     * @param port remote port
     * @param certificate server certificate
     * @return transport configuration
     */
    public static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208.http.client.stack
            .grouping.transport.Tls clientTransportTls(@NonNull final String host, final int port,
                @NonNull final Certificate certificate) {
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
    public static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208.http.client.stack
            .grouping.transport.Tls clientTransportTls(@NonNull final String host, final int port,
                @NonNull final Certificate certificate, @Nullable final String username,
                @Nullable final String password) {
        return new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208.http.client.stack
            .grouping.transport.TlsBuilder()
            .setTls(new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208.http.client
                .stack.grouping.transport.tls.TlsBuilder()
                .setTcpClientParameters(new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client
                    .rev240208.http.client.stack.grouping.transport.tls.tls.TcpClientParametersBuilder()
                    .setRemoteAddress(new Host(IetfInetUtil.ipAddressFor(host)))
                    .setRemotePort(new PortNumber(Uint16.valueOf(port)))
                    .build())
                .setTlsClientParameters(new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client
                    .rev240208.http.client.stack.grouping.transport.tls.tls.TlsClientParametersBuilder()
                    .setServerAuthentication(new ServerAuthenticationBuilder()
                        .setEeCerts(new EeCertsBuilder()
                            .setInlineOrTruststore(new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf
                                .truststore.rev240208.inline.or.truststore.certs.grouping.inline.or.truststore
                                .InlineBuilder()
                                .setInlineDefinition(new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf
                                    .truststore.rev240208.inline.or.truststore.certs.grouping.inline.or.truststore
                                    .inline.InlineDefinitionBuilder()
                                    .setCertificate(BindingMap.of(new CertificateBuilder()
                                        .setName("certificate")
                                        .setCertData(new TrustAnchorCertCms(certificateBytes(certificate)))
                                        .build()))
                                    .build())
                                .build())
                            .build())
                        .build())
                    .build())
                .setHttpClientParameters(new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client
                    .rev240208.http.client.stack.grouping.transport.tls.tls.HttpClientParametersBuilder()
                    .setClientIdentity(clientIdentity(username, password))
                    .build())
                .build())
            .build();
    }

    private static @Nullable ClientAuthentication clientAuthentication(
            final @Nullable Map<String, String> userCryptHashMap) {
        if (userCryptHashMap == null || userCryptHashMap.isEmpty()) {
            return null;
        }
        return new ClientAuthenticationBuilder()
            .setUsers(new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.http
                .server.grouping.client.authentication.UsersBuilder()
                .setUser(userCryptHashMap.entrySet().stream()
                    .map(entry -> new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server
                        .rev240208.http.server.grouping.client.authentication.users.UserBuilder()
                        .setUserId(entry.getKey())
                        .setAuthType(new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server
                            .rev240208.http.server.grouping.client.authentication.users.user.auth.type.BasicBuilder()
                            .setBasic(new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server
                                .rev240208.http.server.grouping.client.authentication.users.user.auth.type.basic
                                .BasicBuilder()
                                .setUsername(entry.getKey())
                                .setPassword(new PasswordBuilder()
                                    .setHashedPassword(new CryptHash(entry.getValue()))
                                    .build())
                                .build())
                            .build())
                        .build())
                    .collect(BindingMap.toMap()))
                .build())
            .build();
    }

    private static @Nullable ClientIdentity clientIdentity(final @Nullable String username,
            final @Nullable String password) {
        if (username == null || password == null) {
            return null;
        }
        return new ClientIdentityBuilder()
            .setAuthType(new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208.http
                .client.identity.grouping.client.identity.auth.type.BasicBuilder()
                .setBasic(new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208
                    .http.client.identity.grouping.client.identity.auth.type.basic.BasicBuilder()
                    .setUserId(username)
                    .setPasswordType(new CleartextPasswordBuilder().setCleartextPassword(password).build())
                    .build())
                .build())
            .build();
    }

    private static byte[] certificateBytes(final Certificate certificate) {
        try {
            return certificate.getEncoded();
        } catch (CertificateEncodingException e) {
            throw new IllegalArgumentException("Certificate bytes are ", e);
        }
    }
}
