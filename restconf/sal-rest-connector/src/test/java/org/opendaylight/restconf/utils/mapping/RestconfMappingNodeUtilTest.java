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
import org.opendaylight.yangtools.yang.common.QNameModule;
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
        assertNotNull("Mapping of modules should be successful", mapNode);

        assertEquals("Looking for module list schema node",
                Draft11.RestconfModule.MODULE_LIST_SCHEMA_NODE, mapNode.getNodeType().getLocalName());

        QNameModule restconfModule = mapNode.getNodeType().getModule();
        assertNotNull("Restconf module should be found", restconfModule);
        assertEquals("Expected correct Restconf module revision",
                Draft11.RestconfModule.REVISION, restconfModule.getFormattedRevision());
        assertEquals("Expected correct Restconf module namespace",
                Draft11.RestconfModule.NAMESPACE, restconfModule.getNamespace().toString());
    }

    @Test
    public void toStreamEntryNodeTest() {
        MapEntryNode mapEntryNode = RestconfMappingNodeUtil.toStreamEntryNode("module1",
                RestconfSchemaUtil.getRestconfSchemaNode(restconfModule,
                        Draft11.MonitoringModule.STREAM_LIST_SCHEMA_NODE));
        assertNotNull("Map entry node should be created", mapEntryNode);

        assertEquals("Looking for stream list scheam node",
                Draft11.MonitoringModule.STREAM_LIST_SCHEMA_NODE, mapEntryNode.getNodeType().getLocalName());

        QNameModule restconfModule = mapEntryNode.getNodeType().getModule();
        assertNotNull("Restconf module should be found", restconfModule);
        assertEquals("Expected correct Restconf module revision",
                Draft11.RestconfModule.REVISION, restconfModule.getFormattedRevision());
        assertEquals("Expected correct Restconf module namespace",
                Draft11.RestconfModule.NAMESPACE, restconfModule.getNamespace().toString());
    }
}
