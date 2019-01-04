/*
 * Copyright (c) 2014 Brocade Communications Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.listener;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.opendaylight.netconf.api.xml.XmlNetconfConstants.URN_IETF_PARAMS_XML_NS_NETCONF_BASE_1_0;

import com.google.common.base.CharMatcher;
import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ListenableFuture;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timer;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.GlobalEventExecutor;
import java.io.ByteArrayInputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.xml.parsers.ParserConfigurationException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.api.NetconfTerminationReason;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.client.NetconfClientDispatcherImpl;
import org.opendaylight.netconf.client.NetconfClientSession;
import org.opendaylight.netconf.client.conf.NetconfClientConfiguration;
import org.opendaylight.netconf.client.conf.NetconfReconnectingClientConfiguration;
import org.opendaylight.netconf.client.conf.NetconfReconnectingClientConfigurationBuilder;
import org.opendaylight.netconf.nettyutil.ReconnectStrategy;
import org.opendaylight.netconf.nettyutil.TimedReconnectStrategy;
import org.opendaylight.netconf.nettyutil.handler.ssh.authentication.LoginPasswordHandler;
import org.opendaylight.netconf.sal.connect.api.RemoteDevice;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yangtools.util.xml.UntrustedXML;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class NetconfDeviceCommunicatorTest {

    private static final Logger LOG = LoggerFactory.getLogger(NetconfDeviceCommunicatorTest.class);

    @Mock
    NetconfClientSession mockSession;

    @Mock
    RemoteDevice<NetconfSessionPreferences, NetconfMessage, NetconfDeviceCommunicator> mockDevice;

    NetconfDeviceCommunicator communicator;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        communicator = new NetconfDeviceCommunicator(
                new RemoteDeviceId("test", InetSocketAddress.createUnresolved("localhost", 22)), mockDevice, 10);
    }

    void setupSession() {
        doReturn(Collections.<String>emptySet()).when(mockSession).getServerCapabilities();
        doNothing().when(mockDevice).onRemoteSessionUp(any(NetconfSessionPreferences.class),
                any(NetconfDeviceCommunicator.class));
        communicator.onSessionUp(mockSession);
    }

    private ListenableFuture<RpcResult<NetconfMessage>> sendRequest() throws Exception {
        return sendRequest(UUID.randomUUID().toString(), true);
    }

    @SuppressWarnings("unchecked")
    private ListenableFuture<RpcResult<NetconfMessage>> sendRequest(final String messageID,
                                                                    final boolean doLastTest) throws Exception {
        Document doc = UntrustedXML.newDocumentBuilder().newDocument();
        Element element = doc.createElement("request");
        element.setAttribute("message-id", messageID);
        doc.appendChild(element);
        NetconfMessage message = new NetconfMessage(doc);

        ChannelFuture mockChannelFuture = mock(ChannelFuture.class);
        doReturn(mockChannelFuture).when(mockChannelFuture)
                .addListener(any(GenericFutureListener.class));
        doReturn(mockChannelFuture).when(mockSession).sendMessage(same(message));

        ListenableFuture<RpcResult<NetconfMessage>> resultFuture =
                communicator.sendRequest(message, QName.create("", "mockRpc"));
        if (doLastTest) {
            assertNotNull("ListenableFuture is null", resultFuture);
        }
        return resultFuture;
    }

    @Test
    public void testOnSessionUp() {
        String testCapability = "urn:opendaylight:params:xml:ns:test?module=test-module&revision=2014-06-02";
        Collection<String> serverCapabilities =
                Sets.newHashSet(NetconfMessageTransformUtil.NETCONF_ROLLBACK_ON_ERROR_URI.toString(),
                        NetconfMessageTransformUtil.IETF_NETCONF_MONITORING.getNamespace().toString(),
                        testCapability);
        doReturn(serverCapabilities).when(mockSession).getServerCapabilities();

        ArgumentCaptor<NetconfSessionPreferences> netconfSessionPreferences =
                ArgumentCaptor.forClass(NetconfSessionPreferences.class);
        doNothing().when(mockDevice).onRemoteSessionUp(netconfSessionPreferences.capture(), eq(communicator));

        communicator.onSessionUp(mockSession);

        verify(mockSession).getServerCapabilities();
        verify(mockDevice).onRemoteSessionUp(netconfSessionPreferences.capture(), eq(communicator));

        NetconfSessionPreferences actualCapabilites = netconfSessionPreferences.getValue();
        assertEquals("containsModuleCapability", true, actualCapabilites.containsNonModuleCapability(
                NetconfMessageTransformUtil.NETCONF_ROLLBACK_ON_ERROR_URI.toString()));
        assertEquals("containsModuleCapability", false, actualCapabilites.containsNonModuleCapability(testCapability));
        assertEquals("getModuleBasedCaps", Sets.newHashSet(
                QName.create("urn:opendaylight:params:xml:ns:test", "2014-06-02", "test-module")),
                actualCapabilites.getModuleBasedCaps());
        assertEquals("isRollbackSupported", true, actualCapabilites.isRollbackSupported());
        assertEquals("isMonitoringSupported", true, actualCapabilites.isMonitoringSupported());
    }

    @SuppressWarnings("unchecked")
    @Test(timeout = 5000)
    public void testOnSessionDown() throws Exception {
        setupSession();

        ListenableFuture<RpcResult<NetconfMessage>> resultFuture1 = sendRequest();
        final ListenableFuture<RpcResult<NetconfMessage>> resultFuture2 = sendRequest();

        doNothing().when(mockDevice).onRemoteSessionDown();

        communicator.onSessionDown(mockSession, new Exception("mock ex"));

        verifyErrorRpcResult(resultFuture1.get(), RpcError.ErrorType.TRANSPORT, "operation-failed");
        verifyErrorRpcResult(resultFuture2.get(), RpcError.ErrorType.TRANSPORT, "operation-failed");

        verify(mockDevice).onRemoteSessionDown();

        reset(mockDevice);

        communicator.onSessionDown(mockSession, new Exception("mock ex"));

        verify(mockDevice, never()).onRemoteSessionDown();
    }

    @Test
    public void testOnSessionTerminated() throws Exception {
        setupSession();

        ListenableFuture<RpcResult<NetconfMessage>> resultFuture = sendRequest();

        doNothing().when(mockDevice).onRemoteSessionDown();

        String reasonText = "testing terminate";
        NetconfTerminationReason reason = new NetconfTerminationReason(reasonText);
        communicator.onSessionTerminated(mockSession, reason);

        RpcError rpcError = verifyErrorRpcResult(resultFuture.get(), RpcError.ErrorType.TRANSPORT,
                "operation-failed");
        assertEquals("RpcError message", reasonText, rpcError.getMessage());

        verify(mockDevice).onRemoteSessionDown();
    }

    @Test
    public void testClose() throws Exception {
        communicator.close();
        verify(mockDevice, never()).onRemoteSessionDown();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    public void testSendRequest() throws Exception {
        setupSession();

        NetconfMessage message = new NetconfMessage(UntrustedXML.newDocumentBuilder().newDocument());
        QName rpc = QName.create("", "mockRpc");

        ArgumentCaptor<GenericFutureListener> futureListener =
                ArgumentCaptor.forClass(GenericFutureListener.class);

        ChannelFuture mockChannelFuture = mock(ChannelFuture.class);
        doReturn(mockChannelFuture).when(mockChannelFuture).addListener(futureListener.capture());
        doReturn(mockChannelFuture).when(mockSession).sendMessage(same(message));

        ListenableFuture<RpcResult<NetconfMessage>> resultFuture = communicator.sendRequest(message, rpc);

        verify(mockSession).sendMessage(same(message));

        assertNotNull("ListenableFuture is null", resultFuture);

        verify(mockChannelFuture).addListener(futureListener.capture());
        Future<Void> operationFuture = mock(Future.class);
        doReturn(true).when(operationFuture).isSuccess();
        doReturn(true).when(operationFuture).isDone();
        futureListener.getValue().operationComplete(operationFuture);

        try {
            resultFuture.get(1, TimeUnit.MILLISECONDS); // verify it's not cancelled or has an error set
        } catch (TimeoutException e) {
            LOG.info("Operation failed due timeout.");
        } // expected
    }

    @Test
    public void testSendRequestWithNoSession() throws Exception {
        NetconfMessage message = new NetconfMessage(UntrustedXML.newDocumentBuilder().newDocument());
        QName rpc = QName.create("", "mockRpc");

        ListenableFuture<RpcResult<NetconfMessage>> resultFuture = communicator.sendRequest(message, rpc);

        assertNotNull("ListenableFuture is null", resultFuture);

        // Should have an immediate result
        RpcResult<NetconfMessage> rpcResult = resultFuture.get(3, TimeUnit.MILLISECONDS);

        verifyErrorRpcResult(rpcResult, RpcError.ErrorType.TRANSPORT, "operation-failed");
    }

    private static NetconfMessage createSuccessResponseMessage(final String messageID)
            throws ParserConfigurationException {
        Document doc = UntrustedXML.newDocumentBuilder().newDocument();
        Element rpcReply =
                doc.createElementNS(URN_IETF_PARAMS_XML_NS_NETCONF_BASE_1_0, XmlNetconfConstants.RPC_REPLY_KEY);
        rpcReply.setAttribute("message-id", messageID);
        Element element = doc.createElementNS("ns", "data");
        element.setTextContent(messageID);
        rpcReply.appendChild(element);
        doc.appendChild(rpcReply);

        return new NetconfMessage(doc);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    public void testSendRequestWithWithSendFailure() throws Exception {
        setupSession();

        NetconfMessage message = new NetconfMessage(UntrustedXML.newDocumentBuilder().newDocument());
        QName rpc = QName.create("", "mockRpc");

        ArgumentCaptor<GenericFutureListener> futureListener =
                ArgumentCaptor.forClass(GenericFutureListener.class);

        ChannelFuture mockChannelFuture = mock(ChannelFuture.class);
        doReturn(mockChannelFuture).when(mockChannelFuture).addListener(futureListener.capture());
        doReturn(mockChannelFuture).when(mockSession).sendMessage(same(message));

        ListenableFuture<RpcResult<NetconfMessage>> resultFuture = communicator.sendRequest(message, rpc);

        assertNotNull("ListenableFuture is null", resultFuture);

        verify(mockChannelFuture).addListener(futureListener.capture());

        Future<Void> operationFuture = mock(Future.class);
        doReturn(false).when(operationFuture).isSuccess();
        doReturn(true).when(operationFuture).isDone();
        doReturn(new Exception("mock error")).when(operationFuture).cause();
        futureListener.getValue().operationComplete(operationFuture);

        // Should have an immediate result
        RpcResult<NetconfMessage> rpcResult = resultFuture.get(3, TimeUnit.MILLISECONDS);

        RpcError rpcError = verifyErrorRpcResult(rpcResult, RpcError.ErrorType.TRANSPORT, "operation-failed");
        assertEquals("RpcError message contains \"mock error\"", true,
                rpcError.getMessage().contains("mock error"));
    }

    //Test scenario verifying whether missing message is handled
    @Test
    public void testOnMissingResponseMessage() throws Exception {

        setupSession();

        String messageID1 = UUID.randomUUID().toString();
        ListenableFuture<RpcResult<NetconfMessage>> resultFuture1 = sendRequest(messageID1, true);

        String messageID2 = UUID.randomUUID().toString();
        ListenableFuture<RpcResult<NetconfMessage>> resultFuture2 = sendRequest(messageID2, true);

        String messageID3 = UUID.randomUUID().toString();
        ListenableFuture<RpcResult<NetconfMessage>> resultFuture3 = sendRequest(messageID3, true);

        //response messages 1,2 are omitted
        communicator.onMessage(mockSession, createSuccessResponseMessage(messageID3));

        verifyResponseMessage(resultFuture3.get(), messageID3);
    }

    @Test
    public void testOnSuccessfulResponseMessage() throws Exception {
        setupSession();

        String messageID1 = UUID.randomUUID().toString();
        ListenableFuture<RpcResult<NetconfMessage>> resultFuture1 = sendRequest(messageID1, true);

        String messageID2 = UUID.randomUUID().toString();
        final ListenableFuture<RpcResult<NetconfMessage>> resultFuture2 = sendRequest(messageID2, true);

        communicator.onMessage(mockSession, createSuccessResponseMessage(messageID1));
        communicator.onMessage(mockSession, createSuccessResponseMessage(messageID2));

        verifyResponseMessage(resultFuture1.get(), messageID1);
        verifyResponseMessage(resultFuture2.get(), messageID2);
    }

    @Test
    public void testOnResponseMessageWithError() throws Exception {
        setupSession();

        String messageID = UUID.randomUUID().toString();
        ListenableFuture<RpcResult<NetconfMessage>> resultFuture = sendRequest(messageID, true);

        communicator.onMessage(mockSession, createErrorResponseMessage(messageID));

        RpcError rpcError = verifyErrorRpcResult(resultFuture.get(), RpcError.ErrorType.RPC,
                "missing-attribute");
        assertEquals("RpcError message", "Missing attribute", rpcError.getMessage());

        String errorInfo = rpcError.getInfo();
        assertNotNull("RpcError info is null", errorInfo);
        assertTrue("Error info contains \"foo\"", errorInfo.contains("<bad-attribute>foo</bad-attribute>"));
        assertTrue("Error info contains \"bar\"", errorInfo.contains("<bad-element>bar</bad-element>"));
    }

    /**
     * Test whether reconnect is scheduled properly.
     */
    @Test
    public void testNetconfDeviceReconnectInCommunicator() throws Exception {
        final RemoteDevice<NetconfSessionPreferences, NetconfMessage, NetconfDeviceCommunicator> device =
                mock(RemoteDevice.class);

        final TimedReconnectStrategy timedReconnectStrategy =
                new TimedReconnectStrategy(GlobalEventExecutor.INSTANCE, 10000, 0, 1.0, null, 100L, null);
        final ReconnectStrategy reconnectStrategy = spy(new ReconnectStrategy() {
            @Override
            public int getConnectTimeout() throws Exception {
                return timedReconnectStrategy.getConnectTimeout();
            }

            @Override
            public Future<Void> scheduleReconnect(final Throwable cause) {
                return timedReconnectStrategy.scheduleReconnect(cause);
            }

            @Override
            public void reconnectSuccessful() {
                timedReconnectStrategy.reconnectSuccessful();
            }
        });

        final EventLoopGroup group = new NioEventLoopGroup();
        final Timer time = new HashedWheelTimer();
        try {
            final NetconfDeviceCommunicator listener = new NetconfDeviceCommunicator(
                    new RemoteDeviceId("test", InetSocketAddress.createUnresolved("localhost", 22)), device, 10);
            final NetconfReconnectingClientConfiguration cfg = NetconfReconnectingClientConfigurationBuilder.create()
                    .withAddress(new InetSocketAddress("localhost", 65000))
                    .withReconnectStrategy(reconnectStrategy)
                    .withConnectStrategyFactory(() -> reconnectStrategy)
                    .withAuthHandler(new LoginPasswordHandler("admin", "admin"))
                    .withConnectionTimeoutMillis(10000)
                    .withProtocol(NetconfClientConfiguration.NetconfClientProtocol.SSH)
                    .withSessionListener(listener)
                    .build();

            listener.initializeRemoteConnection(new NetconfClientDispatcherImpl(group, group, time), cfg);

            verify(reconnectStrategy,
                    timeout((int) TimeUnit.MINUTES.toMillis(3)).times(101)).scheduleReconnect(any(Throwable.class));
        } finally {
            time.stop();
            group.shutdownGracefully();
        }
    }

    @Test
    public void testOnResponseMessageWithWrongMessageID() throws Exception {
        setupSession();

        String messageID = UUID.randomUUID().toString();
        ListenableFuture<RpcResult<NetconfMessage>> resultFuture = sendRequest(messageID, true);

        communicator.onMessage(mockSession, createSuccessResponseMessage(UUID.randomUUID().toString()));

        RpcError rpcError = verifyErrorRpcResult(resultFuture.get(), RpcError.ErrorType.PROTOCOL,
                "bad-attribute");
        assertFalse("RpcError message non-empty", Strings.isNullOrEmpty(rpcError.getMessage()));

        String errorInfo = rpcError.getInfo();
        assertNotNull("RpcError info is null", errorInfo);
        assertTrue("Error info contains \"actual-message-id\"", errorInfo.contains("actual-message-id"));
        assertTrue("Error info contains \"expected-message-id\"", errorInfo.contains("expected-message-id"));
    }

    @Test
    public void testConcurrentMessageLimit() throws Exception {
        setupSession();
        ArrayList<String> messageID = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            messageID.add(UUID.randomUUID().toString());
            ListenableFuture<RpcResult<NetconfMessage>> resultFuture = sendRequest(messageID.get(i), false);
            assertEquals("ListenableFuture is null", true, resultFuture instanceof UncancellableFuture);
        }

        final String notWorkingMessageID = UUID.randomUUID().toString();
        ListenableFuture<RpcResult<NetconfMessage>> resultFuture = sendRequest(notWorkingMessageID, false);
        assertEquals("ListenableFuture is null", false, resultFuture instanceof UncancellableFuture);

        communicator.onMessage(mockSession, createSuccessResponseMessage(messageID.get(0)));

        resultFuture = sendRequest(messageID.get(0), false);
        assertNotNull("ListenableFuture is null", resultFuture);
    }

    private static NetconfMessage createErrorResponseMessage(final String messageID) throws Exception {
        String xmlStr = "<rpc-reply xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\""
                + "           message-id=\"" + messageID + "\">"
                + "  <rpc-error>"
                + "    <error-type>rpc</error-type>"
                + "    <error-tag>missing-attribute</error-tag>"
                + "    <error-severity>error</error-severity>"
                + "    <error-message>Missing attribute</error-message>"
                + "    <error-info>"
                + "      <bad-attribute>foo</bad-attribute>"
                + "      <bad-element>bar</bad-element>"
                + "    </error-info>"
                + "  </rpc-error>"
                + "</rpc-reply>";

        ByteArrayInputStream bis = new ByteArrayInputStream(xmlStr.getBytes());
        Document doc = UntrustedXML.newDocumentBuilder().parse(bis);
        return new NetconfMessage(doc);
    }

    private static void verifyResponseMessage(final RpcResult<NetconfMessage> rpcResult, final String dataText) {
        assertNotNull("RpcResult is null", rpcResult);
        assertTrue("isSuccessful", rpcResult.isSuccessful());
        NetconfMessage messageResult = rpcResult.getResult();
        assertNotNull("getResult", messageResult);
//        List<SimpleNode<?>> nodes = messageResult.getSimpleNodesByName(
//                                         QName.create( URI.create( "ns" ), null, "data" ) );
//        assertNotNull( "getSimpleNodesByName", nodes );
//        assertEquals( "List<SimpleNode<?>> size", 1, nodes.size() );
//        assertEquals( "SimpleNode value", dataText, nodes.iterator().next().getValue() );
    }

    private static RpcError verifyErrorRpcResult(final RpcResult<NetconfMessage> rpcResult,
                                                 final RpcError.ErrorType expErrorType, final String expErrorTag) {
        assertNotNull("RpcResult is null", rpcResult);
        assertFalse("isSuccessful", rpcResult.isSuccessful());
        assertNotNull("RpcResult errors is null", rpcResult.getErrors());
        assertEquals("Errors size", 1, rpcResult.getErrors().size());
        RpcError rpcError = rpcResult.getErrors().iterator().next();
        assertEquals("getErrorSeverity", RpcError.ErrorSeverity.ERROR, rpcError.getSeverity());
        assertEquals("getErrorType", expErrorType, rpcError.getErrorType());
        assertEquals("getErrorTag", expErrorTag, rpcError.getTag());

        final String msg = rpcError.getMessage();
        assertNotNull("getMessage is null", msg);
        assertFalse("getMessage is empty", msg.isEmpty());
        assertFalse("getMessage is blank", CharMatcher.whitespace().matchesAllOf(msg));
        return rpcError;
    }
}
