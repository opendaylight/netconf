/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

class EOMFrameEncoderTest {
    @Test
    void testEncode() {
        final var source = Unpooled.wrappedBuffer(new byte[50]);
        final var destination = Unpooled.buffer();
        new EOMFrameEncoder().encode(null, source, destination);

        assertEquals(Unpooled.wrappedBuffer(source.array(), MessageParts.END_OF_MESSAGE), destination);
    }
}
