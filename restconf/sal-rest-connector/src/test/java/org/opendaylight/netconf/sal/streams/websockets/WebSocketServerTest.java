/*
 * Copyright (c) 2017 Inocybe Technologies, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.streams.websockets;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class WebSocketServerTest {
    private WebSocketServer instance;
    private final int port = 8889;

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
        Thread webServer = new Thread(() -> {
            instance.run();
        });
        webServer.start();

        Assert.assertEquals(Thread.State.RUNNABLE, webServer.getState());

    }

    @After
    public void destroy() {
        WebSocketServer.destroyInstance();
    }
}
