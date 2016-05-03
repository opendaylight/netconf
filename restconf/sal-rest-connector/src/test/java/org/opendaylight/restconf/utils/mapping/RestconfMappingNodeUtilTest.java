/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.utils.mapping;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Before;
import org.junit.Test;
import org.opendaylight.controller.md.sal.rest.common.TestRestconfUtils;
import org.opendaylight.restconf.Draft11;
import org.opendaylight.restconf.utils.schema.context.RestconfSchemaUtil;
import org.opendaylight.yangtools.yang.common.SimpleDateFormatUtil;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

public class RestconfMappingNodeUtilTest {
    private SchemaContext schemaContext;
    private org.opendaylight.yangtools.yang.model.api.Module restconfModule;

    @Before
    public void setup() throws Exception {
        schemaContext = TestRestconfUtils.loadSchemaContext("/modules");
        restconfModule = schemaContext.findModuleByName("ietf-restconf",
                SimpleDateFormatUtil.getRevisionFormat().parse("2013-10-19"));
    }

    @Test
    public void restconfMappingNodeTest() {
        MapNode mapNode = RestconfMappingNodeUtil.restconfMappingNode(restconfModule, schemaContext.getModules());

        assertNotNull(mapNode);
        assertEquals("module", mapNode.getNodeType().getLocalName());
        assertEquals("2013-10-19", mapNode.getNodeType().getModule().getFormattedRevision());
        assertEquals("urn:ietf:params:xml:ns:yang:ietf-restconf",
                mapNode.getNodeType().getModule().getNamespace().toString());
        assertEquals(9, mapNode.getValue().size());
    }

    @Test
    public void toStreamEntryNodeTest() {
        MapEntryNode mapEntryNode = RestconfMappingNodeUtil.toStreamEntryNode("module1",
                RestconfSchemaUtil.getRestconfSchemaNode(restconfModule,
                        Draft11.MonitoringModule.STREAM_LIST_SCHEMA_NODE));

        assertNotNull(mapEntryNode);
        assertEquals("stream", mapEntryNode.getNodeType().getLocalName());
        assertEquals("2013-10-19", mapEntryNode.getNodeType().getModule().getFormattedRevision());
        assertEquals("urn:ietf:params:xml:ns:yang:ietf-restconf",
                mapEntryNode.getNodeType().getModule().getNamespace().toString());
        assertEquals(5, mapEntryNode.getValue().size());
    }
}
