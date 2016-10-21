/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.nettyutil.handler;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.xml.transform.Transformer;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ThreadLocalTransformersTest {

    private ExecutorService executorService;

    @Before
    public void setUp() throws Exception {
        executorService = Executors.newSingleThreadExecutor();
    }

    @Test
    public void testGetDefaultTransformer() throws Exception {
        final Transformer t1 = ThreadLocalTransformers.getDefaultTransformer();
        final Transformer t2 = ThreadLocalTransformers.getDefaultTransformer();
        Assert.assertSame(t1, t2);
        final Future<Transformer> future = executorService.submit(ThreadLocalTransformers::getDefaultTransformer);
        Assert.assertNotSame(t1, future.get());
    }

    @Test
    public void testGetPrettyTransformer() throws Exception {
        final Transformer t1 = ThreadLocalTransformers.getPrettyTransformer();
        final Transformer t2 = ThreadLocalTransformers.getPrettyTransformer();
        Assert.assertSame(t1, t2);
        final Future<Transformer> future = executorService.submit(ThreadLocalTransformers::getPrettyTransformer);
        Assert.assertNotSame(t1, future.get());
    }

    @After
    public void tearDown() throws Exception {
        executorService.shutdown();
    }
}