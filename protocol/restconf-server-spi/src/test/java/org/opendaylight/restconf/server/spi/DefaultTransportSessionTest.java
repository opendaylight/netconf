/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.restconf.server.api.TransportSession.Description;
import org.opendaylight.yangtools.concepts.Registration;

@ExtendWith(MockitoExtension.class)
class DefaultTransportSessionTest {
    @Mock
    private Registration reg1;
    @Mock
    private Registration reg2;
    @Mock
    private Description description;

    private DefaultTransportSession session;

    @BeforeEach
    void beforeEach() {
        session = new DefaultTransportSession(description);
    }

    @Test
    void sceneryWorks() {
        assertSame(description, session.description());
        session.close();
        doReturn("very friendly").when(description).toFriendlyString();
        assertEquals("DefaultTransportSession{transport=very friendly, closed=true}", session.toString());
    }

    @Test
    void registerWhileOpenAndClose() {
        session.registerResource(reg1);
        verifyNoInteractions(reg1);
        session.registerResource(reg2);
        verifyNoInteractions(reg2);
        doNothing().when(reg1).close();
        doNothing().when(reg2).close();
        session.close();
    }

    @Test
    void registerRejectNull() {
        assertThrows(NullPointerException.class, () -> session.registerResource(null));
    }

    @Test
    void registerAfterClose() {
        session.close();
        doNothing().when(reg1).close();
        session.registerResource(reg1);
    }
}
