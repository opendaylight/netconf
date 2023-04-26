/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.BeforeClass;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.openapi.model.Path;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public abstract class AbstractOpenApiTest {
    protected static EffectiveModelContext CONTEXT;
    protected static DOMSchemaService SCHEMA_SERVICE;

    @BeforeClass
    public static void beforeClass() {
        CONTEXT = YangParserTestUtils.parseYangResourceDirectory("/yang");
        SCHEMA_SERVICE = mock(DOMSchemaService.class);
        when(SCHEMA_SERVICE.getGlobalContext()).thenReturn(CONTEXT);
    }

    protected static List<String> getPathParameters(final Map<String, Path> paths, final String path) {
        final var params = new ArrayList<String>();
        paths.get(path).getPost().get("parameters").elements()
            .forEachRemaining(p -> params.add(p.get("name").asText()));
        return params;
    }
}
