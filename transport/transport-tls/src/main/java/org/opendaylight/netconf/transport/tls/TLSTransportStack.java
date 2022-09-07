/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.tls;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ListenableFuture;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import java.util.List;
import java.util.Set;
import javax.net.ssl.SSLException;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.transport.api.AbstractTransportStack;
import org.opendaylight.netconf.transport.api.TransportChannel;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.netconf.transport.tcp.TCPTransportStack;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev220616.CipherSuiteAlgBase;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev220616.TlsAes128CcmSha256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev220616.TlsAes128GcmSha256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev220616.TlsAes256GcmSha384;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev220616.TlsChacha20Poly1305Sha256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev220616.TlsDhePskWithAes128Ccm;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev220616.TlsDhePskWithAes128GcmSha256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev220616.TlsDhePskWithAes256Ccm;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev220616.TlsDhePskWithAes256GcmSha384;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev220616.TlsDhePskWithChacha20Poly1305Sha256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev220616.TlsDheRsaWithAes128Ccm;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev220616.TlsDheRsaWithAes128GcmSha256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev220616.TlsDheRsaWithAes256Ccm;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev220616.TlsDheRsaWithAes256GcmSha384;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev220616.TlsDheRsaWithChacha20Poly1305Sha256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev220616.TlsEcdheEcdsaWithAes128GcmSha256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev220616.TlsEcdheEcdsaWithAes256GcmSha384;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev220616.TlsEcdheEcdsaWithChacha20Poly1305Sha256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev220616.TlsEcdhePskWithAes128CcmSha256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev220616.TlsEcdhePskWithAes128GcmSha256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev220616.TlsEcdhePskWithAes256GcmSha384;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev220616.TlsEcdhePskWithChacha20Poly1305Sha256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev220616.TlsEcdheRsaWithAes128GcmSha256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev220616.TlsEcdheRsaWithAes256GcmSha384;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.tls.cipher.suite.algs.rev220616.TlsEcdheRsaWithChacha20Poly1305Sha256;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.common.rev220718.HelloParamsGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.common.rev220718.TlsVersionBase;
import org.opendaylight.yangtools.yang.common.Empty;

/**
 * Base class for TLS TransportStacks.
 */
public abstract sealed class TLSTransportStack extends AbstractTransportStack permits TLSClient, TLSServer {
    private static final ImmutableMap<CipherSuiteAlgBase, String> CIPHER_SUITES =
        ImmutableMap.<CipherSuiteAlgBase, String>builder()
            .put(TlsAes128CcmSha256.VALUE,                      "TLS_AES_128_CCM_SHA256")
            .put(TlsAes128GcmSha256.VALUE,                      "TLS_AES_128_GCM_SHA256")
            .put(TlsAes256GcmSha384.VALUE,                      "TLS_AES_256_GCM_SHA384")
            .put(TlsChacha20Poly1305Sha256.VALUE,               "TLS_CHACHA20_POLY1305_SHA256")
            .put(TlsDhePskWithAes128Ccm.VALUE,                  "TLS_DHE_PSK_WITH_AES_128_CCM")
            .put(TlsDhePskWithAes128GcmSha256.VALUE,            "TLS_DHE_PSK_WITH_AES_128_GCM_SHA256")
            .put(TlsDhePskWithAes256Ccm.VALUE,                  "TLS_DHE_PSK_WITH_AES_256_CCM")
            .put(TlsDhePskWithAes256GcmSha384.VALUE,            "TLS_DHE_PSK_WITH_AES_256_GCM_SHA384")
            .put(TlsDhePskWithChacha20Poly1305Sha256.VALUE,     "TLS_DHE_PSK_WITH_CHACHA20_POLY1305_SHA256")
            .put(TlsDheRsaWithAes128Ccm.VALUE,                  "TLS_DHE_RSA_WITH_AES_128_CCM")
            .put(TlsDheRsaWithAes128GcmSha256.VALUE,            "TLS_DHE_RSA_WITH_AES_128_GCM_SHA256")
            .put(TlsDheRsaWithAes256Ccm.VALUE,                  "TLS_DHE_RSA_WITH_AES_256_CCM")
            .put(TlsDheRsaWithAes256GcmSha384.VALUE,            "TLS_DHE_RSA_WITH_AES_256_GCM_SHA384")
            .put(TlsDheRsaWithChacha20Poly1305Sha256.VALUE,     "TLS_DHE_RSA_WITH_CHACHA20_POLY1305_SHA256")
            .put(TlsEcdheEcdsaWithAes128GcmSha256.VALUE,        "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256")
            .put(TlsEcdheEcdsaWithAes256GcmSha384.VALUE,        "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384")
            .put(TlsEcdheEcdsaWithChacha20Poly1305Sha256.VALUE, "TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256")
            .put(TlsEcdhePskWithAes128CcmSha256.VALUE,          "TLS_ECDHE_PSK_WITH_AES_128_CCM_SHA256")
            .put(TlsEcdhePskWithAes128GcmSha256.VALUE,          "TLS_ECDHE_PSK_WITH_AES_128_GCM_SHA256")
            .put(TlsEcdhePskWithAes256GcmSha384.VALUE,          "TLS_ECDHE_PSK_WITH_AES_256_GCM_SHA384")
            .put(TlsEcdhePskWithChacha20Poly1305Sha256.VALUE,   "TLS_ECDHE_PSK_WITH_CHACHA20_POLY1305_SHA256")
            .put(TlsEcdheRsaWithAes128GcmSha256.VALUE,          "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256")
            .put(TlsEcdheRsaWithAes256GcmSha384.VALUE,          "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384")
            .put(TlsEcdheRsaWithChacha20Poly1305Sha256.VALUE,   "TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256")
            .build();

