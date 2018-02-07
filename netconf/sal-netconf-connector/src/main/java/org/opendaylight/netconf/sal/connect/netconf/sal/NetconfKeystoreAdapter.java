/*
 * Copyright (c) 2017 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.connect.netconf.sal;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nonnull;
import org.opendaylight.controller.md.sal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.Keystore;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017._private.keys.PrivateKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.keystore.entry.KeyCredential;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.trusted.certificates.TrustedCertificate;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetconfKeystoreAdapter implements ClusteredDataTreeChangeListener<Keystore> {

    private static final Logger LOG = LoggerFactory.getLogger(NetconfKeystoreAdapter.class);

    private final InstanceIdentifier<Keystore> keystoreIid = InstanceIdentifier.create(Keystore.class);

    private final DataBroker dataBroker;
    private final Map<String, KeyCredential> pairs = Collections.synchronizedMap(new HashMap<>());
    private final Map<String, PrivateKey> privateKeys = Collections.synchronizedMap(new HashMap<>());
    private final Map<String, TrustedCertificate> trustedCertificates = Collections.synchronizedMap(new HashMap<>());

    public NetconfKeystoreAdapter(final DataBroker dataBroker) {
        this.dataBroker = dataBroker;

        dataBroker.registerDataTreeChangeListener(
                new DataTreeIdentifier<>(LogicalDatastoreType.CONFIGURATION, keystoreIid), this);
    }

    public Optional<KeyCredential> getKeypairFromId(final String keyId) {
        final KeyCredential keypair = pairs.get(keyId);
        return Optional.ofNullable(keypair);
    }

    /**
     * Using private keys and trusted certificates to create a new JDK <code>KeyStore</code> which
     * will be used by TLS clients to create <code>SSLEngine</code>. The private keys are essential
     * to create JDK <code>KeyStore</code> while the trusted certificates are optional.
     *
     * @return A JDK KeyStore object
     * @throws GeneralSecurityException If any security exception occurred
     * @throws IOException If there is an I/O problem with the keystore data
     */
    public java.security.KeyStore getJavaKeyStore() throws GeneralSecurityException, IOException {
        final java.security.KeyStore keyStore = java.security.KeyStore.getInstance("JKS");

        keyStore.load(null, null);

        synchronized (privateKeys) {
            if (privateKeys.isEmpty()) {
                throw new KeyStoreException("No keystore private key found");
            }

            for (Map.Entry<String, PrivateKey> entry : privateKeys.entrySet()) {
                final java.security.PrivateKey key = getJavaPrivateKey(entry.getValue().getData());

                final List<X509Certificate> certificateChain =
                        getCertificateChain(entry.getValue().getCertificateChain().toArray(new String[0]));
                if (certificateChain.isEmpty()) {
                    throw new CertificateException("No certificate chain associated with private key found");
                }

                keyStore.setKeyEntry(entry.getKey(), key, "".toCharArray(),
                        certificateChain.stream().toArray(Certificate[]::new));
            }
        }

        synchronized (trustedCertificates) {
            for (Map.Entry<String, TrustedCertificate> entry : trustedCertificates.entrySet()) {
                final List<X509Certificate> x509Certificates =
                        getCertificateChain(new String[] {entry.getValue().getCertificate()});

                keyStore.setCertificateEntry(entry.getKey(), x509Certificates.get(0));
            }
        }

        return keyStore;
    }

    private java.security.PrivateKey getJavaPrivateKey(final String base64PrivateKey)
            throws GeneralSecurityException {
        final byte[] encodedKey = base64Decode(base64PrivateKey);
        final PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encodedKey);
        java.security.PrivateKey key;

        try {
            final KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            key = keyFactory.generatePrivate(keySpec);
        } catch (InvalidKeySpecException ignore) {
            final KeyFactory keyFactory = KeyFactory.getInstance("DSA");
            key = keyFactory.generatePrivate(keySpec);
        }

        return key;
    }

    private List<X509Certificate> getCertificateChain(final String[] base64Certificates)
            throws GeneralSecurityException {
        final CertificateFactory factory = CertificateFactory.getInstance("X.509");
        final List<X509Certificate> certificates = new ArrayList<>();

        for (String cert : base64Certificates) {
            final byte[] buffer = base64Decode(cert);
            certificates.add((X509Certificate)factory.generateCertificate(new ByteArrayInputStream(buffer)));
        }

        return certificates;
    }

    private byte[] base64Decode(final String base64) {
        return Base64.getMimeDecoder().decode(base64.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }

    @Override
    public void onDataTreeChanged(@Nonnull final Collection<DataTreeModification<Keystore>> changes) {
        LOG.debug("Keystore updated: {}", changes);

        for (DataTreeModification<Keystore> change : changes) {
            final DataObjectModification<Keystore> rootNode = change.getRootNode();
            switch (rootNode.getModificationType()) {
                case WRITE:
                case SUBTREE_MODIFIED:
                    LOG.debug("Keystore subtree modified: {}", change);

                    for (DataObjectModification<? extends DataObject> child : rootNode.getModifiedChildren()) {
                        if (child.getDataType().equals(KeyCredential.class)) {
                            final Keystore dataAfter = changes.iterator().next().getRootNode().getDataAfter();

                            pairs.clear();
                            if (dataAfter != null) {
                                dataAfter.getKeyCredential().forEach(pair -> pairs.put(pair.getKey().getKeyId(), pair));
                            }
                            return;
                        } else {
                            processChild(child);
                        }
                    }
                    break;

                case DELETE:
                    LOG.debug("Keystore delete: {}", change);
                    break;

                default:
                    break;
            }
        }
    }

    private void processChild(final DataObjectModification<? extends DataObject> objectMod) {
        final Class<?> clazz = objectMod.getDataType();

        switch (objectMod.getModificationType()) {
            case WRITE:
            case SUBTREE_MODIFIED:
                final DataObject objectWrite = objectMod.getDataAfter();

                if (clazz.equals(PrivateKey.class)) {
                    final PrivateKey key = (PrivateKey)objectWrite;
                    privateKeys.put(key.getName(), key);
                } else if (clazz.equals(TrustedCertificate.class)) {
                    final TrustedCertificate certificate = (TrustedCertificate)objectWrite;
                    trustedCertificates.put(certificate.getName(), certificate);
                }
                break;

            case DELETE:
                final DataObject objectDelete = objectMod.getDataBefore();

                if (clazz.equals(PrivateKey.class)) {
                    final PrivateKey key = (PrivateKey)objectDelete;
                    privateKeys.remove(key.getName());
                } else if (clazz.equals(TrustedCertificate.class)) {
                    final TrustedCertificate certificate = (TrustedCertificate)objectDelete;
                    trustedCertificates.remove(certificate.getName());
                }
                break;

            default:
                break;
        }
    }
}
