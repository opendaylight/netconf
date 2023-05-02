/*
 * Copyright (c) 2018 ZTE Corporation. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.opendaylight.mdsal.common.api.CommitInfo.emptyFluentFuture;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.RpcProviderService;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.AddPrivateKeyInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.AddPrivateKeyInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.AddTrustedCertificateInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.AddTrustedCertificateInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.NetconfKeystoreService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017._private.keys.PrivateKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017._private.keys.PrivateKeyBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017._private.keys.PrivateKeyKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.trusted.certificates.TrustedCertificate;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.trusted.certificates.TrustedCertificateBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.trusted.certificates.TrustedCertificateKey;
import org.opendaylight.yangtools.concepts.ObjectRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class NetconfSalKeystoreServiceTest {
    private static final String XML_ELEMENT_PRIVATE_KEY = "private-key";
    private static final String XML_ELEMENT_NAME = "name";
    private static final String XML_ELEMENT_DATA = "data";
    private static final String XML_ELEMENT_CERT_CHAIN = "certificate-chain";
    private static final String XML_ELEMENT_TRUSTED_CERT = "trusted-certificate";
    private static final String XML_ELEMENT_CERT = "certificate";

    @Mock
    private WriteTransaction writeTx;
    @Mock
    private DataBroker dataBroker;
    @Mock
    private AAAEncryptionService encryptionService;
    @Mock
    private RpcProviderService rpcProvider;
    @Mock
    private ObjectRegistration<?> rpcReg;

    @Before
    public void setUp() {
        doReturn(writeTx).when(dataBroker).newWriteOnlyTransaction();
        doNothing().when(writeTx)
            .merge(any(LogicalDatastoreType.class), any(InstanceIdentifier.class), any(DataObject.class));
        doReturn(rpcReg).when(rpcProvider).registerRpcImplementation(eq(NetconfKeystoreService.class), any());
        doNothing().when(rpcReg).close();
    }

    @Test
    public void testAddPrivateKey() throws Exception {
        doReturn(emptyFluentFuture()).when(writeTx).commit();
        try (var keystoreService = new NetconfSalKeystoreService(dataBroker, encryptionService, rpcProvider)) {
            final AddPrivateKeyInput input = getPrivateKeyInput();
            keystoreService.addPrivateKey(input);

            verify(writeTx, times(input.nonnullPrivateKey().size()))
                .merge(any(LogicalDatastoreType.class), any(InstanceIdentifier.class), any(DataObject.class));
        }
    }

    @Test
    public void testAddTrustedCertificate() throws Exception {
        doReturn(emptyFluentFuture()).when(writeTx).commit();
        try (var keystoreService = new NetconfSalKeystoreService(dataBroker, encryptionService, rpcProvider)) {
            final AddTrustedCertificateInput input = getTrustedCertificateInput();
            keystoreService.addTrustedCertificate(input);

            verify(writeTx, times(input.nonnullTrustedCertificate().size()))
                .merge(any(LogicalDatastoreType.class), any(InstanceIdentifier.class), any(DataObject.class));
        }
    }

    private AddPrivateKeyInput getPrivateKeyInput() throws Exception {
        final Map<PrivateKeyKey, PrivateKey> privateKeys = new HashMap<>();
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

            final PrivateKeyKey key = new PrivateKeyKey(keyName);
            privateKeys.put(key, new PrivateKeyBuilder()
                .withKey(key)
                .setData(keyData)
                .setCertificateChain(certChain)
                .build());
        }

        return new AddPrivateKeyInputBuilder().setPrivateKey(privateKeys).build();
    }

    private AddTrustedCertificateInput getTrustedCertificateInput() throws Exception {
        final Map<TrustedCertificateKey, TrustedCertificate> trustedCertificates = new HashMap<>();
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

            final TrustedCertificateKey key = new TrustedCertificateKey(certName);
            trustedCertificates.put(key, new TrustedCertificateBuilder()
                .withKey(key)
                .setName(certName)
                .setCertificate(certData)
                .build());
        }

        return new AddTrustedCertificateInputBuilder().setTrustedCertificate(trustedCertificates).build();
    }

    private Document readKeystoreXML() throws Exception {
        return XmlUtil.readXmlToDocument(getClass().getResourceAsStream("/netconf-keystore.xml"));
    }
}
