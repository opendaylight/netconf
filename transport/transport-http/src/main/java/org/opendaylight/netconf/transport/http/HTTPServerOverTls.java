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
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.util.Map;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.crypt.hash.rev140806.CryptHash;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010.EcPrivateKeyFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010.EndEntityCertCms;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010.RsaPrivateKeyFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010.SubjectPublicKeyInfoFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010._private.key.grouping._private.key.type.CleartextPrivateKeyBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev260402.http.server.listen.stack.grouping.transport.HttpOverTls;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev260402.http.server.listen.stack.grouping.transport.HttpOverTlsBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev260402.http.server.listen.stack.grouping.transport.http.over.tls.http.over.tls.HttpServerParameters;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev260402.http.server.listen.stack.grouping.transport.http.over.tls.http.over.tls.HttpServerParametersBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev260402.http.server.listen.stack.grouping.transport.http.over.tls.http.over.tls.TcpServerParameters;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev260402.http.server.listen.stack.grouping.transport.http.over.tls.http.over.tls.TcpServerParametersBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev260402.http.server.listen.stack.grouping.transport.http.over.tls.http.over.tls.TlsServerParameters;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev260402.http.server.listen.stack.grouping.transport.http.over.tls.http.over.tls.TlsServerParametersBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IetfInetUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev241010.inline.or.keystore.end.entity.cert.with.key.grouping.inline.or.keystore.InlineBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev241010.inline.or.keystore.end.entity.cert.with.key.grouping.inline.or.keystore.inline.InlineDefinitionBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.server.rev241010.tcp.server.grouping.LocalBindBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.server.rev241010.tls.server.grouping.ServerIdentityBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.server.rev241010.tls.server.grouping.server.identity.auth.type.certificate.CertificateBuilder;
import org.opendaylight.yangtools.binding.util.BindingMap;
import org.opendaylight.yangtools.yang.common.Uint16;

/**
 * Utility class for creating {@link HttpOverTls} configurations for {@link HTTPServer}.
 *
 * @since 10.0.3
 */
@Beta
public final class HTTPServerOverTls {
    private HTTPServerOverTls() {
        // Hidden on purpose
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
    public static @NonNull HttpOverTls of(final @NonNull String host, final int port,
            final @NonNull Certificate certificate, final @NonNull PrivateKey privateKey) {
        return of(host, port, certificate, privateKey, null);
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
    public static @NonNull HttpOverTls of(final @NonNull String host, final int port,
            final @NonNull Certificate certificate, final @NonNull PrivateKey privateKey,
            final @Nullable Map<String, String> userCryptHashMap) {
        return of(
            new TcpServerParametersBuilder()
                .setLocalBind(BindingMap.of(new LocalBindBuilder()
                    .setLocalAddress(IetfInetUtil.ipAddressFor(requireNonNull(host)))
                    .setLocalPort(new PortNumber(Uint16.valueOf(port)))
                    .build()))
                .build(),
            new TlsServerParametersBuilder()
                .setServerIdentity(new ServerIdentityBuilder()
                    .setAuthType(new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.server.rev241010
                        .tls.server.grouping.server.identity.auth.type.CertificateBuilder()
                        .setCertificate(new CertificateBuilder()
                            .setInlineOrKeystore(new InlineBuilder()
                                .setInlineDefinition(new InlineDefinitionBuilder()
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
                                    .setCertData(new EndEntityCertCms(ConfigUtils.certificateBytes(certificate)))
                                    .build())
                                .build())
                            .build())
                        .build())
                    .build())
                .build(),
            new HttpServerParametersBuilder()
                .setClientAuthentication(HTTPServerOverTcp.clientAuthentication(userCryptHashMap))
                .build());
    }

    /**
     * Builds transport configuration for {@link HTTPServer} using TLS transport underlay.
     *
     * @param tcpParams TCP layer configuration
     * @param tlsParams TLS layer configuration
     * @param httpParams HTTP layer configuration
     * @return transport configuration
     */
    public static @NonNull HttpOverTls of(final @NonNull TcpServerParameters tcpParams,
            final @NonNull TlsServerParameters tlsParams, final @Nullable HttpServerParameters httpParams) {
        return new HttpOverTlsBuilder()
            .setHttpOverTls(new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev260402
                .http.server.listen.stack.grouping.transport.http.over.tls.HttpOverTlsBuilder()
                .setTcpServerParameters(tcpParams)
                .setTlsServerParameters(tlsParams)
                .setHttpServerParameters(httpParams).build())
            .build();
    }
}
