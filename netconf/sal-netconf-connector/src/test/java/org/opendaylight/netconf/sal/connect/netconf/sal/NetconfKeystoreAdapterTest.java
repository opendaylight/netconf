/*
 * Copyright (c) 2018 ZTE Corporation. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.sal;

import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.security.KeyStoreException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.config.util.xml.XmlUtil;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
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

public class NetconfKeystoreAdapterTest {
    private static final String XML_ELEMENT_PRIVATE_KEY = "private-key";
    private static final String XML_ELEMENT_NAME = "name";
    private static final String XML_ELEMENT_DATA = "data";
    private static final String XML_ELEMENT_CERT_CHAIN = "certificate-chain";
    private static final String XML_ELEMENT_TRUSTED_CERT = "trusted-certificate";
    private static final String XML_ELEMENT_CERT = "certificate";

    @Mock
    private DataBroker dataBroker;
    @Mock
    private ListenerRegistration listenerRegistration;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        doReturn(listenerRegistration).when(dataBroker).registerDataTreeChangeListener(
                any(DataTreeIdentifier.class), any(NetconfKeystoreAdapter.class));
    }

    @Test
    public void testKeystoreAdapterInit() throws Exception {
        NetconfKeystoreAdapter keystoreAdapter = new NetconfKeystoreAdapter(dataBroker);

        try {
            keystoreAdapter.getJavaKeyStore();
            Assert.fail(IllegalStateException.class + "exception expected");
        } catch (KeyStoreException e) {
            assertTrue(e.getMessage().startsWith("No keystore private key found"));
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testWritePrivateKey() throws Exception {
        DataTreeModification<Keystore> dataTreeModification = mock(DataTreeModification.class);
        DataObjectModification<Keystore> keystoreObjectModification = mock(DataObjectModification.class);
        doReturn(keystoreObjectModification).when(dataTreeModification).getRootNode();

        DataObjectModification<?> childObjectModification = mock(DataObjectModification.class);
        doReturn(Collections.singletonList(childObjectModification))
            .when(keystoreObjectModification).getModifiedChildren();
        doReturn(PrivateKey.class).when(childObjectModification).getDataType();

        doReturn(DataObjectModification.ModificationType.WRITE)
            .when(childObjectModification).getModificationType();

        PrivateKey privateKey = getPrivateKey();
        doReturn(privateKey).when(childObjectModification).getDataAfter();

        NetconfKeystoreAdapter keystoreAdapter = new NetconfKeystoreAdapter(dataBroker);
        keystoreAdapter.onDataTreeChanged(Collections.singletonList(dataTreeModification));

        java.security.KeyStore keyStore = keystoreAdapter.getJavaKeyStore();
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
        doReturn(Collections.singletonList(childObjectModification1))
            .when(keystoreObjectModification1).getModifiedChildren();
        doReturn(PrivateKey.class).when(childObjectModification1).getDataType();

        doReturn(DataObjectModification.ModificationType.WRITE)
            .when(childObjectModification1).getModificationType();

        PrivateKey privateKey = getPrivateKey();
        doReturn(privateKey).when(childObjectModification1).getDataAfter();

        // Prepare TrustedCertificate configuration
        DataTreeModification<Keystore> dataTreeModification2 = mock(DataTreeModification.class);
        DataObjectModification<Keystore> keystoreObjectModification2 = mock(DataObjectModification.class);
        doReturn(keystoreObjectModification2).when(dataTreeModification2).getRootNode();

        DataObjectModification<?> childObjectModification2 = mock(DataObjectModification.class);
        doReturn(Collections.singletonList(childObjectModification2))
            .when(keystoreObjectModification2).getModifiedChildren();
        doReturn(TrustedCertificate.class).when(childObjectModification2).getDataType();

        doReturn(DataObjectModification.ModificationType.WRITE)
            .when(childObjectModification2).getModificationType();

        TrustedCertificate trustedCertificate = geTrustedCertificate();
        doReturn(trustedCertificate).when(childObjectModification2).getDataAfter();

        // Apply configurations
        NetconfKeystoreAdapter keystoreAdapter = new NetconfKeystoreAdapter(dataBroker);
        keystoreAdapter.onDataTreeChanged(Arrays.asList(dataTreeModification1, dataTreeModification2));

        // Check result
        java.security.KeyStore keyStore = keystoreAdapter.getJavaKeyStore();
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
                    .setKey(new PrivateKeyKey(keyName))
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
                    .setKey(new TrustedCertificateKey(certName))
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
