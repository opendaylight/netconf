/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.rest.doc.impl;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import javax.ws.rs.core.UriInfo;
import org.junit.BeforeClass;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public abstract class AbstractApiDocTest {
    static EffectiveModelContext CONTEXT;
    static DOMSchemaService SCHEMA_SERVICE;
    static UriInfo URI_INFO;

    @BeforeClass
    public static void beforeClass() throws Exception {
        CONTEXT = YangParserTestUtils.parseYangResourceDirectory("/yang");
        SCHEMA_SERVICE = mock(DOMSchemaService.class);
        when(SCHEMA_SERVICE.getGlobalContext()).thenReturn(CONTEXT);
        URI_INFO = DocGenTestHelper.createMockUriInfo("http://localhost/path");
    }

    protected static List<String> getPathParameters(final ObjectNode paths, final String path) {
        final var params = new ArrayList<String>();
        paths.get(path).get("post").get("parameters").elements()
                .forEachRemaining(p -> {
                    final String name = p.get("name").asText();
                    if (!"content".equals(name)) {
                        params.add(name);
                    }
                });
        return params;
    }

    protected static List<String> getGetPathParameters(final ObjectNode paths, final String path) {
        final var params = new ArrayList<String>();
        paths.get(path).get("get").get("parameters").elements()
            .forEachRemaining(p -> {
                final String name = p.get("name").asText();
                if (!"content".equals(name)) {
                    params.add(name);
                }
            });
        return params;
    }
}
