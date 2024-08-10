/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.api.CapabilityURN;
import org.opendaylight.netconf.api.NetconfSessionListener;
import org.opendaylight.netconf.api.messages.HelloMessage;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.codec.ChunkedFrameDecoder;
import org.opendaylight.netconf.codec.ChunkedFrameEncoder;
import org.opendaylight.netconf.codec.EOMFrameDecoder;
import org.opendaylight.netconf.codec.EOMFrameEncoder;
import org.opendaylight.netconf.codec.FrameDecoder;
import org.opendaylight.netconf.codec.FrameEncoder;
import org.opendaylight.netconf.codec.MessageDecoder;
import org.opendaylight.netconf.codec.MessageEncoder;
import org.opendaylight.netconf.common.NetconfTimer;
import org.opendaylight.netconf.nettyutil.handler.HelloXMLMessageDecoder;
import org.opendaylight.netconf.nettyutil.handler.XMLMessageWriter;

@ExtendWith(MockitoExtension.class)
class AbstractNetconfSessionNegotiatorTest {
    @Mock
    private NetconfSessionListener<TestingNetconfSession> listener;
    @Mock
    private Promise<TestingNetconfSession> promise;
    @Mock
    private SslHandler sslHandler;
    @Mock
    private NetconfTimer timer;
    @Mock
    private Timeout timeout;
    private TestSessionNegotiator negotiator;

    private final HelloMessage hello = HelloMessage.createClientHello(Set.of(), Optional.empty());
    private final HelloMessage helloBase11 =
        HelloMessage.createClientHello(Set.of(CapabilityURN.BASE_1_1), Optional.empty());
    private final HelloXMLMessageDecoder xmlToHello  = new HelloXMLMessageDecoder();
    private final EmbeddedChannel channel = new EmbeddedChannel();

    @BeforeEach
    void setUp() {
        channel.pipeline()
            .addLast("mockEncoder", new MessageEncoder(XMLMessageWriter.pretty()))
            .addLast(MessageDecoder.HANDLER_NAME, xmlToHello)
            .addLast(FrameEncoder.HANDLER_NAME, new EOMFrameEncoder())
            .addLast(FrameDecoder.HANDLER_NAME, new EOMFrameDecoder());
        negotiator = new TestSessionNegotiator(helloBase11, promise, channel, timer, listener, 100L);
    }

    @Test
    void testStartNegotiation() {
        enableTimerTask();
        negotiator.startNegotiation();

        final var buf = assertInstanceOf(ByteBuf.class, channel.readOutbound());
        assertEquals("""
            <?xml version="1.0" encoding="UTF-8" standalone="no"?>
            <hello xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
                <capabilities>
                    <capability>urn:ietf:params:netconf:base:1.1</capability>
                </capabilities>
            </hello>
            """, new String(ByteBufUtil.getBytes(buf), StandardCharsets.UTF_8));
    }

    @Test
    void testStartNegotiationSsl() throws Exception {
        doReturn(true).when(sslHandler).isSharable();
        doNothing().when(sslHandler).handlerAdded(any());
        doNothing().when(sslHandler).write(any(), any(), any());
        final Future<EmbeddedChannel> handshakeFuture = channel.eventLoop().newSucceededFuture(channel);
        doReturn(handshakeFuture).when(sslHandler).handshakeFuture();
        doNothing().when(sslHandler).flush(any());
        channel.pipeline().addLast(sslHandler);

        enableTimerTask();
        negotiator.startNegotiation();
        verify(sslHandler).write(any(), eq(helloBase11), any());
    }

    @Test
    void testStartNegotiationNotEstablished() throws Exception {
        final var closedDetector = spy(new CloseDetector());
        channel.pipeline().addLast("closedDetector", closedDetector);
        doReturn(false).when(promise).isDone();
        doReturn(false).when(promise).isCancelled();

        final var captor = ArgumentCaptor.forClass(TimerTask.class);
        doReturn(timeout).when(timer).newTimeout(captor.capture(), eq(100L), eq(TimeUnit.MILLISECONDS));
        negotiator.startNegotiation();

        captor.getValue().run(timeout);
        channel.runPendingTasks();
        verify(closedDetector).close(any(), any());
    }

    @Test
    void testGetSessionForHelloMessage() throws Exception {
        enableTimerTask();
        negotiator.startNegotiation();
        final var session = negotiator.getSessionForHelloMessage(hello);
        assertNotNull(session);
        final var pipeline = channel.pipeline();
        assertInstanceOf(EOMFrameDecoder.class, pipeline.get(FrameDecoder.HANDLER_NAME));
        assertInstanceOf(EOMFrameEncoder.class, pipeline.get(FrameEncoder.HANDLER_NAME));
    }

    @Test
    void testGetSessionForHelloMessageBase11() throws Exception {
        enableTimerTask();
        negotiator.startNegotiation();
        final var session = negotiator.getSessionForHelloMessage(helloBase11);
        assertNotNull(session);
        final var pipeline = channel.pipeline();
        assertInstanceOf(ChunkedFrameDecoder.class, pipeline.get(FrameDecoder.HANDLER_NAME));
        assertInstanceOf(ChunkedFrameEncoder.class, pipeline.get(FrameEncoder.HANDLER_NAME));
    }

    @Test
    void testReplaceHelloMessageInboundHandler() throws Exception {
        final List<Object> out = new ArrayList<>();
        final byte[] msg = "<rpc/>".getBytes();
        final ByteBuf msgBuf = Unpooled.wrappedBuffer(msg);
        final ByteBuf helloBuf = Unpooled.wrappedBuffer(XmlUtil.toString(hello.getDocument()).getBytes());

        enableTimerTask();
        negotiator.startNegotiation();

        xmlToHello.decode(null, helloBuf, out);
        xmlToHello.decode(null, msgBuf, out);
        final TestingNetconfSession session = mock(TestingNetconfSession.class);
        doNothing().when(session).handleMessage(any());
        negotiator.replaceHelloMessageInboundHandler(session);
        verify(session, times(1)).handleMessage(any());
    }

    @Test
    void testNegotiationFail() {
        doReturn(promise).when(promise).setFailure(any());

        enableTimerTask();
        doReturn(true).when(timeout).cancel();
        negotiator.startNegotiation();
        final RuntimeException cause = new RuntimeException("failure cause");
        channel.pipeline().fireExceptionCaught(cause);
        verify(promise).setFailure(cause);
    }

    private void enableTimerTask() {
        doReturn(timeout).when(timer).newTimeout(any(), eq(100L), eq(TimeUnit.MILLISECONDS));
    }

    private static final class CloseDetector extends ChannelOutboundHandlerAdapter {
        @Override
        public void close(final ChannelHandlerContext ctx, final ChannelPromise promise) {
            // Override needed so @Skip from superclass is not effective
        }
    }
}
