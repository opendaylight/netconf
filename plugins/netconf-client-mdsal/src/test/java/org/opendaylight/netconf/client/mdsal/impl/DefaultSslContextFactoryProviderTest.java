/*
 * Copyright (c) 2018 ZTE Corporation. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.impl;

import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;

import java.security.KeyStoreException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.DataObjectModified;
import org.opendaylight.mdsal.binding.api.DataObjectWritten;
import org.opendaylight.mdsal.binding.api.DataTreeChangeListener;
import org.opendaylight.mdsal.binding.api.DataTreeModification;
import org.opendaylight.mdsal.binding.api.RpcProviderService;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.singleton.api.ClusterSingletonServiceProvider;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.keystore.legacy.impl.DefaultNetconfKeystoreService;
import org.opendaylight.netconf.keystore.legacy.impl.SecurityHelper;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev240708.Keystore;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev240708._private.keys.PrivateKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev240708._private.keys.PrivateKeyBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev240708._private.keys.PrivateKeyKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev240708.trusted.certificates.TrustedCertificate;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev240708.trusted.certificates.TrustedCertificateBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev240708.trusted.certificates.TrustedCertificateKey;
import org.opendaylight.yangtools.concepts.Registration;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

@ExtendWith(MockitoExtension.class)
class DefaultSslContextFactoryProviderTest {
    private static final String XML_ELEMENT_PRIVATE_KEY = "private-key";
    private static final String XML_ELEMENT_NAME = "name";
    private static final String XML_ELEMENT_DATA = "data";
    private static final String XML_ELEMENT_CERT_CHAIN = "certificate-chain";
    private static final String XML_ELEMENT_TRUSTED_CERT = "trusted-certificate";
    private static final String XML_ELEMENT_CERT = "certificate";

    @Mock
    private DataBroker dataBroker;
    @Mock
    private RpcProviderService rpcProvider;
    @Mock
    private ClusterSingletonServiceProvider cssProvider;
    @Mock
    private AAAEncryptionService encryptionService;
    @Mock
    private Registration registration;
    @Mock
    private DataTreeModification<Keystore> dataTreeModification1;
    @Mock
    private DataTreeModification<Keystore> dataTreeModification2;
    @Mock
    private DataObjectModified<Keystore> keystoreObjectModification1;
    @Mock
    private DataObjectModified<Keystore> keystoreObjectModification2;
    @Mock
    private DataObjectWritten<PrivateKey> privateKeyModification;
    @Mock
    private DataObjectWritten<TrustedCertificate> trustedCertificateModification;

    private DataTreeChangeListener<Keystore> listener;
    private DefaultNetconfKeystoreService keystore;

    @BeforeEach
    void beforeEach() {
        doAnswer(inv -> {
            listener = inv.getArgument(2);
            return registration;
        }).when(dataBroker).registerTreeChangeListener(eq(LogicalDatastoreType.CONFIGURATION), any(), any());
        doReturn(registration).when(cssProvider).registerClusterSingletonService(any());
        keystore = new DefaultNetconfKeystoreService(dataBroker, rpcProvider, cssProvider, encryptionService);
    }

    @AfterEach
    void afterEach() {
        keystore.close();
    }

    private DefaultSslContextFactoryProvider newProvider() {
        return new DefaultSslContextFactoryProvider(keystore);
    }

    @Test
    void testKeystoreAdapterInit() {
        try (var keystoreAdapter = newProvider()) {
            final var ex = assertThrows(KeyStoreException.class, () -> keystoreAdapter.getJavaKeyStore(Set.of()));
            assertThat(ex.getMessage(), startsWith("No keystore private key found"));
        }
    }

    @Test
    void testWritePrivateKey() throws Exception {
        doReturn(keystoreObjectModification1).when(dataTreeModification1).getRootNode();
        doReturn(List.of(privateKeyModification)).when(keystoreObjectModification1)
            .getModifiedChildren(PrivateKey.class);

        final var privateKey = getPrivateKey();
        doReturn(privateKey).when(privateKeyModification).dataAfter();
        doReturn(privateKey.requireData()).when(encryptionService).decrypt(privateKey.requireData());
        doReturn(privateKey.requireCertificateChain().get(0)).when(encryptionService)
            .decrypt(privateKey.requireCertificateChain().get(0));

        try (var keystoreAdapter = newProvider()) {
            listener.onDataTreeChanged(List.of(dataTreeModification1));

            final var keyStore = keystoreAdapter.getJavaKeyStore(Set.of());
            assertTrue(keyStore.containsAlias(privateKey.getName()));
        }
    }

    @Test
    void testWritePrivateKeyAndTrustedCertificate() throws Exception {
        // Prepare PrivateKey configuration
        doReturn(keystoreObjectModification1).when(dataTreeModification1).getRootNode();

        doReturn(List.of(privateKeyModification)).when(keystoreObjectModification1)
            .getModifiedChildren(PrivateKey.class);

        final var privateKey = getPrivateKey();
        doReturn(privateKey).when(privateKeyModification).dataAfter();

        // Prepare TrustedCertificate configuration
        doReturn(keystoreObjectModification2).when(dataTreeModification2).getRootNode();

        doReturn(List.of()).when(keystoreObjectModification2).getModifiedChildren(PrivateKey.class);
        doReturn(List.of(trustedCertificateModification)).when(keystoreObjectModification2)
            .getModifiedChildren(TrustedCertificate.class);

        final var trustedCertificate = getTrustedCertificate();
        doReturn(trustedCertificate).when(trustedCertificateModification).dataAfter();

        doReturn(privateKey.requireData()).when(encryptionService).decrypt(privateKey.requireData());
        doReturn(privateKey.requireCertificateChain().get(0)).when(encryptionService)
            .decrypt(privateKey.requireCertificateChain().get(0));
        doReturn(trustedCertificate.requireCertificate()).when(encryptionService)
            .decrypt(trustedCertificate.requireCertificate());

        try (var keystoreAdapter = newProvider()) {
            // Apply configurations
            listener.onDataTreeChanged(List.of(dataTreeModification1, dataTreeModification2));

            // Check result
            final var keyStore = keystoreAdapter.getJavaKeyStore(Set.of());
            assertTrue(keyStore.containsAlias(privateKey.getName()));
            assertTrue(keyStore.containsAlias(trustedCertificate.getName()));
        }
    }

    private static PrivateKey getPrivateKey() throws Exception {
        final var privateKeys = new ArrayList<PrivateKey>();
        final var document = readKeystoreXML();
        final var nodeList = document.getElementsByTagName(XML_ELEMENT_PRIVATE_KEY);
        for (int i = 0; i < nodeList.getLength(); i++) {
            if (nodeList.item(i) instanceof Element element) {
                final var keyName = element.getElementsByTagName(XML_ELEMENT_NAME).item(0).getTextContent();
                final var keyData = element.getElementsByTagName(XML_ELEMENT_DATA).item(0).getTextContent();
                final var certNodes = element.getElementsByTagName(XML_ELEMENT_CERT_CHAIN);
                final var certChain = new ArrayList<String>();
                for (int j = 0; j < certNodes.getLength(); j++) {
                    if (certNodes.item(j) instanceof Element certNode) {
                        certChain.add(certNode.getTextContent());
                    }
                }

                final var privateKey = new SecurityHelper().decodePrivateKey(keyData, null);

                final var decodedCerts = new ArrayList<byte[]>();
                for (final var cert : certChain) {
                    decodedCerts.add(SecurityHelper.decodeCertificate(cert).getEncoded());
                }


                privateKeys.add(new PrivateKeyBuilder()
                    .withKey(new PrivateKeyKey(keyName))
                    .setName(keyName)
                    .setData(privateKey.getPrivate().getEncoded())
                    .setAlgorithm(privateKey.getPrivate().getAlgorithm())
                    .setCertificateChain(decodedCerts)
                    .build());
            }
        }

        return privateKeys.get(0);
    }

    private static TrustedCertificate getTrustedCertificate() throws Exception {
        final var trustedCertificates = new ArrayList<TrustedCertificate>();
        final var document = readKeystoreXML();
        final var nodeList = document.getElementsByTagName(XML_ELEMENT_TRUSTED_CERT);
        for (int i = 0; i < nodeList.getLength(); i++) {
            if (nodeList.item(i) instanceof Element element) {
                final var certName = element.getElementsByTagName(XML_ELEMENT_NAME).item(0).getTextContent();
                final var certData = element.getElementsByTagName(XML_ELEMENT_CERT).item(0).getTextContent();

                final var cert = SecurityHelper.decodeCertificate(certData).getEncoded();

                trustedCertificates.add(new TrustedCertificateBuilder()
                    .withKey(new TrustedCertificateKey(certName))
                    .setName(certName)
                    .setCertificate(cert)
                    .build());
            }
        }

        return trustedCertificates.get(0);
    }

    private static Document readKeystoreXML() throws Exception {
        return XmlUtil.readXmlToDocument(
            DefaultSslContextFactoryProviderTest.class.getResourceAsStream("/netconf-keystore.xml"));
    }
}
