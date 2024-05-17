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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
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
import javax.xml.parsers.ParserConfigurationException;
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
import org.w3c.dom.Document;
import org.w3c.dom.Element;

@ExtendWith(MockitoExtension.class)
public class NetconfDeviceCommunicatorTest {
    private static final SessionIdType SESSION_ID = new SessionIdType(Uint32.ONE);

    @Mock
    private RemoteDevice<NetconfDeviceCommunicator> mockDevice;

    private NetconfClientSession spySession;
    private NetconfDeviceCommunicator communicator;

    @BeforeEach
    public void setUp() throws Exception {
        communicator = new NetconfDeviceCommunicator(
                new RemoteDeviceId("test", InetSocketAddress.createUnresolved("localhost", 22)), mockDevice, 10);
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
        Document doc = UntrustedXML.newDocumentBuilder().newDocument();
        Element element = doc.createElement("request");
        element.setAttribute("message-id", messageID);
        doc.appendChild(element);
        NetconfMessage message = new NetconfMessage(doc);

        ChannelFuture mockChannelFuture = mock(ChannelFuture.class);
        doReturn(mockChannelFuture).when(mockChannelFuture)
                .addListener(any(GenericFutureListener.class));
        doReturn(mockChannelFuture).when(spySession).sendMessage(same(message));

        ListenableFuture<RpcResult<NetconfMessage>> resultFuture =
                communicator.sendRequest(message, QName.create("", "mockRpc"));
        if (doLastTest) {
            assertNotNull(resultFuture, "ListenableFuture is null");
        }
        return resultFuture;
    }

    @SuppressWarnings("unchecked")
    private ListenableFuture<RpcResult<NetconfMessage>> sendRequestWithoutMocking(final String messageID,
        final boolean doLastTest) {
        Document doc = UntrustedXML.newDocumentBuilder().newDocument();
        Element element = doc.createElement("request");
        element.setAttribute("message-id", messageID);
        doc.appendChild(element);
        NetconfMessage message = new NetconfMessage(doc);

        ListenableFuture<RpcResult<NetconfMessage>> resultFuture =
            communicator.sendRequest(message, QName.create("", "mockRpc"));
        if (doLastTest) {
            assertNotNull(resultFuture, "ListenableFuture is null");
        }
        return resultFuture;
    }

