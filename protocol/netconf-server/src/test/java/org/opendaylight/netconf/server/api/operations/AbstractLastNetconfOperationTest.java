/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.api.operations;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import org.junit.Before;
import org.junit.Test;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class AbstractLastNetconfOperationTest {
    private static final class LastNetconfOperationImplTest extends AbstractLastNetconfOperation {
        boolean handleWithNoSubsequentOperationsRun;

        protected LastNetconfOperationImplTest(final SessionIdType sessionId) {
            super(sessionId);
            handleWithNoSubsequentOperationsRun = false;
        }

        @Override
        protected Element handleWithNoSubsequentOperations(final Document document, final XmlElement operationElement)
                throws DocumentedException {
            handleWithNoSubsequentOperationsRun = true;
            return null;
        }

        @Override
        protected String getOperationName() {
            return "";
        }
    }

    LastNetconfOperationImplTest netconfOperation;

    @Before
    public void setUp() throws Exception {
        netconfOperation = new LastNetconfOperationImplTest(new SessionIdType(Uint32.ONE));
    }

    @Test
    public void testNetconfOperation() throws Exception {
        netconfOperation.handleWithNoSubsequentOperations(null, null);
        assertTrue(netconfOperation.handleWithNoSubsequentOperationsRun);
        assertEquals(HandlingPriority.HANDLE_WITH_DEFAULT_PRIORITY, netconfOperation.getHandlingPriority());
    }

    @Test(expected = DocumentedException.class)
    public void testHandle() throws Exception {
        NetconfOperationChainedExecution operation = mock(NetconfOperationChainedExecution.class);
        doReturn("").when(operation).toString();

        doReturn(false).when(operation).isExecutionTermination();
        netconfOperation.handle(null, null, operation);
    }
}
