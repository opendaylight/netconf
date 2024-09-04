/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.netty;

import java.util.Set;
import org.junit.jupiter.api.Test;

class YangLibraryE2ETest extends AbstractE2ETest {
    private static final String URI = "/rests/yang-library-version";

    @Test
    void readJson() throws Exception {
        assertContentJson(URI, """
            {
              "ietf-restconf:yang-library-version": "2019-01-04"
            }""");
    }

    // TODO Read XML

    @Test
    void head() throws Exception {
        assertHead(URI);
    }

    @Test
    void options() throws Exception {
        assertOptions(URI, Set.of("GET", "OPTIONS", "HEAD"));
    }
}
