/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.api.operations;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.test.util.XmlFileLoader;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

public class AbstractNetconfOperationTest {
    private static class NetconfOperationImpl extends AbstractNetconfOperation {
        public boolean handleRun;

        NetconfOperationImpl(final SessionIdType sessionId) {
            super(sessionId);
        }

        @Override
        protected String getOperationName() {
            return null;
        }

        @Override
        protected Element handle(final Document document, final XmlElement message,
                final NetconfOperationChainedExecution subsequentOperation) throws DocumentedException {
            handleRun = true;
            try {
                return XmlUtil.readXmlToElement("<element/>");
            } catch (SAXException | IOException e) {
                throw DocumentedException.wrap(e);
            }
        }
    }

    private final NetconfOperationImpl netconfOperation = new NetconfOperationImpl(new SessionIdType(Uint32.ONE));
    private NetconfOperationChainedExecution operation;

    @Before
    public void setUp() throws Exception {
        operation = mock(NetconfOperationChainedExecution.class);
    }

    @Test
    public void testAbstractNetconfOperation() throws Exception {
        Document helloMessage = XmlFileLoader.xmlFileToDocument("netconfMessages/edit_config.xml");
        assertEquals(new SessionIdType(Uint32.ONE), netconfOperation.sessionId());
        assertNotNull(netconfOperation.canHandle(helloMessage));
        assertEquals(HandlingPriority.HANDLE_WITH_DEFAULT_PRIORITY, netconfOperation.getHandlingPriority());

        netconfOperation.handle(helloMessage, operation);
        assertTrue(netconfOperation.handleRun);
    }
}
