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
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010.EcPrivateKeyFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010.EndEntityCertCms;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010.RsaPrivateKeyFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010.SubjectPublicKeyInfoFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010._private.key.grouping._private.key.type.CleartextPrivateKeyBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev260204.http.server.listen.stack.grouping.transport.HttpOverQuic;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev260204.http.server.listen.stack.grouping.transport.HttpOverQuicBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev260204.http.server.listen.stack.grouping.transport.http.over.quic.http.over.quic.HttpServerParametersBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev260204.http.server.listen.stack.grouping.transport.http.over.quic.http.over.quic.TlsServerParametersBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev260204.http.server.listen.stack.grouping.transport.http.over.quic.http.over.quic.UdpServerParametersBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IetfInetUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev241010.inline.or.keystore.end.entity.cert.with.key.grouping.inline.or.keystore.InlineBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev241010.inline.or.keystore.end.entity.cert.with.key.grouping.inline.or.keystore.inline.InlineDefinitionBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.server.rev241010.tls.server.grouping.ServerIdentityBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.server.rev241010.tls.server.grouping.server.identity.auth.type.certificate.CertificateBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.udp.server.rev251216.udp.server.LocalBindBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.yang.http.server.rev260415.http3.server.grouping.QuicUnderHttpBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.yang.http.server.rev260415.http3.server.grouping.quic.under.http.QuicServerParametersBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.yang.quic.common.rev260415.Varint;
import org.opendaylight.yangtools.binding.util.BindingMap;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.common.Uint64;

/**
 * Utility class for creating {@link HttpOverQuic} configurations for {@link HTTPServer}.
 */
@Beta
public final class HTTPServerOverQuic {
    private HTTPServerOverQuic() {
        // Hidden on purpose
    }

    /**
     * Builds {@link HttpOverQuic} transport configuration for an {@link HTTPServer}.
     *
     * @param host local bind address
     * @param port local bind port
     * @param certificate server certificate
     * @param privateKey server private key
     * @param initialMaxData QUIC {@code initial_max_data}
     * @param initialMaxStreamDataBidirectionalRemote QUIC {@code initial_max_stream_data_bidi_remote}
     * @param initialMaxStreamsBidirectional QUIC {@code initial_max_streams_bidi}
     * @return {@link HttpOverQuic} transport configuration
     */
    public static @NonNull HttpOverQuic of(final @NonNull String host, final int port,
            final @NonNull Certificate certificate, final @NonNull PrivateKey privateKey,
            final @NonNull Uint64 initialMaxData, final @NonNull Uint64 initialMaxStreamDataBidirectionalRemote,
            final @NonNull Uint32 initialMaxStreamsBidirectional) {
        return new HttpOverQuicBuilder()
            .setHttpOverQuic(new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev260204
                .http.server.listen.stack.grouping.transport.http.over.quic.HttpOverQuicBuilder()
                .setUdpServerParameters(new UdpServerParametersBuilder()
                    .setLocalBind(BindingMap.of(new LocalBindBuilder()
                        .setLocalAddress(IetfInetUtil.ipAddressFor(requireNonNull(host)))
                        .setLocalPort(new PortNumber(Uint16.valueOf(port)))
                        .build()))
                    .build())
                .setTlsServerParameters(new TlsServerParametersBuilder()
                    .setServerIdentity(new ServerIdentityBuilder()
                        .setAuthType(new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.server
                            .rev241010.tls.server.grouping.server.identity.auth.type.CertificateBuilder()
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
                    .build())
                .setHttpServerParameters(new HttpServerParametersBuilder().build())
                .build())
            .addAugmentation(new QuicUnderHttpBuilder()
                .setQuicServerParameters(new QuicServerParametersBuilder()
                    .setInitialMaxData(new Varint(initialMaxData))
                    .setInitialMaxStreamDataBidiRemote(new Varint(initialMaxStreamDataBidirectionalRemote))
                    .setInitialMaxStreamsBidi(initialMaxStreamsBidirectional)
                    .build())
                .build())
            .build();
    }
}
