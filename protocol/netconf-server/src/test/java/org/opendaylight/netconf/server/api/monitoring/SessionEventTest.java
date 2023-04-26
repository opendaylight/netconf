/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.api.monitoring;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class SessionEventTest {
    @Mock
    private NetconfManagementSession session;

    @Test
    public void test() {
        assertEquals(SessionEvent.Type.IN_RPC_FAIL, SessionEvent.inRpcFail(session).getType());
        assertEquals(SessionEvent.Type.IN_RPC_SUCCESS, SessionEvent.inRpcSuccess(session).getType());
        assertEquals(SessionEvent.Type.NOTIFICATION, SessionEvent.notification(session).getType());
        assertEquals(SessionEvent.Type.OUT_RPC_ERROR, SessionEvent.outRpcError(session).getType());
    }
}
