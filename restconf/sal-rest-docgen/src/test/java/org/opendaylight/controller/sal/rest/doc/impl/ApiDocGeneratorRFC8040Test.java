/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.controller.sal.rest.doc.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.netconf.sal.rest.doc.impl.ApiDocGeneratorRFC8040;
import org.opendaylight.netconf.sal.rest.doc.impl.ApiDocServiceImpl;
import org.opendaylight.netconf.sal.rest.doc.impl.ApiDocServiceImpl.URIType;
import org.opendaylight.netconf.sal.rest.doc.swagger.SwaggerObject;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public final class ApiDocGeneratorRFC8040Test {
    private static final String NAME = "toaster2";
    private static final String REVISION_DATE = "2009-11-20";
    private static final String NAME_2 = "toaster";
    private static final String REVISION_DATE_2 = "2009-11-20";

    private EffectiveModelContext context;
    private ApiDocGeneratorRFC8040 generator;

    @Before
    public void setUp() {
        context = YangParserTestUtils.parseYangResourceDirectory("/yang");
        generator = new ApiDocGeneratorRFC8040(DocGenTestHelper.createMockSchemaService(context));
    }

    /**
     * Test that paths are generated according to the model.
     */
    @Test
    public void testPaths() {
        final List<String> expectedPaths = Arrays.asList("/rests/data",
                "/rests/data/toaster2:toaster",
                "/rests/data/toaster2:toaster/toasterSlot={slotId}",
                "/rests/data/toaster2:toaster/toasterSlot={slotId}/toaster-augmented:slotInfo",
                "/rests/data/toaster2:lst",
                "/rests/data/toaster2:lst/cont1",
                "/rests/data/toaster2:lst/cont1/cont11",
                "/rests/data/toaster2:lst/cont1/lst11",
                "/rests/data/toaster2:lst/lst1={key1},{key2}",
                "/rests/operations/toaster2:make-toast",
                "/rests/operations/toaster2:cancel-toast",
                "/rests/operations/toaster2:restock-toaster");

        final Optional<? extends Module> module = context.findModule(NAME, Revision.of(REVISION_DATE));
        assertTrue("Desired module not found", module.isPresent());
        final SwaggerObject doc = generator.getSwaggerDocSpec(module.get(), "http","localhost:8181",
                "/", "", context, URIType.RFC8040, ApiDocServiceImpl.OAversion.V2_0);
        final List<String> actualPaths = new ArrayList<>();
        doc.getPaths().fieldNames().forEachRemaining(actualPaths::add);

        assertEquals(expectedPaths, actualPaths);
    }

    /**
     * Test that generated configuration paths allow to use operations: get, put, delete and post.
     */
    @Test
    public void testConfigPaths() {
        final List<String> configPaths = Arrays.asList("/rests/data/toaster2:lst",
                "/rests/data/toaster2:lst/cont1",
                "/rests/data/toaster2:lst/cont1/cont11",
                "/rests/data/toaster2:lst/cont1/lst11",
                "/rests/data/toaster2:lst/lst1={key1},{key2}");

        final Optional<? extends Module> module = context.findModule(NAME, Revision.of(REVISION_DATE));
        assertTrue("Desired module not found", module.isPresent());
        final SwaggerObject doc = generator.getSwaggerDocSpec(module.get(), "http","localhost:8181",
                "/", "", context, URIType.RFC8040, ApiDocServiceImpl.OAversion.V2_0);

        for (final String path : configPaths) {
            final JsonNode node = doc.getPaths().get(path);
            assertFalse(node.path("get").isMissingNode());
            assertFalse(node.path("put").isMissingNode());
            assertFalse(node.path("delete").isMissingNode());
            assertFalse(node.path("post").isMissingNode());
        }
    }

    /**
     * Test that generated document contains the following definitions.
     */
    @Test
    public void testDefinitions() {
        final Optional<? extends Module> module = context.findModule(NAME, Revision.of(REVISION_DATE));
        assertTrue("Desired module not found", module.isPresent());
        final SwaggerObject doc = generator.getSwaggerDocSpec(module.get(), "http","localhost:8181",
                "/", "", context, URIType.RFC8040, ApiDocServiceImpl.OAversion.V2_0);

        final ObjectNode definitions = doc.getDefinitions();
        assertNotNull(definitions);

        final JsonNode configLstTop = definitions.get("toaster2_config_lst_TOP");
        assertNotNull(configLstTop);
        DocGenTestHelper.containsReferences(configLstTop, "lst",
                "#/definitions/toaster2_config_lst");

        final JsonNode configLst = definitions.get("toaster2_config_lst");
        assertNotNull(configLst);
        DocGenTestHelper.containsReferences(configLst,
                "lst1", "#/definitions/toaster2_lst_config_lst1");
        DocGenTestHelper.containsReferences(configLst,
                "cont1", "#/definitions/toaster2_lst_config_cont1");

        final JsonNode configLst1Top = definitions.get("toaster2_lst_config_lst1_TOP");
        assertNotNull(configLst1Top);
        DocGenTestHelper.containsReferences(configLst1Top,
                "lst1", "#/definitions/toaster2_lst_config_lst1");

        final JsonNode configLst1 = definitions.get("toaster2_lst_config_lst1");
        assertNotNull(configLst1);

        final JsonNode configCont1Top = definitions.get("toaster2_lst_config_cont1_TOP");
        assertNotNull(configCont1Top);
        DocGenTestHelper.containsReferences(configCont1Top,
                "cont1", "#/definitions/toaster2_lst_config_cont1");

        final JsonNode configCont1 = definitions.get("toaster2_lst_config_cont1");
        assertNotNull(configCont1);
        DocGenTestHelper.containsReferences(configCont1,
                "cont11", "#/definitions/toaster2_lst_cont1_config_cont11");
        DocGenTestHelper.containsReferences(configCont1,
                "lst11", "#/definitions/toaster2_lst_cont1_config_lst11");

        final JsonNode configCont11Top = definitions.get("toaster2_lst_cont1_config_cont11_TOP");
        assertNotNull(configCont11Top);
        DocGenTestHelper.containsReferences(configCont11Top,
                "cont11", "#/definitions/toaster2_lst_cont1_config_cont11");

        final JsonNode configCont11 = definitions.get("toaster2_lst_cont1_config_cont11");
        assertNotNull(configCont11);

        final JsonNode configLst11Top = definitions.get("toaster2_lst_cont1_config_lst11_TOP");
        assertNotNull(configLst11Top);
        DocGenTestHelper.containsReferences(configLst11Top,
                "lst11", "#/definitions/toaster2_lst_cont1_config_lst11");

        final JsonNode configLst11 = definitions.get("toaster2_lst_cont1_config_lst11");
        assertNotNull(configLst11);
    }

    /**
     * Test that generated document contains RPC definition for "make-toast" with correct input.
     */
    @Test
    public void testRPC() {
        final Optional<? extends Module> module = context.findModule(NAME_2, Revision.of(REVISION_DATE_2));
        assertTrue("Desired module not found", module.isPresent());
        final SwaggerObject doc = generator.getSwaggerDocSpec(module.get(), "http","localhost:8181",
                "/", "", context, URIType.RFC8040, ApiDocServiceImpl.OAversion.V2_0);
        assertNotNull(doc);

        final ObjectNode definitions = doc.getDefinitions();
        final JsonNode inputTop = definitions.get("toaster_make-toast_input_TOP");
        assertNotNull(inputTop);
        final String testString = "{\"input\":{\"$ref\":\"#/definitions/toaster_make-toast_input\"}}";
        assertEquals(testString, inputTop.get("properties").toString());
        final JsonNode input = definitions.get("toaster_make-toast_input");
        final JsonNode properties = input.get("properties");
        assertTrue(properties.has("toasterDoneness"));
        assertTrue(properties.has("toasterToastType"));
    }
}
