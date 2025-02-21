/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api.testlib;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TestInstanceIdentifierTest extends AbstractInstanceIdentifierTest {
    @Test
    void iidTestModelContextModules() {
        assertEquals(15, IID_SCHEMA.getModules().size());
    }
}