    @Test
    public void testOnSessionUp() {
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

    @SuppressWarnings("unchecked")
    @Test
    public void testOnSessionDown() {
        assertTimeout(Duration.ofMillis(5000), () -> {
            setupSession();

            ListenableFuture<RpcResult<NetconfMessage>> resultFuture1 = sendRequest();
            final ListenableFuture<RpcResult<NetconfMessage>> resultFuture2 = sendRequest();

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
    public void testOnSessionTerminated() throws Exception {
        setupSession();

        ListenableFuture<RpcResult<NetconfMessage>> resultFuture = sendRequest();

        doNothing().when(mockDevice).onRemoteSessionDown();

        String reasonText = "testing terminate";
        NetconfTerminationReason reason = new NetconfTerminationReason(reasonText);
        communicator.onSessionTerminated(spySession, reason);

        RpcError rpcError = verifyErrorRpcResult(resultFuture.get(), ErrorType.TRANSPORT, ErrorTag.OPERATION_FAILED);
        assertEquals(reasonText, rpcError.getMessage(), "RpcError message");

        verify(mockDevice).onRemoteSessionDown();
    }

    @Test
    public void testClose() {
        communicator.close();
        verify(mockDevice, never()).onRemoteSessionDown();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSendRequest() throws Exception {
        setupSession();

        NetconfMessage message = new NetconfMessage(UntrustedXML.newDocumentBuilder().newDocument());
        QName rpc = QName.create("", "mockRpc");

        final var futureListener = ArgumentCaptor.forClass(GenericFutureListener.class);

        ChannelFuture mockChannelFuture = mock(ChannelFuture.class);
        doReturn(mockChannelFuture).when(mockChannelFuture).addListener(futureListener.capture());
        doReturn(mockChannelFuture).when(spySession).sendMessage(same(message));

        ListenableFuture<RpcResult<NetconfMessage>> resultFuture = communicator.sendRequest(message, rpc);

        verify(spySession).sendMessage(same(message));

        assertNotNull(resultFuture, "ListenableFuture is null");

        verify(mockChannelFuture).addListener(futureListener.capture());
        Future<Void> operationFuture = mock(Future.class);
        doReturn(null).when(operationFuture).cause();
        futureListener.getValue().operationComplete(operationFuture);

        // verify it is not cancelled nor has an error set
        assertFalse(resultFuture.isDone());
    }

    @Test
    public void testSendRequestWithNoSession() throws Exception {
        NetconfMessage message = new NetconfMessage(UntrustedXML.newDocumentBuilder().newDocument());
        QName rpc = QName.create("", "mockRpc");

        ListenableFuture<RpcResult<NetconfMessage>> resultFuture = communicator.sendRequest(message, rpc);

        assertNotNull(resultFuture, "ListenableFuture is null");

        // Should have an immediate result
        RpcResult<NetconfMessage> rpcResult = Futures.getDone(resultFuture);

        verifyErrorRpcResult(rpcResult, ErrorType.TRANSPORT, ErrorTag.OPERATION_FAILED);
    }

    private static NetconfMessage createSuccessResponseMessage(final String messageID)
            throws ParserConfigurationException {
        Document doc = UntrustedXML.newDocumentBuilder().newDocument();
        Element rpcReply = doc.createElementNS(NamespaceURN.BASE, RpcReplyMessage.ELEMENT_NAME);
        rpcReply.setAttribute("message-id", messageID);
        Element element = doc.createElementNS("ns", "data");
        element.setTextContent(messageID);
        rpcReply.appendChild(element);
        doc.appendChild(rpcReply);

        return new NetconfMessage(doc);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSendRequestWithWithSendFailure() throws Exception {
        setupSession();

        NetconfMessage message = new NetconfMessage(UntrustedXML.newDocumentBuilder().newDocument());
        QName rpc = QName.create("", "mockRpc");

        final var futureListener = ArgumentCaptor.forClass(GenericFutureListener.class);

        ChannelFuture mockChannelFuture = mock(ChannelFuture.class);
        doReturn(mockChannelFuture).when(mockChannelFuture).addListener(futureListener.capture());
        doReturn(mockChannelFuture).when(spySession).sendMessage(same(message));

        ListenableFuture<RpcResult<NetconfMessage>> resultFuture = communicator.sendRequest(message, rpc);

        assertNotNull(resultFuture, "ListenableFuture is null");

        verify(mockChannelFuture).addListener(futureListener.capture());

        Future<Void> operationFuture = mock(Future.class);
        doReturn(new Exception("mock error")).when(operationFuture).cause();
        futureListener.getValue().operationComplete(operationFuture);

        // Should have an immediate result
        RpcResult<NetconfMessage> rpcResult = Futures.getDone(resultFuture);

        RpcError rpcError = verifyErrorRpcResult(rpcResult, ErrorType.TRANSPORT, ErrorTag.OPERATION_FAILED);
        assertEquals(true, rpcError.getMessage().contains("mock error"),
            "RpcError message contains \"mock error\"");
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
        communicator.onMessage(spySession, createSuccessResponseMessage(messageID3));

        verifyResponseMessage(resultFuture3.get(), messageID3);
    }

    @Test
    public void testOnSuccessfulResponseMessage() throws Exception {
        setupSession();

        String messageID1 = UUID.randomUUID().toString();
        ListenableFuture<RpcResult<NetconfMessage>> resultFuture1 = sendRequest(messageID1, true);

        String messageID2 = UUID.randomUUID().toString();
        final ListenableFuture<RpcResult<NetconfMessage>> resultFuture2 = sendRequest(messageID2, true);

        communicator.onMessage(spySession, createSuccessResponseMessage(messageID1));
        communicator.onMessage(spySession, createSuccessResponseMessage(messageID2));

        verifyResponseMessage(resultFuture1.get(), messageID1);
        verifyResponseMessage(resultFuture2.get(), messageID2);
    }

    @Test
    public void testOnResponseMessageWithError() throws Exception {
        setupSession();

        String messageID = UUID.randomUUID().toString();
        ListenableFuture<RpcResult<NetconfMessage>> resultFuture = sendRequest(messageID, true);

        communicator.onMessage(spySession, createErrorResponseMessage(messageID));

        RpcError rpcError = verifyErrorRpcResult(resultFuture.get(), ErrorType.RPC, ErrorTag.MISSING_ATTRIBUTE);
        assertEquals("Missing attribute", rpcError.getMessage(), "RpcError message");

        String errorInfo = rpcError.getInfo();
        assertNotNull("RpcError info is null", errorInfo);
        assertTrue(errorInfo.contains("<bad-attribute>foo</bad-attribute>"), "Error info contains \"foo\"");
        assertTrue(errorInfo.contains("<bad-element>bar</bad-element>"), "Error info contains \"bar\"");
    }

    @Test
    public void testOnResponseMessageWithMultipleErrors() throws Exception {
        setupSession();

        String messageID = UUID.randomUUID().toString();
        ListenableFuture<RpcResult<NetconfMessage>> resultFuture = sendRequest(messageID, true);

        communicator.onMessage(spySession, createMultiErrorResponseMessage(messageID));

        RpcError rpcError = verifyErrorRpcResult(resultFuture.get(), ErrorType.PROTOCOL, ErrorTag.OPERATION_FAILED);

        String errorInfo = rpcError.getInfo();
        assertNotNull("RpcError info is null", errorInfo);

        String errorInfoMessages = rpcError.getInfo();
        String errMsg1 = "Number of member links configured, i.e [1], "
                + "for interface [ae0]is lesser than the required minimum [2].";
        String errMsg2 = "configuration check-out failed";
        assertTrue(errorInfoMessages.contains(errMsg1) && errorInfoMessages.contains(errMsg2),
            String.format("Error info contains \"%s\" or \"%s\'", errMsg1, errMsg2));
    }

    @Test
    public void testOnResponseMessageWithWrongMessageID() throws Exception {
        setupSession();

        String messageID = UUID.randomUUID().toString();
        ListenableFuture<RpcResult<NetconfMessage>> resultFuture = sendRequest(messageID, true);

        communicator.onMessage(spySession, createSuccessResponseMessage(UUID.randomUUID().toString()));

        RpcError rpcError = verifyErrorRpcResult(resultFuture.get(), ErrorType.PROTOCOL, ErrorTag.BAD_ATTRIBUTE);
        assertFalse(Strings.isNullOrEmpty(rpcError.getMessage()), "RpcError message non-empty");

        String errorInfo = rpcError.getInfo();
        assertNotNull("RpcError info is null", errorInfo);
        assertTrue(errorInfo.contains("actual-message-id"), "Error info contains \"actual-message-id\"");
        assertTrue(errorInfo.contains("expected-message-id"), "Error info contains \"expected-message-id\"");
    }

    @Test
    public void testConcurrentMessageLimit() throws Exception {
        setupSession();
        ArrayList<String> messageID = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            messageID.add(UUID.randomUUID().toString());
            ListenableFuture<RpcResult<NetconfMessage>> resultFuture = sendRequest(messageID.get(i), false);
            assertEquals(true, resultFuture instanceof UncancellableFuture, "ListenableFuture is null");
        }

        final String notWorkingMessageID = UUID.randomUUID().toString();
        ListenableFuture<RpcResult<NetconfMessage>> resultFuture = sendRequestWithoutMocking(notWorkingMessageID,
            false);
        assertEquals(false, resultFuture instanceof UncancellableFuture, "ListenableFuture is null");

        communicator.onMessage(spySession, createSuccessResponseMessage(messageID.get(0)));

        resultFuture = sendRequest(messageID.get(0), false);
        assertNotNull(resultFuture, "ListenableFuture is null");
    }

    private static NetconfMessage createMultiErrorResponseMessage(final String messageID) throws Exception {
        // multiple rpc-errors which simulate actual response like in NETCONF-666
        String xmlStr = "<nc:rpc-reply xmlns:nc=\"urn:ietf:params:xml:ns:netconf:base:1.0\" "
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

        ByteArrayInputStream bis = new ByteArrayInputStream(xmlStr.getBytes());
        Document doc = UntrustedXML.newDocumentBuilder().parse(bis);
        return new NetconfMessage(doc);
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
        assertNotNull(rpcResult, "RpcResult is null");
        assertTrue(rpcResult.isSuccessful(), "isSuccessful");
        NetconfMessage messageResult = rpcResult.getResult();
        assertNotNull(messageResult, "getResult");
    }

    private static RpcError verifyErrorRpcResult(final RpcResult<NetconfMessage> rpcResult,
                                                 final ErrorType expErrorType, final ErrorTag expErrorTag) {
        assertNotNull(rpcResult, "RpcResult is null");
        assertFalse(rpcResult.isSuccessful(), "isSuccessful");
        assertNotNull(rpcResult.getErrors(), "RpcResult errors is null");
        assertEquals(1, rpcResult.getErrors().size(), "Errors size");
        RpcError rpcError = rpcResult.getErrors().iterator().next();
        assertEquals(ErrorSeverity.ERROR, rpcError.getSeverity(), "getErrorSeverity");
        assertEquals(expErrorType, rpcError.getErrorType(), "getErrorType");
        assertEquals(expErrorTag, rpcError.getTag(), "getErrorTag");

        final String msg = rpcError.getMessage();
        assertNotNull("getMessage is null", msg);
        assertFalse(msg.isEmpty(), "getMessage is empty");
        assertFalse(CharMatcher.whitespace().matchesAllOf(msg), "getMessage is blank");
        return rpcError;
    }
}
