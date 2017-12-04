/*
 * Copyright (c) 2017 ZTE Corporation. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.util;

import java.io.FileNotFoundException;
import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;

public class NetconfSslContextFactoryTest {
    @Test
    public void testKeyStoreFileNoteFound() throws Exception {
        final String ksFile = "keystore.jks";
        try {
            NetconfSslContextFactory.getContextInstance(ksFile, "123456", ksFile, "123456");
            Assert.fail(IllegalStateException.class + "exception expected");
        } catch (IllegalStateException e) {
            Assert.assertTrue(e.getCause() instanceof FileNotFoundException);
        }
    }

    @Test
    public void testKeyStorePasswordError() throws Exception {
        final String ksFile = getClass().getClassLoader().getResource("netconfclient.jks").getPath();
        try {
            NetconfSslContextFactory.getContextInstance(ksFile, "netconf1", ksFile, "netconf");
            Assert.fail(IllegalStateException.class + "exception expected");
        } catch (IllegalStateException e) {
            Assert.assertTrue(e.getCause() instanceof IOException);
        }
    }
}
