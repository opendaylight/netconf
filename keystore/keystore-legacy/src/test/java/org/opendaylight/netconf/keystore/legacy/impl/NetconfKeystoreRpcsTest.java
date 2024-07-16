/*
 * Copyright (c) 2018 ZTE Corporation. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.keystore.legacy.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.opendaylight.mdsal.common.api.CommitInfo.emptyFluentFuture;

import java.util.ArrayList;
import java.util.HashMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.RpcProviderService;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev240708.AddPrivateKeyInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev240708.AddPrivateKeyInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev240708.AddTrustedCertificateInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev240708.AddTrustedCertificateInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev240708.rpc._private.keys.PrivateKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev240708.rpc._private.keys.PrivateKeyBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev240708.rpc._private.keys.PrivateKeyKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev240708.rpc.trusted.certificates.TrustedCertificate;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev240708.rpc.trusted.certificates.TrustedCertificateBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev240708.rpc.trusted.certificates.TrustedCertificateKey;
import org.opendaylight.yangtools.binding.DataObject;
import org.opendaylight.yangtools.concepts.ObjectRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

@ExtendWith(MockitoExtension.class)
class NetconfKeystoreRpcsTest {
    private static final String XML_ELEMENT_PRIVATE_KEY = "private-key";
    private static final String XML_ELEMENT_NAME = "name";
    private static final String XML_ELEMENT_DATA = "data";
    private static final String XML_ELEMENT_CERT_CHAIN = "certificate-chain";
    private static final String XML_ELEMENT_TRUSTED_CERT = "trusted-certificate";
    private static final String XML_ELEMENT_CERT = "certificate";

    private static Document KEYSTORE;

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

    @BeforeAll
    static void beforeAll() throws Exception {
        KEYSTORE = XmlUtil.readXmlToDocument(
            NetconfKeystoreRpcsTest.class.getResourceAsStream("/netconf-keystore.xml"));
    }

    @BeforeEach
    void beforeEach() {
        doReturn(writeTx).when(dataBroker).newWriteOnlyTransaction();
    }

    @Test
    void testAddPrivateKey() throws Exception {
        doReturn(emptyFluentFuture()).when(writeTx).commit();

        final var rpc = new DefaultAddPrivateKey(dataBroker);
        final var input = getPrivateKeyInput();
        rpc.invoke(input).get();

        verify(writeTx, times(input.nonnullPrivateKey().size()))
            .put(any(LogicalDatastoreType.class), any(InstanceIdentifier.class), any(DataObject.class));
    }

    private static AddPrivateKeyInput getPrivateKeyInput() throws Exception {
        final var privateKeys = new HashMap<PrivateKeyKey, PrivateKey>();
        final var nodeList = KEYSTORE.getElementsByTagName(XML_ELEMENT_PRIVATE_KEY);
        for (int i = 0; i < nodeList.getLength(); i++) {
            if (nodeList.item(i) instanceof Element element) {
                final var keyName = element.getElementsByTagName(XML_ELEMENT_NAME).item(0).getTextContent();
                final var keyData = element.getElementsByTagName(XML_ELEMENT_DATA).item(0).getTextContent();
                final var certNodes = element.getElementsByTagName(XML_ELEMENT_CERT_CHAIN);
                final var certChain = new ArrayList<String>();
                for (int j = 0; j < certNodes.getLength(); j++) {
                    if (certNodes.item(j) instanceof Element certElement) {
                        certChain.add(certElement.getTextContent());
                    }
                }

                final var key = new PrivateKeyKey(keyName);
                privateKeys.put(key, new PrivateKeyBuilder()
                    .withKey(key)
                    .setData(keyData)
                    .setCertificateChain(certChain)
                    .build());
            }
        }

        return new AddPrivateKeyInputBuilder().setPrivateKey(privateKeys).build();
    }

    @Test
    void testAddTrustedCertificate() throws Exception {
        doReturn(emptyFluentFuture()).when(writeTx).commit();

        final var rpc = new DefaultAddTrustedCertificate(dataBroker);
        final var input = getTrustedCertificateInput();
        rpc.invoke(input).get();

        verify(writeTx, times(input.nonnullTrustedCertificate().size()))
            .put(any(LogicalDatastoreType.class), any(InstanceIdentifier.class), any(DataObject.class));
    }

    private static AddTrustedCertificateInput getTrustedCertificateInput() throws Exception {
        final var trustedCertificates = new HashMap<TrustedCertificateKey, TrustedCertificate>();
        final var nodeList = KEYSTORE.getElementsByTagName(XML_ELEMENT_TRUSTED_CERT);
        for (int i = 0; i < nodeList.getLength(); i++) {
            if (nodeList.item(i) instanceof Element element) {
                final var certName = element.getElementsByTagName(XML_ELEMENT_NAME).item(0).getTextContent();
                final var certData = element.getElementsByTagName(XML_ELEMENT_CERT).item(0).getTextContent();

                final var key = new TrustedCertificateKey(certName);
                trustedCertificates.put(key, new TrustedCertificateBuilder()
                    .withKey(key)
                    .setName(certName)
                    .setCertificate(certData)
                    .build());
            }
        }

        return new AddTrustedCertificateInputBuilder().setTrustedCertificate(trustedCertificates).build();
    }
}
