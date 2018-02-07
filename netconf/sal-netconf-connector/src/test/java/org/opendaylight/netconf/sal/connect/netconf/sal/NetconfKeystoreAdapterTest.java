/*
 * Copyright (c) 2018 ZTE Corporation. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.sal;

import static org.junit.Assert.assertTrue;

import java.security.KeyStoreException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;

public class NetconfKeystoreAdapterTest {
    @Mock
    private DataBroker dataBroker;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
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
}
