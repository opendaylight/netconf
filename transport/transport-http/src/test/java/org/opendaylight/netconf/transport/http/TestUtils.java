/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static org.opendaylight.netconf.transport.http.AbstractBasicAuthHandler.BASIC_AUTH_PREFIX;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import java.io.IOException;
import java.math.BigInteger;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.RSAKeyGenParameterSpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

final class TestUtils {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private TestUtils() {
        // hidden on purpose
    }

    static int freePort() {
        // find free port
        try {
            final var socket = new ServerSocket(0);
            final var localPort = socket.getLocalPort();
            socket.close();
            return localPort;
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    static String basicAuthHeader(final String username, final String password) {
        return BASIC_AUTH_PREFIX + Base64.getEncoder()
            .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    static HttpRequest httpRequest(final String authHeader) {
        return httpRequest("/uri", authHeader);
    }

    static HttpRequest httpRequest(final String uri, final String authHeader) {
        final var request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri);
        if (authHeader != null) {
            request.headers().add(HttpHeaderNames.AUTHORIZATION, authHeader);
        }
        return request;
    }

    static ListenableFuture<FullHttpResponse> invoke(final HTTPClient client, final FullHttpRequest request) {
        final var future = SettableFuture.<FullHttpResponse>create();
        client.invoke(request, new FutureCallback<>() {
            @Override
            public void onSuccess(final FullHttpResponse response) {
                // To simplify the test code we use future on top of callback.
                // Due to content of response object is released (cleared) on exit from this method
                // we need to ensure the content is copied channel independent byte buffer.
                // Using response.copy() is not suitable because it uses same byte buf allocator
                // as the original message, this may result ResourceLeakDetector exception
                // if copied content buffer is not released (read) in a moment when
                // byte buf allocator's garbage collector is called.
                final var copy = new DefaultFullHttpResponse(response.protocolVersion(), response.status());
                copy.headers().set(response.headers());
                copy.replace(Unpooled.wrappedBuffer(ByteBufUtil.getBytes(response.content())));
                future.set(copy);
            }

            @Override
            public void onFailure(final Throwable cause) {
                future.setException(cause);
            }
        });
        return future;
    }

    static X509CertData generateX509CertData(final String algorithm) throws Exception {
        final var keyPairGenerator = KeyPairGenerator.getInstance(algorithm);
        if (isRSA(algorithm)) {
            keyPairGenerator.initialize(new RSAKeyGenParameterSpec(2048, RSAKeyGenParameterSpec.F4), SECURE_RANDOM);
        } else {
            keyPairGenerator.initialize(new ECGenParameterSpec("secp256r1"), SECURE_RANDOM);
        }
        final var keyPair = keyPairGenerator.generateKeyPair();
        final var certificate = generateCertificate(keyPair, isRSA(algorithm) ? "SHA256withRSA" : "SHA256withECDSA");
        return new X509CertData(certificate, keyPair.getPrivate());
    }

    static X509Certificate generateCertificate(final KeyPair keyPair, final String hashAlgorithm) throws Exception {
        final var now = Instant.now();
        final var contentSigner = new JcaContentSignerBuilder(hashAlgorithm).build(keyPair.getPrivate());

        final var x500Name = new X500Name("CN=TestCertificate");
        final var certificateBuilder = new JcaX509v3CertificateBuilder(x500Name,
            BigInteger.valueOf(now.toEpochMilli()),
            Date.from(now), Date.from(now.plus(Duration.ofDays(365))),
            x500Name,
            keyPair.getPublic());
        return new JcaX509CertificateConverter()
            .setProvider(new BouncyCastleProvider()).getCertificate(certificateBuilder.build(contentSigner));
    }

    static boolean isRSA(final String algorithm) {
        return "RSA".equals(algorithm);
    }

    record X509CertData(X509Certificate certificate, PrivateKey privateKey) {
    }
}
