/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api.testlib;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.opendaylight.yangtools.yang.common.QNameModule;

class TestJukeBoxTest extends AbstractJukeboxTest {
    @Test
    void jukeboxModelContextModules() {
        assertEquals(Set.of(
            QNameModule.ofRevision("urn:ietf:params:xml:ns:yang:ietf-inet-types", "2013-07-15"),
            QNameModule.ofRevision("urn:ietf:params:xml:ns:yang:ietf-yang-types", "2013-07-15"),
            QNameModule.ofRevision("urn:ietf:params:xml:ns:yang:ietf-restconf-monitoring", "2017-01-26"),
            QNameModule.ofRevision("http://example.com/ns/example-jukebox", "2015-04-04"),
            QNameModule.ofRevision("http://example.com/ns/augmented-jukebox", "2016-05-05")),
            JUKEBOX_SCHEMA.getModuleStatements().keySet());
    }
}
