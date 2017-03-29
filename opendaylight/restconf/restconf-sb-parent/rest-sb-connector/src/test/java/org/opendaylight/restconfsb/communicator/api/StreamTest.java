/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.communicator.api;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;

import org.junit.Before;
import org.junit.Test;
import org.opendaylight.restconfsb.communicator.api.stream.Stream;

public class StreamTest {

    private static final String NAME = "SNMP";

    Stream stream;

    @Before
    public void setUp() {
        stream = new Stream(NAME, "http://localhost:8181/restconf/data/ietf-restconf-monitoring:restconf-state/streams/stream/", false);
    }

    @Test
    public void testLocation() {
        final String location = stream.getLocation();
        assertEquals("http://localhost:8181/restconf/data/ietf-restconf-monitoring:restconf-state/streams/stream/", location);
    }

    @Test
    public void testName() {
        final String name = stream.getName();
        assertEquals(NAME, name);
    }

    @Test
    public void testReplaySupport() {
        final Boolean replaySupport = stream.isReplaySupport();
        assertFalse(replaySupport);
    }

}
