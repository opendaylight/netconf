/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.keystore.legacy.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.DataTreeChangeListener;
import org.opendaylight.mdsal.binding.api.RpcProviderService;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.singleton.api.ClusterSingletonService;
import org.opendaylight.mdsal.singleton.api.ClusterSingletonServiceProvider;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev240708.keystore.entry.KeyCredentialBuilder;
import org.opendaylight.yangtools.binding.DataObjectReference;
import org.opendaylight.yangtools.concepts.Registration;

@ExtendWith(MockitoExtension.class)
class DefaultNetconfKeystoreServiceTest {
    private static final String KEY_ID = "key-id";

    @Mock
    private DataBroker dataBroker;
    @Mock
    private RpcProviderService rpcProvider;
    @Mock
    private ClusterSingletonServiceProvider cssProvider;
    @Mock
    private AAAEncryptionService encryptionService;
    @Mock
    private Registration configListenerReg;
    @Mock
    private Registration rpcSingletonReg;

    private DefaultNetconfKeystoreService service;

    @BeforeEach
    void beforeEach() {
        doReturn(configListenerReg).when(dataBroker).registerTreeChangeListener(any(LogicalDatastoreType.class),
            any(DataObjectReference.class), any(DataTreeChangeListener.class));
        doReturn(rpcSingletonReg).when(cssProvider).registerClusterSingletonService(any(ClusterSingletonService.class));
        service = new DefaultNetconfKeystoreService(dataBroker, rpcProvider, cssProvider, encryptionService);
    }

    @AfterEach
    void afterEach() {
        service.close();
    }

    @Test
    void lookupBeforeKeystoreIsLoaded() {
        assertNull(service.lookupAsymmetric(KEY_ID));
    }

    @Test
    void lookupLoadedCredential() throws Exception {
        final var keyPair = generateKeyPair();
        // the test keys are not encrypted
        doAnswer(invocation -> invocation.getArgument(0)).when(encryptionService).decrypt(any(byte[].class));

        service.runUpdate(builder -> builder.credentials().put(KEY_ID, new KeyCredentialBuilder()
            .setKeyId(KEY_ID)
            .setAlgorithm("RSA")
            .setPrivateKey(keyPair.getPrivate().getEncoded())
            .setPublicKey(keyPair.getPublic().getEncoded())
            .build()));

        final var found = service.lookupAsymmetric(KEY_ID);
        assertNotNull(found);
        assertEquals(keyPair.getPublic(), found.getPublic());
        assertEquals(keyPair.getPrivate(), found.getPrivate());
        assertNull(service.lookupAsymmetric("other-key-id"));
    }

    @Test
    void lookupAfterInvalidConfiguration() throws Exception {
        // the test keys are not encrypted
        doAnswer(invocation -> invocation.getArgument(0)).when(encryptionService).decrypt(any(byte[].class));

        // the key bytes cannot be turned into a key pair, so the whole configuration is not applied
        service.runUpdate(builder -> builder.credentials().put(KEY_ID, new KeyCredentialBuilder()
            .setKeyId(KEY_ID)
            .setAlgorithm("RSA")
            .setPrivateKey(new byte[] { 1, 2, 3 })
            .setPublicKey(new byte[] { 4, 5, 6 })
            .build()));

        assertNull(service.lookupAsymmetric(KEY_ID));
    }

    private static KeyPair generateKeyPair() throws Exception {
        final var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }
}
