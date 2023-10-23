/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil.handler;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.Test;
import org.opendaylight.netconf.api.messages.FramingMechanism;

class FramingMechanismEncoderTest {
    @Test
    void testCreate() {
        assertInstanceOf(ChunkedFramingMechanismEncoder.class, FramingMechanismEncoder.of(FramingMechanism.CHUNK));
        assertInstanceOf(EOMFramingMechanismEncoder.class, FramingMechanismEncoder.of(FramingMechanism.EOM));
    }
}