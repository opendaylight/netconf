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
import org.opendaylight.netconf.util.test.XmlFileLoader;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

public class AbstractNetconfOperationTest {
    private static class NetconfOperationImpl extends AbstractNetconfOperation {
        public boolean handleRun;

        NetconfOperationImpl(final String netconfSessionIdForReporting) {
            super(netconfSessionIdForReporting);
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

    private final NetconfOperationImpl netconfOperation = new NetconfOperationImpl("str");
    private NetconfOperationChainedExecution operation;

    @Before
    public void setUp() throws Exception {
        operation = mock(NetconfOperationChainedExecution.class);
    }

    @Test
    public void testAbstractNetconfOperation() throws Exception {
        Document helloMessage = XmlFileLoader.xmlFileToDocument("netconfMessages/edit_config.xml");
        assertEquals(netconfOperation.getNetconfSessionIdForReporting(), "str");
        assertNotNull(netconfOperation.canHandle(helloMessage));
        assertEquals(netconfOperation.getHandlingPriority(), HandlingPriority.HANDLE_WITH_DEFAULT_PRIORITY);

        netconfOperation.handle(helloMessage, operation);
        assertTrue(netconfOperation.handleRun);
    }
}
