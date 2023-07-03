/*
 * Copyright (c) 2014 Brocade Communications Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;

import com.google.common.base.CharMatcher;
import com.google.common.base.Strings;
import com.google.common.util.concurrent.ListenableFuture;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import java.io.ByteArrayInputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.xml.parsers.ParserConfigurationException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockMakers;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.netconf.api.CapabilityURN;
import org.opendaylight.netconf.api.NamespaceURN;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.api.NetconfTerminationReason;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.client.NetconfClientSession;
import org.opendaylight.netconf.client.NetconfClientSessionListener;
import org.opendaylight.netconf.client.mdsal.api.NetconfSessionPreferences;
import org.opendaylight.netconf.client.mdsal.api.RemoteDevice;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.impl.NetconfMessageTransformUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.opendaylight.yangtools.util.xml.UntrustedXML;
import org.opendaylight.yangtools.yang.common.ErrorSeverity;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class NetconfDeviceCommunicatorTest {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfDeviceCommunicatorTest.class);
    private static final SessionIdType SESSION_ID = new SessionIdType(Uint32.ONE);

    @Mock
    RemoteDevice<NetconfDeviceCommunicator> mockDevice;

    private NetconfClientSession spySession;
    private NetconfDeviceCommunicator communicator;

    @Before
    public void setUp() throws Exception {
        communicator = new NetconfDeviceCommunicator(
                new RemoteDeviceId("test", InetSocketAddress.createUnresolved("localhost", 22)), mockDevice, 10);
        // FIXME: spy() except we override the MockMaker in use
        spySession = mock(NetconfClientSession.class, withSettings()
            .spiedInstance(new NetconfClientSession(mock(NetconfClientSessionListener.class), mock(Channel.class),
                SESSION_ID, Set.of()))
            .defaultAnswer(CALLS_REAL_METHODS)
            .mockMaker(MockMakers.SUBCLASS));
    }

    void setupSession() {
        doNothing().when(mockDevice).onRemoteSessionUp(any(NetconfSessionPreferences.class),
                any(NetconfDeviceCommunicator.class));
        communicator.onSessionUp(spySession);
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
        doReturn(mockChannelFuture).when(spySession).sendMessage(same(message));

        ListenableFuture<RpcResult<NetconfMessage>> resultFuture =
                communicator.sendRequest(message, QName.create("", "mockRpc"));
        if (doLastTest) {
            assertNotNull("ListenableFuture is null", resultFuture);
        }
        return resultFuture;
    }

    @Test
    public void testOnSessionUp() {
        final var testCapability = "urn:opendaylight:params:xml:ns:test?module=test-module&revision=2014-06-02";
        final var serverCapabilities = Set.of(
            CapabilityURN.ROLLBACK_ON_ERROR,
            NetconfMessageTransformUtil.IETF_NETCONF_MONITORING.getNamespace().toString(),
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
    @Test(timeout = 5000)
    public void testOnSessionDown() throws Exception {
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
        doReturn(mockChannelFuture).when(spySession).sendMessage(same(message));

        ListenableFuture<RpcResult<NetconfMessage>> resultFuture = communicator.sendRequest(message, rpc);

        verify(spySession).sendMessage(same(message));

        assertNotNull("ListenableFuture is null", resultFuture);

        verify(mockChannelFuture).addListener(futureListener.capture());
        Future<Void> operationFuture = mock(Future.class);
        doReturn(true).when(operationFuture).isSuccess();
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

        verifyErrorRpcResult(rpcResult, ErrorType.TRANSPORT, ErrorTag.OPERATION_FAILED);
    }

    private static NetconfMessage createSuccessResponseMessage(final String messageID)
            throws ParserConfigurationException {
        Document doc = UntrustedXML.newDocumentBuilder().newDocument();
        Element rpcReply = doc.createElementNS(NamespaceURN.BASE, XmlNetconfConstants.RPC_REPLY_KEY);
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
        doReturn(mockChannelFuture).when(spySession).sendMessage(same(message));

        ListenableFuture<RpcResult<NetconfMessage>> resultFuture = communicator.sendRequest(message, rpc);

        assertNotNull("ListenableFuture is null", resultFuture);

        verify(mockChannelFuture).addListener(futureListener.capture());

        Future<Void> operationFuture = mock(Future.class);
        doReturn(false).when(operationFuture).isSuccess();
        doReturn(new Exception("mock error")).when(operationFuture).cause();
        futureListener.getValue().operationComplete(operationFuture);

        // Should have an immediate result
        RpcResult<NetconfMessage> rpcResult = resultFuture.get(3, TimeUnit.MILLISECONDS);

        RpcError rpcError = verifyErrorRpcResult(rpcResult, ErrorType.TRANSPORT, ErrorTag.OPERATION_FAILED);
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
        assertEquals("RpcError message", "Missing attribute", rpcError.getMessage());

        String errorInfo = rpcError.getInfo();
        assertNotNull("RpcError info is null", errorInfo);
        assertTrue("Error info contains \"foo\"", errorInfo.contains("<bad-attribute>foo</bad-attribute>"));
        assertTrue("Error info contains \"bar\"", errorInfo.contains("<bad-element>bar</bad-element>"));
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
        assertTrue(String.format("Error info contains \"%s\" or \"%s\'", errMsg1, errMsg2),
                errorInfoMessages.contains(errMsg1) && errorInfoMessages.contains(errMsg2));
    }

    @Test
    public void testOnResponseMessageWithWrongMessageID() throws Exception {
        setupSession();

        String messageID = UUID.randomUUID().toString();
        ListenableFuture<RpcResult<NetconfMessage>> resultFuture = sendRequest(messageID, true);

        communicator.onMessage(spySession, createSuccessResponseMessage(UUID.randomUUID().toString()));

        RpcError rpcError = verifyErrorRpcResult(resultFuture.get(), ErrorType.PROTOCOL, ErrorTag.BAD_ATTRIBUTE);
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

        communicator.onMessage(spySession, createSuccessResponseMessage(messageID.get(0)));

        resultFuture = sendRequest(messageID.get(0), false);
        assertNotNull("ListenableFuture is null", resultFuture);
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
                                                 final ErrorType expErrorType, final ErrorTag expErrorTag) {
        assertNotNull("RpcResult is null", rpcResult);
        assertFalse("isSuccessful", rpcResult.isSuccessful());
        assertNotNull("RpcResult errors is null", rpcResult.getErrors());
        assertEquals("Errors size", 1, rpcResult.getErrors().size());
        RpcError rpcError = rpcResult.getErrors().iterator().next();
        assertEquals("getErrorSeverity", ErrorSeverity.ERROR, rpcError.getSeverity());
        assertEquals("getErrorType", expErrorType, rpcError.getErrorType());
        assertEquals("getErrorTag", expErrorTag, rpcError.getTag());

        final String msg = rpcError.getMessage();
        assertNotNull("getMessage is null", msg);
        assertFalse("getMessage is empty", msg.isEmpty());
        assertFalse("getMessage is blank", CharMatcher.whitespace().matchesAllOf(msg));
        return rpcError;
    }
}
