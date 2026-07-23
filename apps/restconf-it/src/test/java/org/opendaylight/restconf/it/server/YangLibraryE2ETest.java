/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.it.server;

import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.opendaylight.restconf.it.ProtocolVersion;

class YangLibraryE2ETest extends AbstractE2ETest {
    private static final String URI = "/rests/yang-library-version";

    @ParameterizedTest
    @EnumSource(ProtocolVersion.class)
    void readJsonTest(final ProtocolVersion version) throws Exception {
        assertContentJson(URI, """
            {
              "ietf-restconf:yang-library-version": "2019-01-04"
            }""", version);
    }

    @ParameterizedTest
    @EnumSource(ProtocolVersion.class)
    void readXmlTest(final ProtocolVersion version) throws Exception {
        assertContentXml(URI, """
            <ietf-restconf:yang-library-version xmlns:ietf-restconf="urn:ietf:params:xml:ns:yang:ietf-restconf">
                2019-01-04
            </ietf-restconf:yang-library-version>""", version);
    }

    @ParameterizedTest
    @EnumSource(ProtocolVersion.class)
    void headTest(final ProtocolVersion version) throws Exception {
        assertHead(URI, version);
    }

    @ParameterizedTest
    @EnumSource(ProtocolVersion.class)
    void optionsTest(final ProtocolVersion version) throws Exception {
        assertOptions(URI, Set.of("GET", "OPTIONS", "HEAD"), version);
    }
}
