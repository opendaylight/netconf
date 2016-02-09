/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.impl.osgi;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.opendaylight.controller.config.util.xml.DocumentedException;
import org.opendaylight.controller.config.util.xml.XmlUtil;
import org.opendaylight.netconf.mapping.api.HandlingPriority;
import org.opendaylight.netconf.mapping.api.NetconfOperation;
import org.opendaylight.netconf.mapping.api.NetconfOperationChainedExecution;
import org.opendaylight.netconf.mapping.api.NetconfOperationService;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

public class NetconfOperationRouterImplTest {

    public static final String TEST_RPC = "<rpc message-id=\"101\" xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\"><test/></rpc>\n";
    public static final Document TEST_RPC_DOC;
    public static final String MAX_PRIORITY_REPLY = "<high/>";
    public static final String DEFAULT_PRIORITY_REPLY = "<default/>";

    static {
        try {
            TEST_RPC_DOC = XmlUtil.readXmlToDocument(TEST_RPC);
        } catch (SAXException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private NetconfOperationRouterImpl operationRouter;
    private NetconfOperationRouterImpl emptyOperationRouter;
    private NetconfOperationService operationService;
    private NetconfOperation defaultPriorityOp;
    private NetconfOperation highPriorityOp;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Before
    public void setUp() throws Exception {
        Set<NetconfOperation> operations = new HashSet<>();
        defaultPriorityOp = getDefaultPriorityOp();
        operations.add(defaultPriorityOp);
        highPriorityOp = getHighPriorityOp();
        operations.add(highPriorityOp);
        operationService = getMock(NetconfOperationService.class);
        when(operationService.getNetconfOperations()).thenReturn(operations);

        NetconfOperationService operationService2 = getMock(NetconfOperationService.class);
        when(operationService2.getNetconfOperations()).thenReturn(new HashSet<NetconfOperation>());
        operationRouter = new NetconfOperationRouterImpl(operationService, null, "session-1");
        emptyOperationRouter = new NetconfOperationRouterImpl(operationService2, null, "session-1");

    }

    @Test
    public void testOnNetconfMessage() throws Exception {
        ArgumentCaptor<NetconfOperationChainedExecution> highPriorityChainEx = ArgumentCaptor.forClass(NetconfOperationChainedExecution.class);
        ArgumentCaptor<NetconfOperationChainedExecution> defaultPriorityChainEx = ArgumentCaptor.forClass(NetconfOperationChainedExecution.class);

        final Document document = operationRouter.onNetconfMessage(TEST_RPC_DOC, null);

        //max priority message is first in chain
        verify(highPriorityOp).handle(any(Document.class), highPriorityChainEx.capture());
        final NetconfOperationChainedExecution chainedExecution = highPriorityChainEx.getValue();
        Assert.assertFalse(chainedExecution.isExecutionTermination());

        //execute next in chain
        final Document execute = chainedExecution.execute(XmlUtil.newDocument());
        Assert.assertEquals(DEFAULT_PRIORITY_REPLY, XmlUtil.toString(execute).trim());

        //default priority message is second and last
        verify(defaultPriorityOp).handle(any(Document.class), defaultPriorityChainEx.capture());
        Assert.assertTrue(defaultPriorityChainEx.getValue().isExecutionTermination());

        Assert.assertEquals(MAX_PRIORITY_REPLY, XmlUtil.toString(document).trim());
    }

    @Test
    public void testOnNetconfMessageFail() throws Exception {
        expectedException.expect(DocumentedException.class);
        expectedException.expectMessage("Unable to handle rpc <rpc xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\" message-id=\"101\">");
        expectedException.expectMessage("<test/>");

        emptyOperationRouter.onNetconfMessage(TEST_RPC_DOC, null);

    }

    @Test
    public void testClose() throws Exception {
        operationRouter.close();
        verify(operationService).close();
    }


    private <T> T getMock(Class<T> cls) {
        return mock(cls, new Answer() {
            @Override
            public T answer(InvocationOnMock invocation) throws Throwable {
                return null;
            }
        });
    }

    private NetconfOperation getHighPriorityOp() {
        NetconfOperation op = getMock(NetconfOperation.class);
        try {
            when(op.canHandle((Document) anyObject())).thenReturn(HandlingPriority.HANDLE_WITH_MAX_PRIORITY);
            when(op.handle(any(Document.class), any(NetconfOperationChainedExecution.class))).thenReturn(XmlUtil.readXmlToDocument(MAX_PRIORITY_REPLY));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return op;
    }

    private NetconfOperation getDefaultPriorityOp() {
        NetconfOperation op = getMock(NetconfOperation.class);
        try {
            when(op.canHandle((Document) anyObject())).thenReturn(HandlingPriority.HANDLE_WITH_DEFAULT_PRIORITY);
            when(op.handle(any(Document.class), any(NetconfOperationChainedExecution.class))).thenReturn(XmlUtil.readXmlToDocument(DEFAULT_PRIORITY_REPLY));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return op;
    }
}