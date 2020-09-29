/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil.handler.ssh.client;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import io.netty.buffer.ByteBuf;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class AsyncSshHandlerWriterTest {

    private ByteBuf byteBuf;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        byteBuf = mock(ByteBuf.class, Mockito.CALLS_REAL_METHODS);
        doReturn(byteBuf).when(byteBuf).resetReaderIndex();
    }

    @Test
    public void testByteBufToString() {
        String testText = "Lorem Ipsum 0123456780!@#$%^&*<>\\|/?[]()\n\r";
        doReturn(testText).when(byteBuf).toString(ArgumentMatchers.any());
        assertEquals(testText, AsyncSshHandlerWriter.byteBufToString(byteBuf));

        testText = "Lorem Ipsum" + (char) 0x8 + " 0123456780" + (char) 0x11 + (char) 0x7F + "9 !@#$%^&*<>\\|/?[]()\n\r";
        doReturn(testText).when(byteBuf).toString(ArgumentMatchers.any());
        assertEquals("Lorem Ipsum\"08\" 0123456780\"117F\"9 !@#$%^&*<>\\|/?[]()\n\r",
                AsyncSshHandlerWriter.byteBufToString(byteBuf));
    }
}