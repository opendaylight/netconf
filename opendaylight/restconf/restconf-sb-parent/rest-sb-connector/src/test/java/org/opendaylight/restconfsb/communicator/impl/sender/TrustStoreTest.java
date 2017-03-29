/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.communicator.impl.sender;

import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import org.junit.Test;

public class TrustStoreTest {

    @Test
    public void createKeyStoreFromClassPathTest() throws CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException {
        final TrustStore trustStore = new TrustStore("/cacerts", "changeit", "JKS", "CLASSPATH");
        trustStore.createKeyStore();
    }

    @Test
    public void createKeyStoreFromPathTest() throws CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException {
        final TrustStore trustStore = new TrustStore(getClass().getResource("/store/cacerts").getPath(), "changeit", "JKS", "PATH");
        trustStore.createKeyStore();
    }
}
