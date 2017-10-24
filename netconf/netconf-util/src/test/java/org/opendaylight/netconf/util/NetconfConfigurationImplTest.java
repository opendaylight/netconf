/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.util;

import java.net.InetSocketAddress;
import java.util.Dictionary;
import java.util.Hashtable;
import org.junit.Assert;
import org.junit.Test;

public class NetconfConfigurationImplTest {

    @Test
    public void testUpdated() throws Exception {
        final NetconfConfigurationImpl config = new NetconfConfigurationImpl("127.0.0.1", "8383",
                "0.0.0.0", "1830",
                "./configuration/RSA.pk");
        Assert.assertEquals(new InetSocketAddress("0.0.0.0", 1830), config.getSshServerAddress());
        Assert.assertEquals(new InetSocketAddress("127.0.0.1", 8383), config.getTcpServerAddress());
        Assert.assertEquals("./configuration/RSA.pk", config.getPrivateKeyPath());
        final Dictionary<String, String> newValues = new Hashtable<>();
        final String newSshIp = "192.168.1.1";
        final String newTcpIp = "192.168.1.2";
        final int newSshPort = 1234;
        final int newTcpPort = 4567;
        final String newSshKeyPath = "./new_folder/configuration/RSA.pk";
        newValues.put("ssh-address", newSshIp);
        newValues.put("ssh-port", Integer.toString(newSshPort));
        newValues.put("tcp-address", newTcpIp);
        newValues.put("tcp-port", Integer.toString(newTcpPort));
        newValues.put("ssh-pk-path", newSshKeyPath);
        config.updated(newValues);
        Assert.assertEquals(new InetSocketAddress(newSshIp, newSshPort), config.getSshServerAddress());
        Assert.assertEquals(new InetSocketAddress(newTcpIp, newTcpPort), config.getTcpServerAddress());
        Assert.assertEquals(newSshKeyPath, config.getPrivateKeyPath());
    }

    @Test
    public void testUpdatedNull() throws Exception {
        final NetconfConfigurationImpl config = new NetconfConfigurationImpl("127.0.0.1", "8383",
                "0.0.0.0", "1830",
                "./configuration/RSA.pk");
        Assert.assertEquals(new InetSocketAddress("0.0.0.0", 1830), config.getSshServerAddress());
        Assert.assertEquals(new InetSocketAddress("127.0.0.1", 8383), config.getTcpServerAddress());
        Assert.assertEquals("./configuration/RSA.pk", config.getPrivateKeyPath());
        final Dictionary<String, String> nullDictionary = null;
        config.updated(nullDictionary);
        Assert.assertEquals(new InetSocketAddress("0.0.0.0", 1830), config.getSshServerAddress());
        Assert.assertEquals(new InetSocketAddress("127.0.0.1", 8383), config.getTcpServerAddress());
        Assert.assertEquals("./configuration/RSA.pk", config.getPrivateKeyPath());
    }
}