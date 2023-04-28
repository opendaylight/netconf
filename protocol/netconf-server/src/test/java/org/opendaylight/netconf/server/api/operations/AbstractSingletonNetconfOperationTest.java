/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.api.operations;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class AbstractSingletonNetconfOperationTest {
    private static final class SingletonNCOperationImpl extends AbstractSingletonNetconfOperation {
        SingletonNCOperationImpl(final SessionIdType sessionId) {
            super(sessionId);
        }

        @Override
        protected Element handleWithNoSubsequentOperations(final Document document,
                final XmlElement operationElement) throws DocumentedException {
            return null;
        }

        @Override
        protected String getOperationName() {
            return null;
        }
    }

    @Test
    public void testAbstractSingletonNetconfOperation() throws Exception {
        SingletonNCOperationImpl operation = new SingletonNCOperationImpl(new SessionIdType(Uint32.TEN));
        assertEquals(HandlingPriority.HANDLE_WITH_MAX_PRIORITY, operation.getHandlingPriority());
    }
}
