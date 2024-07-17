/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.opendaylight.restconf.api.QueryParameters;
import org.opendaylight.restconf.api.query.ContentParam;
import org.opendaylight.restconf.api.query.DepthParam;
import org.opendaylight.restconf.api.query.FieldsParam;
import org.opendaylight.restconf.server.api.DataGetParams;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContext;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.stmt.ContainerEffectiveStatement;

class NormalizedNodeFieldsParamTest {
    /**
     * Test of parsing user defined parameters from URI request.
     */
    @Test
    void parseUriParametersUserDefinedTest() throws Exception {
        final QName containerChild = QName.create("ns", "container-child");

        final var params = DataGetParams.of(QueryParameters.of(
            ContentParam.CONFIG, DepthParam.of(10), FieldsParam.forUriValue("container-child")));
        // content
        assertEquals(ContentParam.CONFIG, params.content());

        // depth
        final var depth = params.depth();
        assertNotNull(depth);
        assertEquals(10, depth.value());

        // fields
        final var paramsFields = params.fields();
        assertNotNull(paramsFields);

        // fields for write filtering
        final var containerSchema = mock(ContainerSchemaNode.class,
            withSettings().extraInterfaces(ContainerEffectiveStatement.class));
        final var containerQName = QName.create(containerChild, "container");
        doReturn(containerQName).when(containerSchema).getQName();
        final var containerChildSchema = mock(LeafSchemaNode.class);
        doReturn(containerChild).when(containerChildSchema).getQName();
        doReturn(containerChildSchema).when(containerSchema).dataChildByName(containerChild);

        final var fields = NormalizedNodeWriter.translateFieldsParam(mock(EffectiveModelContext.class),
            DataSchemaContext.of(containerSchema), paramsFields);
        assertNotNull(fields);
        assertEquals(1, fields.size());
        assertEquals(Set.of(containerChild), fields.get(0));
    }
}
