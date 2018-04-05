/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.impl.osgi;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.mapping.api.HandlingPriority;
import org.opendaylight.netconf.mapping.api.NetconfOperation;
import org.opendaylight.netconf.mapping.api.NetconfOperationChainedExecution;
import org.opendaylight.netconf.mapping.api.NetconfOperationService;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

public class NetconfOperationRouterImplTest {

    private static final String TEST_RPC = "<rpc message-id=\"101\" "
            + "xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\"><test/></rpc>\n";
    private static final String MAX_PRIORITY_REPLY = "<high/>";
    private static final String DEFAULT_PRIORITY_REPLY = "<default/>";

    private static Document TEST_RPC_DOC;

    @Mock
    private NetconfOperationService operationService;
    @Mock
    private NetconfOperationService operationService2;
    @Mock
    private NetconfOperation maxPrioMock;
    @Mock
    private NetconfOperation defaultPrioMock;

    private NetconfOperationRouterImpl operationRouter;
    private NetconfOperationRouterImpl emptyOperationRouter;

    @BeforeClass
    public static void suiteSetUp() throws IOException, SAXException {
        TEST_RPC_DOC = XmlUtil.readXmlToDocument(TEST_RPC);
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        doReturn(HandlingPriority.HANDLE_WITH_MAX_PRIORITY).when(maxPrioMock).canHandle(any(Document.class));
        doReturn(XmlUtil.readXmlToDocument(MAX_PRIORITY_REPLY)).when(maxPrioMock).handle(any(Document.class),
                any(NetconfOperationChainedExecution.class));

        doReturn(HandlingPriority.HANDLE_WITH_DEFAULT_PRIORITY).when(defaultPrioMock).canHandle(any(Document.class));
        doReturn(XmlUtil.readXmlToDocument(DEFAULT_PRIORITY_REPLY)).when(defaultPrioMock).handle(any(Document.class),
                any(NetconfOperationChainedExecution.class));

        final Set<NetconfOperation> operations = new HashSet<>();
        operations.add(maxPrioMock);
        operations.add(defaultPrioMock);
        doReturn(operations).when(operationService).getNetconfOperations();
        doNothing().when(operationService).close();

        operationRouter = new NetconfOperationRouterImpl(operationService, null, "session-1");
        doReturn(Collections.EMPTY_SET).when(operationService2).getNetconfOperations();
        emptyOperationRouter = new NetconfOperationRouterImpl(operationService2, null, "session-1");
    }

    @Test
    public void testOnNetconfMessage() throws Exception {
        final ArgumentCaptor<NetconfOperationChainedExecution> highPriorityChainEx =
                ArgumentCaptor.forClass(NetconfOperationChainedExecution.class);
        final ArgumentCaptor<NetconfOperationChainedExecution> defaultPriorityChainEx =
                ArgumentCaptor.forClass(NetconfOperationChainedExecution.class);

        final Document document = operationRouter.onNetconfMessage(TEST_RPC_DOC, null);

        //max priority message is first in chain
        verify(maxPrioMock).handle(any(Document.class), highPriorityChainEx.capture());
        final NetconfOperationChainedExecution chainedExecution = highPriorityChainEx.getValue();
        Assert.assertFalse(chainedExecution.isExecutionTermination());

        //execute next in chain
        final Document execute = chainedExecution.execute(XmlUtil.newDocument());
        Assert.assertEquals(DEFAULT_PRIORITY_REPLY, XmlUtil.toString(execute).trim());

        //default priority message is second and last
        verify(defaultPrioMock).handle(any(Document.class), defaultPriorityChainEx.capture());
        Assert.assertTrue(defaultPriorityChainEx.getValue().isExecutionTermination());

        Assert.assertEquals(MAX_PRIORITY_REPLY, XmlUtil.toString(document).trim());
    }

    @Test
    public void testOnNetconfMessageFail() throws Exception {
        try {
            emptyOperationRouter.onNetconfMessage(TEST_RPC_DOC, null);
            Assert.fail("Exception expected");
        } catch (final DocumentedException e) {
            Assert.assertEquals(e.getErrorTag(), DocumentedException.ErrorTag.OPERATION_NOT_SUPPORTED);
        }
    }

    @Test
    public void testClose() throws Exception {
        operationRouter.close();
        verify(operationService).close();
    }

}
