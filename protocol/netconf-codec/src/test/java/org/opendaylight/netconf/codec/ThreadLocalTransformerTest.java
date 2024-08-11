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
import static org.opendaylight.netconf.codec.MessageWriter.DEFAULT_TRANSFORMER;
import static org.opendaylight.netconf.codec.MessageWriter.PRETTY_TRANSFORMER;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ThreadLocalTransformerTest {
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
        final var t1 = DEFAULT_TRANSFORMER.get();
        final var t2 = DEFAULT_TRANSFORMER.get();
        assertSame(t1, t2);
        final var future = executorService.submit(DEFAULT_TRANSFORMER::get);
        assertNotSame(t1, future.get());
    }

    @Test
    void testGetPrettyTransformer() throws Exception {
        final var t1 = PRETTY_TRANSFORMER.get();
        final var t2 = PRETTY_TRANSFORMER.get();
        assertSame(t1, t2);
        final var future = executorService.submit(PRETTY_TRANSFORMER::get);
        assertNotSame(t1, future.get());
    }
}
