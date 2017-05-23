/*
 * Copyright (c) 2017 Frinx s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.impl.mapping.operations;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import com.google.common.collect.Sets;
import io.netty.channel.Channel;
import java.io.IOException;
import java.io.StringWriter;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.controller.config.util.xml.DocumentedException;
import org.opendaylight.netconf.api.NetconfTerminationReason;
import org.opendaylight.netconf.impl.NetconfServerSession;
import org.opendaylight.netconf.impl.NetconfServerSessionListener;
import org.opendaylight.netconf.impl.osgi.NetconfOperationRouterImpl;
import org.opendaylight.netconf.impl.osgi.NetconfSessionDatastore;
import org.opendaylight.netconf.mapping.api.NetconfOperationChainedExecution;
import org.opendaylight.netconf.mapping.api.NetconfOperationService;
import org.opendaylight.netconf.util.test.XmlFileLoader;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

public class DefaultKillSessionTest {
    private String SESSION_TEST_ID = "1";
    private DefaultKillSession killSession;

    @Before
    public void setUp() throws Exception {
        final NetconfOperationService netconfOperationService = mock(NetconfOperationService.class);
        doReturn(Sets.newHashSet()).when(netconfOperationService).getNetconfOperations();
        doNothing().when(netconfOperationService).close(); //this is called in kill-session

        final NetconfServerSessionListener sessionListener = mock(NetconfServerSessionListener.class);
        doNothing().when(sessionListener).onSessionTerminated(any(NetconfServerSession.class), any(NetconfTerminationReason.class));
        final Channel channel = mock(Channel.class);
        doReturn("").when(channel).toString();
        doReturn(null).when(channel).close(); //this is called in kill-session

        NetconfServerSession serverSession1 = new NetconfServerSession(sessionListener, channel, 1, null);
        NetconfServerSession serverSession2 = new NetconfServerSession(sessionListener, channel, 2, null);

        NetconfSessionDatastore netconfSessionDatastore = new NetconfSessionDatastore();
        netconfSessionDatastore.put(serverSession1, new NetconfOperationRouterImpl(netconfOperationService, netconfSessionDatastore, "1"));
        netconfSessionDatastore.put(serverSession2, new NetconfOperationRouterImpl(netconfOperationService, netconfSessionDatastore, "2"));

        killSession = new DefaultKillSession(SESSION_TEST_ID,netconfSessionDatastore);


    }

    @Test
    public void testKillSession() throws Exception {
        verifyResponse(killSession("messages/mapping/kill_session.xml"));
        try {
            killSession("messages/mapping/kill_session_invalid.xml");
            fail("Should have failed");
        } catch (DocumentedException e) {
            assertTrue(e.getErrorSeverity() == DocumentedException.ErrorSeverity.error);
            assertTrue(e.getErrorTag() == DocumentedException.ErrorTag.operation_failed);
            assertTrue(e.getErrorType() == DocumentedException.ErrorType.application);
        }

        try {
            killSession("messages/mapping/suicide.xml");
            fail("Should have failed");
        } catch (DocumentedException e) {
            assertTrue(e.getErrorSeverity() == DocumentedException.ErrorSeverity.error);
            assertTrue(e.getErrorTag() == DocumentedException.ErrorTag.operation_failed);
            assertTrue(e.getErrorType() == DocumentedException.ErrorType.application);
        }
    }

    private Document killSession(String filename) throws ParserConfigurationException, SAXException, IOException, DocumentedException {
        final Document request = XmlFileLoader.xmlFileToDocument(filename);
        final Document response = killSession.handle(request, NetconfOperationChainedExecution.EXECUTION_TERMINATION_POINT);

        return response;
    }

    private void verifyResponse(Document response) throws IOException, TransformerException {
        String xml = printDocument(response);
        assertTrue(xml.contains("<ok/>"));
    }

    private static String printDocument(Document doc) throws IOException, TransformerException {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.METHOD, "xml");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc),
                new StreamResult(writer));
        return writer.getBuffer().toString();
    }


}
