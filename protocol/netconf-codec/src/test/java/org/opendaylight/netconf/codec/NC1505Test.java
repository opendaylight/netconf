/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.netty.buffer.Unpooled;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.ResourceLeakDetectorFactory;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NC1505Test {

    @BeforeAll
    static void before() {
        ResourceLeakDetectorFactory.setResourceLeakDetectorFactory(new TestResourceLeakDetectorFactory());
    }

    @Test
    void testResourceByteBufLeak() {
        final var localDecoder = new ChunkedFrameDecoder(16 * 1024 * 1024);
        final var message = """

            #4
            <rpc
            #16000002
             %s

            #79
                 xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
              <close-session/>
            </rpc>
            ##
            """.formatted("a".repeat(16000000));
        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.PARANOID);
        try {
            for (int i = 0; i < 5; i++) {
                var output = new ArrayList<>();
                localDecoder.decode(null, Unpooled.copiedBuffer(message.getBytes(StandardCharsets.UTF_8)), output);
                assertEquals(1, output.size());
                assertFalse(TestResourceLeakDetector.LEAK_DETECTED.get(), "ResourceLeakDetector reported a LEAK");
            }
        } finally {
            ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.SIMPLE);
        }
    }

    static final class TestResourceLeakDetectorFactory extends ResourceLeakDetectorFactory {
        @Override
        public <T> ResourceLeakDetector<T> newResourceLeakDetector(final Class<T> resource,
            final int samplingInterval, final long maxActive) {
            return new TestResourceLeakDetector<>(resource, samplingInterval);
        }
    }

    private static class TestResourceLeakDetector<T> extends ResourceLeakDetector<T> {
        static final AtomicBoolean LEAK_DETECTED = new AtomicBoolean();

        TestResourceLeakDetector(final Class<T> resource, final int samplingInterval) {
            super(resource, samplingInterval);
        }

        @Override
        protected void reportTracedLeak(final String resourceType, final String records) {
            LEAK_DETECTED.set(true);
            super.reportTracedLeak(resourceType, records);
        }
    }
}
