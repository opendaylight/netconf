/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.jaxrs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

@ExtendWith(MockitoExtension.class)
class RestconfYangLibraryVersionGetTest extends AbstractRestconfTest {
    @Override
    EffectiveModelContext modelContext() {
        return YangParserTestUtils.parseYangResourceDirectory("/restconf/impl");
    }

    @Test
    void testLibraryVersion() {
        final var body = assertFormattableBody(200, ar -> restconf.yangLibraryVersionGETJ(sc, ar));

        assertFormat("""
            {"ietf-restconf:yang-library-version":"2019-01-04"}""", body::formatToJSON, false);
        assertFormat("""
            <yang-library-version xmlns="urn:ietf:params:xml:ns:yang:ietf-restconf">2019-01-04\
            </yang-library-version>""", body::formatToXML, false);
    }
}
