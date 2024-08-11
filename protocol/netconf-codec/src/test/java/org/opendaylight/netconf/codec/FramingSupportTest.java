/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import java.io.IOException;
import java.io.OutputStream;
import javax.xml.transform.TransformerException;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.api.messages.NetconfMessage;

@ExtendWith(MockitoExtension.class)
class FramingSupportTest {
    @Mock
    private NetconfMessage message;

    final void writeBytes(final FramingSupport framing, final byte[] bytes, final ByteBuf out) {
        try {
            framing.writeMessage(ByteBufAllocator.DEFAULT, message, new MessageWriter(false) {
                @Override
                protected void writeTo(final NetconfMessage ignored, final OutputStream out)
                        throws IOException {
                    out.write(bytes);
                }
            }, out);
        } catch (IOException | TransformerException e) {
            throw new AssertionError(e);
        }
    }
}
