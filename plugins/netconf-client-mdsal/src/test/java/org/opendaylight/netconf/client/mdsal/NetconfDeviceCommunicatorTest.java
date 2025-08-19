/*
 * Copyright (c) 2014 Brocade Communications Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.common.base.CharMatcher;
import com.google.common.base.Strings;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import java.io.ByteArrayInputStream;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.api.CapabilityURN;
import org.opendaylight.netconf.api.NamespaceURN;
import org.opendaylight.netconf.api.NetconfTerminationReason;
import org.opendaylight.netconf.api.messages.NetconfMessage;
import org.opendaylight.netconf.api.messages.RpcReplyMessage;
import org.opendaylight.netconf.client.NetconfClientSession;
import org.opendaylight.netconf.client.NetconfClientSessionListener;
import org.opendaylight.netconf.client.mdsal.api.NetconfSessionPreferences;
import org.opendaylight.netconf.client.mdsal.api.RemoteDevice;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.NetconfState;
import org.opendaylight.yangtools.util.xml.UntrustedXML;
import org.opendaylight.yangtools.yang.common.ErrorSeverity;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.Uint32;

@ExtendWith(MockitoExtension.class)
class NetconfDeviceCommunicatorTest {
    private static final SessionIdType SESSION_ID = new SessionIdType(Uint32.ONE);
    private static final int RPC_MESSAGE_LIMIT = 10;

    @Mock
    private RemoteDevice<NetconfDeviceCommunicator> mockDevice;
    @Mock
    private ChannelFuture mockChannelFuture;
    @Mock
    private Future<Void> operationFuture;

    private NetconfClientSession spySession;
    private NetconfDeviceCommunicator communicator;

    @BeforeEach
    void setUp() {
        communicator = new NetconfDeviceCommunicator(new RemoteDeviceId("test",
            InetSocketAddress.createUnresolved("localhost", 22)), mockDevice, RPC_MESSAGE_LIMIT);
        spySession = spy(new NetconfClientSession(mock(NetconfClientSessionListener.class), mock(Channel.class),
            SESSION_ID, Set.of()));
    }

    void setupSession() {
        doNothing().when(mockDevice).onRemoteSessionUp(any(NetconfSessionPreferences.class),
                any(NetconfDeviceCommunicator.class));
        communicator.onSessionUp(spySession);
    }

    private ListenableFuture<RpcResult<NetconfMessage>> sendRequest() {
        return sendRequest(UUID.randomUUID().toString(), true);
    }

    @SuppressWarnings("unchecked")
    private ListenableFuture<RpcResult<NetconfMessage>> sendRequest(final String messageID,
            final boolean doLastTest) {
        final var message = createTestMessage(messageID);
        doReturn(mockChannelFuture).when(mockChannelFuture)
                .addListener(any(GenericFutureListener.class));
        doReturn(mockChannelFuture).when(spySession).sendMessage(same(message));

        final var resultFuture = communicator.sendRequest(message);
        if (doLastTest) {
            assertNotNull(resultFuture, "ListenableFuture is null");
        }
        return resultFuture;
    }

    private ListenableFuture<RpcResult<NetconfMessage>> sendRequestWithoutMocking(final String messageID,
            final boolean doLastTest) {
        final var resultFuture = communicator.sendRequest(createTestMessage(messageID));
        if (doLastTest) {
            assertNotNull(resultFuture, "ListenableFuture is null");
        }
        return resultFuture;
    }

    @Test
    void testOnSessionUp() {
        final var testCapability = "urn:opendaylight:params:xml:ns:test?module=test-module&revision=2014-06-02";
        final var serverCapabilities = Set.of(
            CapabilityURN.ROLLBACK_ON_ERROR,
            NetconfState.QNAME.getNamespace().toString(),
            testCapability);
        doReturn(serverCapabilities).when(spySession).getServerCapabilities();

        final var netconfSessionPreferences = ArgumentCaptor.forClass(NetconfSessionPreferences.class);
        doNothing().when(mockDevice).onRemoteSessionUp(netconfSessionPreferences.capture(), eq(communicator));

        communicator.onSessionUp(spySession);

        verify(spySession).getServerCapabilities();
        verify(mockDevice).onRemoteSessionUp(netconfSessionPreferences.capture(), eq(communicator));

        NetconfSessionPreferences actualCapabilites = netconfSessionPreferences.getValue();
        assertTrue(actualCapabilites.containsNonModuleCapability(
                "urn:ietf:params:netconf:capability:rollback-on-error:1.0"));
        assertFalse(actualCapabilites.containsNonModuleCapability(testCapability));
        assertEquals(Set.of(QName.create("urn:opendaylight:params:xml:ns:test", "2014-06-02", "test-module")),
                actualCapabilites.moduleBasedCaps().keySet());
        assertTrue(actualCapabilites.isRollbackSupported());
        assertTrue(actualCapabilites.isMonitoringSupported());
        assertEquals(SESSION_ID, actualCapabilites.sessionId());
    }

    @Test
    void testOnSessionDown() {
        assertTimeout(Duration.ofMillis(5000), () -> {
            setupSession();

            final var resultFuture1 = sendRequest();
            final var resultFuture2 = sendRequest();

            doNothing().when(mockDevice).onRemoteSessionDown();

            communicator.onSessionDown(spySession, new Exception("mock ex"));

            verifyErrorRpcResult(resultFuture1.get(), ErrorType.TRANSPORT, ErrorTag.OPERATION_FAILED);
            verifyErrorRpcResult(resultFuture2.get(), ErrorType.TRANSPORT, ErrorTag.OPERATION_FAILED);

            verify(mockDevice).onRemoteSessionDown();

            reset(mockDevice);

            communicator.onSessionDown(spySession, new Exception("mock ex"));

            verify(mockDevice, never()).onRemoteSessionDown();
        });
    }

    @Test
    void testOnSessionTerminated() throws Exception {
        setupSession();

        final var resultFuture = sendRequest();

        doNothing().when(mockDevice).onRemoteSessionDown();

        final var reasonText = "testing terminate";
        final var reason = new NetconfTerminationReason(reasonText);
        communicator.onSessionTerminated(spySession, reason);

        final var rpcError = verifyErrorRpcResult(resultFuture.get(), ErrorType.TRANSPORT, ErrorTag.OPERATION_FAILED);
        assertEquals(reasonText, rpcError.getMessage(), "RpcError message");

        verify(mockDevice).onRemoteSessionDown();
    }

    @Test
    void testClose() {
        communicator.close();
        verify(mockDevice, never()).onRemoteSessionDown();
    }

    @Test
    void testMessageLimitAfterDisconnect() throws Exception {
        // Prepare environment.
        setupSession();
        final ArgumentCaptor<GenericFutureListener<Future<Void>>> futureListener = ArgumentCaptor.captor();
        doReturn(mockChannelFuture).when(mockChannelFuture).addListener(futureListener.capture());
        final var message = new NetconfMessage(UntrustedXML.newDocumentBuilder().newDocument());
        doReturn(mockChannelFuture).when(spySession).sendMessage(same(message));
        doReturn(true).when(spySession).isUp();
        doAnswer(invocationOnMock -> {
            communicator.onSessionTerminated(spySession,  new NetconfTerminationReason("Session closed"));
            return null;
        }).when(spySession).close();

        // Reach max-connection-attempts.
        for (int i = 1; i <= RPC_MESSAGE_LIMIT; i++) {
            final var resultFuture = communicator.sendRequest(message);
            assertInstanceOf(UncancellableFuture.class, resultFuture,
                String.format("The resultFuture has an incorrect type: %s", resultFuture));
            verify(spySession, times(i)).sendMessage(same(message));
            communicator.disconnect();
            communicator.onSessionUp(spySession);
        }

        // Verify that more requests can be sent because the semaphore counter is not 0.
        final var resultFuture = communicator.sendRequest(message);
        assertInstanceOf(UncancellableFuture.class, resultFuture,
            String.format("The resultFuture has an incorrect type: %s", resultFuture));
        verify(spySession, times(RPC_MESSAGE_LIMIT + 1)).sendMessage(same(message));
        verify(mockChannelFuture, times(RPC_MESSAGE_LIMIT + 1)).addListener(futureListener.capture());
    }

    @Test
    void testSendRequest() throws Exception {
        setupSession();

        final var message = new NetconfMessage(UntrustedXML.newDocumentBuilder().newDocument());

        final ArgumentCaptor<GenericFutureListener<Future<Void>>> futureListener = ArgumentCaptor.captor();

        doReturn(mockChannelFuture).when(mockChannelFuture).addListener(futureListener.capture());
        doReturn(mockChannelFuture).when(spySession).sendMessage(same(message));

        final var resultFuture = communicator.sendRequest(message);

        verify(spySession).sendMessage(same(message));

        assertNotNull(resultFuture, "ListenableFuture is null");

        verify(mockChannelFuture).addListener(futureListener.capture());
        doReturn(null).when(operationFuture).cause();
        futureListener.getValue().operationComplete(operationFuture);

        // verify it is not cancelled nor has an error set
        assertFalse(resultFuture.isDone());
    }

    @Test
    void testSendRequestWithNoSession() throws Exception {
        final var message = new NetconfMessage(UntrustedXML.newDocumentBuilder().newDocument());

        final var resultFuture = communicator.sendRequest(message);

        assertNotNull(resultFuture, "ListenableFuture is null");

        // Should have an immediate result
        final var rpcResult = Futures.getDone(resultFuture);

        verifyErrorRpcResult(rpcResult, ErrorType.TRANSPORT, ErrorTag.OPERATION_FAILED);
    }

    private static NetconfMessage createSuccessResponseMessage(final String messageID) {
        final var doc = UntrustedXML.newDocumentBuilder().newDocument();
        final var rpcReply = doc.createElementNS(NamespaceURN.BASE, RpcReplyMessage.ELEMENT_NAME);
        rpcReply.setAttribute("message-id", messageID);
        final var element = doc.createElementNS("ns", "data");
        element.setTextContent(messageID);
        rpcReply.appendChild(element);
        doc.appendChild(rpcReply);

        return new NetconfMessage(doc);
    }

    @SuppressWarnings("unchecked")
    @Test
    void testSendRequestWithWithSendFailure() throws Exception {
        setupSession();

        final var message = new NetconfMessage(UntrustedXML.newDocumentBuilder().newDocument());

        final var futureListener = ArgumentCaptor.forClass(GenericFutureListener.class);

        doReturn(mockChannelFuture).when(mockChannelFuture).addListener(futureListener.capture());
        doReturn(mockChannelFuture).when(spySession).sendMessage(same(message));

        final var resultFuture = communicator.sendRequest(message);

        assertNotNull(resultFuture, "ListenableFuture is null");

        verify(mockChannelFuture).addListener(futureListener.capture());

        doReturn(new Exception("mock error")).when(operationFuture).cause();
        futureListener.getValue().operationComplete(operationFuture);

        // Should have an immediate result
        final var rpcResult = Futures.getDone(resultFuture);

        final var rpcError = verifyErrorRpcResult(rpcResult, ErrorType.TRANSPORT, ErrorTag.OPERATION_FAILED);
        assertEquals(true, rpcError.getMessage().contains("mock error"),
            "RpcError message contains \"mock error\"");
    }

    //Test scenario verifying whether missing message is handled
    @Test
    void testOnMissingResponseMessage() throws Exception {

        setupSession();

        final var messageID = UUID.randomUUID().toString();
        final var resultFuture = sendRequest(messageID, true);

        //response messages 1,2 are omitted
        communicator.onMessage(spySession, createSuccessResponseMessage(messageID));

        verifyResponseMessage(resultFuture.get(), messageID);
    }

    @Test
    void testOnSuccessfulResponseMessage() throws Exception {
        setupSession();

        final var messageID1 = UUID.randomUUID().toString();
        final var resultFuture1 = sendRequest(messageID1, true);

        final var messageID2 = UUID.randomUUID().toString();
        final var resultFuture2 = sendRequest(messageID2, true);

        communicator.onMessage(spySession, createSuccessResponseMessage(messageID1));
        communicator.onMessage(spySession, createSuccessResponseMessage(messageID2));

        verifyResponseMessage(resultFuture1.get(), messageID1);
        verifyResponseMessage(resultFuture2.get(), messageID2);
    }

    @Test
    void testOnResponseMessageWithError() throws Exception {
        setupSession();

        final var messageID = UUID.randomUUID().toString();
        final var resultFuture = sendRequest(messageID, true);

        communicator.onMessage(spySession, createErrorResponseMessage(messageID));

        final var rpcError = verifyErrorRpcResult(resultFuture.get(), ErrorType.RPC, ErrorTag.MISSING_ATTRIBUTE);
        assertEquals("Missing attribute", rpcError.getMessage(), "RpcError message");

        final var errorInfo = rpcError.getInfo();
        assertNotNull("RpcError info is null", errorInfo);
        assertTrue(errorInfo.contains("<bad-attribute>foo</bad-attribute>"), "Error info contains \"foo\"");
        assertTrue(errorInfo.contains("<bad-element>bar</bad-element>"), "Error info contains \"bar\"");
    }

    @Test
    void testOnResponseMessageWithMultipleErrors() throws Exception {
        setupSession();

        final var messageID = UUID.randomUUID().toString();
        final var resultFuture = sendRequest(messageID, true);

        communicator.onMessage(spySession, createMultiErrorResponseMessage(messageID));

        final var rpcError = verifyErrorRpcResult(resultFuture.get(), ErrorType.PROTOCOL, ErrorTag.OPERATION_FAILED);

        final var errorInfo = rpcError.getInfo();
        assertNotNull("RpcError info is null", errorInfo);

        final var errorInfoMessages = rpcError.getInfo();
        final var errMsg1 = "Number of member links configured, i.e [1], "
                + "for interface [ae0]is lesser than the required minimum [2].";
        final var errMsg2 = "configuration check-out failed";
        assertTrue(errorInfoMessages.contains(errMsg1) && errorInfoMessages.contains(errMsg2),
            String.format("Error info contains \"%s\" or \"%s\'", errMsg1, errMsg2));
    }

    @Test
    void testOnResponseMessageWithWrongMessageID() throws Exception {
        setupSession();

        final var messageID = UUID.randomUUID().toString();
        final var resultFuture = sendRequest(messageID, true);

        communicator.onMessage(spySession, createSuccessResponseMessage(UUID.randomUUID().toString()));

        final var rpcError = verifyErrorRpcResult(resultFuture.get(), ErrorType.PROTOCOL, ErrorTag.BAD_ATTRIBUTE);
        assertFalse(Strings.isNullOrEmpty(rpcError.getMessage()), "RpcError message non-empty");

        final var errorInfo = rpcError.getInfo();
        assertNotNull("RpcError info is null", errorInfo);
        assertTrue(errorInfo.contains("actual-message-id"), "Error info contains \"actual-message-id\"");
        assertTrue(errorInfo.contains("expected-message-id"), "Error info contains \"expected-message-id\"");
    }

    @Test
    void testConcurrentMessageLimit() {
        setupSession();
        final var messageID = new ArrayList<String>();

        for (int i = 0; i < RPC_MESSAGE_LIMIT; i++) {
            messageID.add(UUID.randomUUID().toString());
            final var resultFuture = sendRequest(messageID.get(i), false);
            assertInstanceOf(UncancellableFuture.class, resultFuture, "ListenableFuture is null");
        }

        final var notWorkingMessageID = UUID.randomUUID().toString();
        var resultFuture = sendRequestWithoutMocking(notWorkingMessageID, false);
        assertFalse(resultFuture instanceof UncancellableFuture, "ListenableFuture is null");

        communicator.onMessage(spySession, createSuccessResponseMessage(messageID.get(0)));

        resultFuture = sendRequest(messageID.get(0), false);
        assertNotNull(resultFuture, "ListenableFuture is null");
    }

    @Test
    void testNoConcurrentMessageLimitWithCopyInstance() {
        setupSession();
        doReturn(mockChannelFuture).when(mockChannelFuture).addListener(any(GenericFutureListener.class));
        final var netconfMessage = createTestMessage(UUID.randomUUID().toString());
        doReturn(mockChannelFuture).when(spySession).sendMessage(netconfMessage);

        final var deviceCommunicator = communicator.copyWithoutRpcLimit();
        for (int i = 0; i < RPC_MESSAGE_LIMIT; i++) {
            final var resultFuture = deviceCommunicator.sendRequest(netconfMessage);
            assertInstanceOf(UncancellableFuture.class, resultFuture, "ListenableFuture is null");
        }
        var resultFuture = deviceCommunicator.sendRequest(netconfMessage);
        assertInstanceOf(UncancellableFuture.class, resultFuture, "ListenableFuture is null");
    }

    private static NetconfMessage createMultiErrorResponseMessage(final String messageID) throws Exception {
        // multiple rpc-errors which simulate actual response like in NETCONF-666
        final var xmlStr = "<nc:rpc-reply xmlns:nc=\"urn:ietf:params:xml:ns:netconf:base:1.0\" "
                + "xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\" "
                + "xmlns:junos=\"http://xml.juniper.net/junos/18.4R1/junos\" message-id=\"" + messageID + "\">"
                + "<nc:rpc-error>\n"
                + "<nc:error-type>protocol</nc:error-type>\n"
                + "<nc:error-tag>operation-failed</nc:error-tag>\n"
                + "<nc:error-severity>error</nc:error-severity>\n"
                + "<source-daemon>\n"
                + "dcd\n"
                + "</source-daemon>\n"
                + "<nc:error-message>\n"
                + "Number of member links configured, i.e [1], "
                + "for interface [ae0]is lesser than the required minimum [2].\n"
                + "</nc:error-message>\n"
                + "</nc:rpc-error>\n"
                + "<nc:rpc-error>\n"
                + "<nc:error-type>protocol</nc:error-type>\n"
                + "<nc:error-tag>operation-failed</nc:error-tag>\n"
                + "<nc:error-severity>error</nc:error-severity>\n"
                + "<nc:error-message>\n"
                + "configuration check-out failed\n"
                + "</nc:error-message>\n"
                + "</nc:rpc-error>\n"
                + "</nc:rpc-reply>";

        final var bis = new ByteArrayInputStream(xmlStr.getBytes());
        final var doc = UntrustedXML.newDocumentBuilder().parse(bis);
        return new NetconfMessage(doc);
    }

    private static NetconfMessage createErrorResponseMessage(final String messageID) throws Exception {
        final var xmlStr = "<rpc-reply xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\""
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

        final var bis = new ByteArrayInputStream(xmlStr.getBytes());
        final var doc = UntrustedXML.newDocumentBuilder().parse(bis);
        return new NetconfMessage(doc);
    }

    private static NetconfMessage createTestMessage(final String messageID) {
        final var doc = UntrustedXML.newDocumentBuilder().newDocument();
        final var element = doc.createElement("request");
        element.setAttribute("message-id", messageID);
        doc.appendChild(element);
        return new NetconfMessage(doc);
    }

    private static void verifyResponseMessage(final RpcResult<NetconfMessage> rpcResult, final String dataText) {
        assertNotNull(rpcResult, "RpcResult is null");
        assertTrue(rpcResult.isSuccessful(), "isSuccessful");
        final var messageResult = rpcResult.getResult();
        assertNotNull(messageResult, "getResult");
    }

    private static RpcError verifyErrorRpcResult(final RpcResult<NetconfMessage> rpcResult,
            final ErrorType expErrorType, final ErrorTag expErrorTag) {
        assertNotNull(rpcResult, "RpcResult is null");
        assertFalse(rpcResult.isSuccessful(), "isSuccessful");
        assertNotNull(rpcResult.getErrors(), "RpcResult errors is null");
        assertEquals(1, rpcResult.getErrors().size(), "Errors size");
        final var rpcError = rpcResult.getErrors().iterator().next();
        assertEquals(ErrorSeverity.ERROR, rpcError.getSeverity(), "getErrorSeverity");
        assertEquals(expErrorType, rpcError.getErrorType(), "getErrorType");
        assertEquals(expErrorTag, rpcError.getTag(), "getErrorTag");

        final var msg = rpcError.getMessage();
        assertNotNull("getMessage is null", msg);
        assertFalse(msg.isEmpty(), "getMessage is empty");
        assertFalse(CharMatcher.whitespace().matchesAllOf(msg), "getMessage is blank");
        return rpcError;
    }
}
