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
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.security.KeyStoreException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.DataObjectModification;
import org.opendaylight.mdsal.binding.api.DataTreeIdentifier;
import org.opendaylight.mdsal.binding.api.DataTreeModification;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.Keystore;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017._private.keys.PrivateKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017._private.keys.PrivateKeyBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017._private.keys.PrivateKeyKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.trusted.certificates.TrustedCertificate;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.trusted.certificates.TrustedCertificateBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.trusted.certificates.TrustedCertificateKey;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class DefaultSslHandlerFactoryProviderTest {
    private static final String XML_ELEMENT_PRIVATE_KEY = "private-key";
    private static final String XML_ELEMENT_NAME = "name";
    private static final String XML_ELEMENT_DATA = "data";
    private static final String XML_ELEMENT_CERT_CHAIN = "certificate-chain";
    private static final String XML_ELEMENT_TRUSTED_CERT = "trusted-certificate";
    private static final String XML_ELEMENT_CERT = "certificate";

    @Mock
    private DataBroker dataBroker;
    @Mock
    private ListenerRegistration<?> listenerRegistration;

    @Before
    public void setUp() {
        doReturn(listenerRegistration).when(dataBroker)
            .registerDataTreeChangeListener(any(DataTreeIdentifier.class), any(DefaultSslHandlerFactoryProvider.class));
    }

    @Test
    public void testKeystoreAdapterInit() throws Exception {
        final DefaultSslHandlerFactoryProvider keystoreAdapter = new DefaultSslHandlerFactoryProvider(dataBroker);
        final var ex = assertThrows(KeyStoreException.class, keystoreAdapter::getJavaKeyStore);
        assertThat(ex.getMessage(), startsWith("No keystore private key found"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testWritePrivateKey() throws Exception {
        DataTreeModification<Keystore> dataTreeModification = mock(DataTreeModification.class);
        DataObjectModification<Keystore> keystoreObjectModification = mock(DataObjectModification.class);
        doReturn(keystoreObjectModification).when(dataTreeModification).getRootNode();

        DataObjectModification<?> childObjectModification = mock(DataObjectModification.class);
        doReturn(List.of(childObjectModification)).when(keystoreObjectModification).getModifiedChildren();
        doReturn(PrivateKey.class).when(childObjectModification).getDataType();

        doReturn(DataObjectModification.ModificationType.WRITE).when(childObjectModification).getModificationType();

        final var privateKey = getPrivateKey();
        doReturn(privateKey).when(childObjectModification).getDataAfter();

        final var keystoreAdapter = new DefaultSslHandlerFactoryProvider(dataBroker);
        keystoreAdapter.onDataTreeChanged(List.of(dataTreeModification));

        final var keyStore = keystoreAdapter.getJavaKeyStore();
        assertTrue(keyStore.containsAlias(privateKey.getName()));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testWritePrivateKeyAndTrustedCertificate() throws Exception {
        // Prepare PrivateKey configuration
        DataTreeModification<Keystore> dataTreeModification1 = mock(DataTreeModification.class);
        DataObjectModification<Keystore> keystoreObjectModification1 = mock(DataObjectModification.class);
        doReturn(keystoreObjectModification1).when(dataTreeModification1).getRootNode();

        DataObjectModification<?> childObjectModification1 = mock(DataObjectModification.class);
        doReturn(List.of(childObjectModification1)).when(keystoreObjectModification1).getModifiedChildren();
        doReturn(PrivateKey.class).when(childObjectModification1).getDataType();

        doReturn(DataObjectModification.ModificationType.WRITE).when(childObjectModification1).getModificationType();

        final var privateKey = getPrivateKey();
        doReturn(privateKey).when(childObjectModification1).getDataAfter();

        // Prepare TrustedCertificate configuration
        DataTreeModification<Keystore> dataTreeModification2 = mock(DataTreeModification.class);
        DataObjectModification<Keystore> keystoreObjectModification2 = mock(DataObjectModification.class);
        doReturn(keystoreObjectModification2).when(dataTreeModification2).getRootNode();

        DataObjectModification<?> childObjectModification2 = mock(DataObjectModification.class);
        doReturn(List.of(childObjectModification2)).when(keystoreObjectModification2).getModifiedChildren();
        doReturn(TrustedCertificate.class).when(childObjectModification2).getDataType();

        doReturn(DataObjectModification.ModificationType.WRITE)
            .when(childObjectModification2).getModificationType();

        final var trustedCertificate = geTrustedCertificate();
        doReturn(trustedCertificate).when(childObjectModification2).getDataAfter();

        // Apply configurations
        final var keystoreAdapter = new DefaultSslHandlerFactoryProvider(dataBroker);
        keystoreAdapter.onDataTreeChanged(List.of(dataTreeModification1, dataTreeModification2));

        // Check result
        final var keyStore = keystoreAdapter.getJavaKeyStore();
        assertTrue(keyStore.containsAlias(privateKey.getName()));
        assertTrue(keyStore.containsAlias(trustedCertificate.getName()));
    }

    private PrivateKey getPrivateKey() throws Exception {
        final List<PrivateKey> privateKeys = new ArrayList<>();
        final Document document = readKeystoreXML();
        final NodeList nodeList = document.getElementsByTagName(XML_ELEMENT_PRIVATE_KEY);
        for (int i = 0; i < nodeList.getLength(); i++) {
            final Node node = nodeList.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            final Element element = (Element)node;
            final String keyName = element.getElementsByTagName(XML_ELEMENT_NAME).item(0).getTextContent();
            final String keyData = element.getElementsByTagName(XML_ELEMENT_DATA).item(0).getTextContent();
            final NodeList certNodes = element.getElementsByTagName(XML_ELEMENT_CERT_CHAIN);
            final List<String> certChain = new ArrayList<>();
            for (int j = 0; j < certNodes.getLength(); j++) {
                final Node certNode = certNodes.item(j);
                if (certNode.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                certChain.add(certNode.getTextContent());
            }

            final PrivateKey privateKey = new PrivateKeyBuilder()
                    .withKey(new PrivateKeyKey(keyName))
                    .setName(keyName)
                    .setData(keyData)
                    .setCertificateChain(certChain)
                    .build();
            privateKeys.add(privateKey);
        }

        return privateKeys.get(0);
    }

    private TrustedCertificate geTrustedCertificate() throws Exception {
        final List<TrustedCertificate> trustedCertificates = new ArrayList<>();
        final Document document = readKeystoreXML();
        final NodeList nodeList = document.getElementsByTagName(XML_ELEMENT_TRUSTED_CERT);
        for (int i = 0; i < nodeList.getLength(); i++) {
            final Node node = nodeList.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            final Element element = (Element)node;
            final String certName = element.getElementsByTagName(XML_ELEMENT_NAME).item(0).getTextContent();
            final String certData = element.getElementsByTagName(XML_ELEMENT_CERT).item(0).getTextContent();

            final TrustedCertificate certificate = new TrustedCertificateBuilder()
                    .withKey(new TrustedCertificateKey(certName))
                    .setName(certName)
                    .setCertificate(certData)
                    .build();
            trustedCertificates.add(certificate);
        }

        return trustedCertificates.get(0);
    }

    private Document readKeystoreXML() throws Exception {
        return XmlUtil.readXmlToDocument(getClass().getResourceAsStream("/netconf-keystore.xml"));
    }
}
