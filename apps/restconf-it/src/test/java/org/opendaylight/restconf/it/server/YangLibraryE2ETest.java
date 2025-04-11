/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.it.server;

import java.util.Set;
import org.junit.jupiter.api.Test;

class YangLibraryE2ETest extends AbstractE2ETest {
    private static final String URI = "/rests/yang-library-version";

    @Test
    void readJsonTest() throws Exception {
        assertContentJson(URI, """
            {
              "ietf-restconf:yang-library-version": "2019-01-04"
            }""");
    }

    @Test
    void readXmlTest() throws Exception {
        assertContentXml(URI, """
            <ietf-restconf:yang-library-version xmlns:ietf-restconf="urn:ietf:params:xml:ns:yang:ietf-restconf">
                2019-01-04
            </ietf-restconf:yang-library-version>""");
    }

    @Test
    void headTest() throws Exception {
        assertHead(URI);
    }

    @Test
    void optionsTest() throws Exception {
        assertOptions(URI, Set.of("GET", "OPTIONS", "HEAD"));
    }
}
