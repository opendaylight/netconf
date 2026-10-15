/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class SSHClientCredentialsTest {
    @Test
    void credentialsRequireAuthentication() {
        final var ex = assertThrows(IllegalArgumentException.class,
            () -> new SSHClientCredentials("user", List.of(), List.of()));
        assertEquals("Neither passwords nor key pairs are defined", ex.getMessage());
    }
}
