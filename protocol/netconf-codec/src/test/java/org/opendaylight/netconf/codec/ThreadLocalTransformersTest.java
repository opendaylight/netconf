/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.codec;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ThreadLocalTransformersTest {
    private ExecutorService executorService;

    @BeforeEach
    void beforeEach() {
        executorService = Executors.newSingleThreadExecutor();
    }

    @AfterEach
    void afterEach() {
        executorService.shutdown();
    }

    @Test
    void testGetDefaultTransformer() throws Exception {
        final var t1 = ThreadLocalTransformers.getDefaultTransformer();
        final var t2 = ThreadLocalTransformers.getDefaultTransformer();
        assertSame(t1, t2);
        final var future = executorService.submit(ThreadLocalTransformers::getDefaultTransformer);
        assertNotSame(t1, future.get());
    }

    @Test
    void testGetPrettyTransformer() throws Exception {
        final var t1 = ThreadLocalTransformers.getPrettyTransformer();
        final var t2 = ThreadLocalTransformers.getPrettyTransformer();
        assertSame(t1, t2);
        final var future = executorService.submit(ThreadLocalTransformers::getPrettyTransformer);
        assertNotSame(t1, future.get());
    }

    @AfterEach
    void tearDown() {
        executorService.shutdown();
    }
}
