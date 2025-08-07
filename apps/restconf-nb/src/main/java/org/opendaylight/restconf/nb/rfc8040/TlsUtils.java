/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.util.encoders.DecoderException;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// FIXME: inline this class into its sole user
final class TlsUtils {
    private static final Logger LOG = LoggerFactory.getLogger(TlsUtils.class);

    @NonNullByDefault
    record CertificateKey(X509Certificate certificate, PrivateKey privateKey) {
        CertificateKey {
            requireNonNull(certificate);
            requireNonNull(privateKey);
        }
    }

    private TlsUtils() {
        // hidden on purpose
    }

    static @Nullable CertificateKey readCertificateKey(final String certificateFilePath,
            final String privateKeyFilePath) {
        if (certificateFilePath.isEmpty() || privateKeyFilePath.isEmpty()) {
            return null;
        }
        final var certificate = readCertificate(Path.of(certificateFilePath));
        final var privateKey = readPrivateKey(Path.of(privateKeyFilePath));
        if (certificate == null || privateKey == null) {
            LOG.warn(
                "TLS transport configurations for Netty RESTCONF server endpoint s ignored as invalid or incomplete.");
            return null;
        }
        return new CertificateKey(certificate, privateKey);
    }

    private static X509Certificate readCertificate(final Path file) {
        try (var certReader = new PEMParser(Files.newBufferedReader(file))) {
            final var obj = certReader.readObject();
            if (obj instanceof X509CertificateHolder cert) {
                return new JcaX509CertificateConverter().getCertificate(cert);
            }
            // FIXME: NPE if obj == null, which is a valid return
            LOG.warn("Configured certificate file {} contains unexpected object {}. Content ignored as invalid.",
                file.toAbsolutePath(), obj.getClass());
        } catch (NoSuchFileException e) {
            LOG.warn("Configured certificate file {} does not exists", file.toAbsolutePath());
        } catch (IOException | DecoderException | CertificateException e) {
            LOG.warn("Error reading certificate file {} ", file.toAbsolutePath(), e);
        }
        return null;
    }

    private static PrivateKey readPrivateKey(final Path file) {
        try (var keyReader = new PEMParser(Files.newBufferedReader(file))) {
            final var obj = keyReader.readObject();
            switch (obj) {
                case PEMKeyPair keyPair -> {
                    return new JcaPEMKeyConverter().getKeyPair(keyPair).getPrivate();
                }
                case PrivateKeyInfo pkInfo -> {
                    return new JcaPEMKeyConverter().getPrivateKey(pkInfo);
                }
                case null, default -> {
                    // FIXME: NPE if obj == null, which is a valid return
                    LOG.warn(
                        "Configured private key file {} contains unexpected object {}. Content ignored as invalid.",
                        file.toAbsolutePath(), obj.getClass());
                }
            }
        } catch (NoSuchFileException e) {
            LOG.warn("Configured private key file {} file does not exists", file.toAbsolutePath());
        } catch (IOException | DecoderException e) {
            LOG.warn("Error reading private key file {} ", file.toAbsolutePath(), e);
        }
        return null;
    }
}