    private final @NonNull TCPTransportStack tcpStack;

    private volatile @NonNull SslContext sslContext;

    TLSTransportStack(final TransportChannelListener listener, final TCPTransportStack tcpStack,
            final SslContext sslContext) {
        super(listener);
        this.tcpStack = requireNonNull(tcpStack);
        this.sslContext = requireNonNull(sslContext);
    }

    @Override
    protected final ListenableFuture<Empty> startShutdown() {
        return tcpStack.shutdown();
    }

    final void setSslContext(final SslContext sslContext) {
        this.sslContext = requireNonNull(sslContext);
    }

    // Shoud be TransportChannelListener
    final void onNewChannel(final TransportChannel channel) {
        final var ch = channel.channel();
        final var sslHandler = sslContext.newHandler(ch.alloc());

        ch.pipeline().addLast(sslHandler);
        sslHandler.handshakeFuture().addListener(future -> {
            final var cause = future.cause();
            if (cause != null) {
                listener().onTransportChannelFailed(cause);
                ch.close();
            } else {
                listener().onTransportChannelEstablished(channel);
            }
        });
    }

    static final SslContext buildSslContext(final SslContextBuilder builder, final HelloParamsGrouping helloParams)
            throws UnsupportedConfigurationException {
        if (helloParams != null) {
            final var tlsVersions = helloParams.getTlsVersions();
            if (tlsVersions != null) {
                final var versions = tlsVersions.getTlsVersion();
                if (versions != null && !versions.isEmpty()) {
                    builder.protocols(createTlsStrings(versions));
                }
            }
            final var cipherSuites = helloParams.getCipherSuites();
            if (cipherSuites != null) {
                final var ciphers = cipherSuites.getCipherSuite();
                if (ciphers != null && !ciphers.isEmpty()) {
                    builder.ciphers(createCipherStrings(ciphers));
                }
            }
        }

        try {
            return builder.build();
        } catch (SSLException e) {
            throw new UnsupportedConfigurationException("Cannot instantiate TLS context", e);
        }
    }

    private static String[] createTlsStrings(final Set<TlsVersionBase> versions)
            throws UnsupportedConfigurationException {
        // FIXME: cache these
        final var ret = new String[versions.size()];
        int idx = 0;
        for (var version : versions) {
            final var str = IetfTlsCommonFeatureProvider.algorithmNameOf(version);
            if (str == null) {
                throw new UnsupportedConfigurationException("Unhandled TLS version " + version);
            }
            ret[idx++] = str;
        }
        return ret;
    }

    private static ImmutableList<String> createCipherStrings(final List<CipherSuiteAlgBase> ciphers)
            throws UnsupportedConfigurationException {
        // FIXME: cache these
        final var builder = ImmutableList.<String>builderWithExpectedSize(ciphers.size());
        for (var cipher : ciphers) {
            final var str = CIPHER_SUITES.get(cipher);
            if (str == null) {
                throw new UnsupportedConfigurationException("Unhandled cipher suite " + cipher);
            }
            builder.add(str);
        }
        return builder.build();
    }
}
