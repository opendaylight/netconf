/*
 * Copyright (c) 2017 Inocybe Technologies, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.streams.websockets;

import io.netty.channel.EventLoopGroup;
import java.lang.reflect.Field;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class WebSocketServerTest {
    private WebSocketServer instance;
    private final int port = 8888;

    @Before
    public void init() {
        WebSocketServer.createInstance(port);
        instance = WebSocketServer.getInstance();
        Assert.assertNotNull(instance);
    }

    @Test
    public void getPortTest() {
        Assert.assertEquals(instance.getPort(), port);
    }

    @Test
    public void runTest() throws NoSuchFieldException, IllegalAccessException {
        Thread mainRunner = new Thread(() -> {
            instance.run();
        });
        mainRunner.start();

        final Field bossGroup = instance.getClass().getDeclaredField("bossGroup");
        final Field workerGroup = instance.getClass().getDeclaredField("workerGroup");
        bossGroup.setAccessible(true);
        workerGroup.setAccessible(true);
        Assert.assertNotNull((EventLoopGroup) bossGroup.get(instance));
        Assert.assertNotNull((EventLoopGroup) workerGroup.get(instance));
    }

    @After
    public void destory() {
        WebSocketServer.destroyInstance();
    }
}
