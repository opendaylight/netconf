/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.util.osgi;

import java.net.InetSocketAddress;
import java.util.Dictionary;
import java.util.Hashtable;
import org.junit.Assert;
import org.junit.Test;

public class NetconfConfigurationTest {

    @Test
    public void testUpdated() throws Exception {
        final NetconfConfiguration config = new NetconfConfiguration();
        Assert.assertEquals(new InetSocketAddress("0.0.0.0", 1830), config.getSshServerAddress());
        Assert.assertEquals(new InetSocketAddress("127.0.0.1", 8383), config.getTcpServerAddress());
        Assert.assertEquals("./configuration/RSA.pk", config.getPrivateKeyPath());
        Assert.assertEquals("./configuration/netconfclient.jks", config.getKeyStoreFile());
        Assert.assertEquals("netconf", config.getKeyStorePassword());
        Assert.assertEquals("./configuration/netconfclient.jks", config.getTrustStoreFile());
        Assert.assertEquals("netconf", config.getTrustStorePassword());
        final Dictionary<String, String> newValues = new Hashtable<>();
        final String newSshIp = "192.168.1.1";
        final String newTcpIp = "192.168.1.2";
        final int newSshPort = 1234;
        final int newTcpPort = 4567;
        final String newSshKeyPath = "./new_folder/configuration/RSA.pk";
        final String newKeyStoreFile = "./new_folder/configuration/netconfclient.jks";
        final String newKeyStorePasswd = "netconf1";
        final String newTrustStoreFile = "./new_folder/configuration/netconfclient.jks";
        final String newTrustStorePasswd = "netconf1";
        newValues.put("ssh-address", newSshIp);
        newValues.put("ssh-port", Integer.toString(newSshPort));
        newValues.put("tcp-address", newTcpIp);
        newValues.put("tcp-port", Integer.toString(newTcpPort));
        newValues.put("ssh-pk-path", newSshKeyPath);
        newValues.put("key-store-file", newKeyStoreFile);
        newValues.put("key-store-password", newKeyStorePasswd);
        newValues.put("trust-store-file", newTrustStoreFile);
        newValues.put("trust-store-password", newTrustStorePasswd);
        config.updated(newValues);
        Assert.assertEquals(new InetSocketAddress(newSshIp, newSshPort), config.getSshServerAddress());
        Assert.assertEquals(new InetSocketAddress(newTcpIp, newTcpPort), config.getTcpServerAddress());
        Assert.assertEquals(newSshKeyPath, config.getPrivateKeyPath());
        Assert.assertEquals(newKeyStoreFile, config.getKeyStoreFile());
        Assert.assertEquals(newKeyStorePasswd, config.getKeyStorePassword());
        Assert.assertEquals(newTrustStoreFile, config.getTrustStoreFile());
        Assert.assertEquals(newTrustStorePasswd, config.getTrustStorePassword());
    }

    @Test
    public void testUpdatedNull() throws Exception {
        final NetconfConfiguration config = new NetconfConfiguration();
        Assert.assertEquals(new InetSocketAddress("0.0.0.0", 1830), config.getSshServerAddress());
        Assert.assertEquals(new InetSocketAddress("127.0.0.1", 8383), config.getTcpServerAddress());
        Assert.assertEquals("./configuration/RSA.pk", config.getPrivateKeyPath());
        Assert.assertEquals("./configuration/netconfclient.jks", config.getKeyStoreFile());
        Assert.assertEquals("netconf", config.getKeyStorePassword());
        Assert.assertEquals("./configuration/netconfclient.jks", config.getTrustStoreFile());
        Assert.assertEquals("netconf", config.getTrustStorePassword());
        final Dictionary<String, String> nullDictionary = null;
        config.updated(nullDictionary);
        Assert.assertEquals(new InetSocketAddress("0.0.0.0", 1830), config.getSshServerAddress());
        Assert.assertEquals(new InetSocketAddress("127.0.0.1", 8383), config.getTcpServerAddress());
        Assert.assertEquals("./configuration/RSA.pk", config.getPrivateKeyPath());
        Assert.assertEquals("./configuration/netconfclient.jks", config.getKeyStoreFile());
        Assert.assertEquals("netconf", config.getKeyStorePassword());
        Assert.assertEquals("./configuration/netconfclient.jks", config.getTrustStoreFile());
        Assert.assertEquals("netconf", config.getTrustStorePassword());
    }
}