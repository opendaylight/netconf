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
import org.opendaylight.mdsal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.DataObjectModification;
import org.opendaylight.mdsal.binding.api.DataTreeIdentifier;
import org.opendaylight.mdsal.binding.api.DataTreeModification;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
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

        dataBroker.registerDataTreeChangeListener(DataTreeIdentifier.create(LogicalDatastoreType.CONFIGURATION,
            keystoreIid), this);
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

    private static java.security.PrivateKey getJavaPrivateKey(final String base64PrivateKey)
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

    private static List<X509Certificate> getCertificateChain(final String[] base64Certificates)
            throws GeneralSecurityException {
        final CertificateFactory factory = CertificateFactory.getInstance("X.509");
        final List<X509Certificate> certificates = new ArrayList<>();

        for (String cert : base64Certificates) {
            final byte[] buffer = base64Decode(cert);
            certificates.add((X509Certificate)factory.generateCertificate(new ByteArrayInputStream(buffer)));
        }

        return certificates;
    }

    private static byte[] base64Decode(final String base64) {
        return Base64.getMimeDecoder().decode(base64.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }

    @Override
    public void onDataTreeChanged(final Collection<DataTreeModification<Keystore>> changes) {
        LOG.debug("Keystore updated: {}", changes);

        for (final DataTreeModification<Keystore> change : changes) {
            final DataObjectModification<Keystore> rootNode = change.getRootNode();

            for (final DataObjectModification<? extends DataObject> changedChild : rootNode.getModifiedChildren()) {
                if (changedChild.getDataType().equals(KeyCredential.class)) {
                    final Keystore dataAfter = rootNode.getDataAfter();

                    pairs.clear();
                    if (dataAfter != null) {
                        dataAfter.getKeyCredential().forEach(pair -> pairs.put(pair.key().getKeyId(), pair));
                    }

                } else if (changedChild.getDataType().equals(PrivateKey.class)) {
                    onPrivateKeyChanged((DataObjectModification<PrivateKey>)changedChild);
                } else if (changedChild.getDataType().equals(TrustedCertificate.class)) {
                    onTrustedCertificateChanged((DataObjectModification<TrustedCertificate>)changedChild);
                }

            }
        }
    }

    private void onPrivateKeyChanged(final DataObjectModification<PrivateKey> objectModification) {

        switch (objectModification.getModificationType()) {
            case SUBTREE_MODIFIED:
            case WRITE:
                final PrivateKey privateKey = objectModification.getDataAfter();
                privateKeys.put(privateKey.getName(), privateKey);
                break;
            case DELETE:
                privateKeys.remove(objectModification.getDataBefore().getName());
                break;
            default:
                break;
        }
    }

    private void onTrustedCertificateChanged(final DataObjectModification<TrustedCertificate> objectModification) {
        switch (objectModification.getModificationType()) {
            case SUBTREE_MODIFIED:
            case WRITE:
                final TrustedCertificate trustedCertificate = objectModification.getDataAfter();
                trustedCertificates.put(trustedCertificate.getName(), trustedCertificate);
                break;
            case DELETE:
                trustedCertificates.remove(objectModification.getDataBefore().getName());
                break;
            default:
                break;
        }
    }
}
