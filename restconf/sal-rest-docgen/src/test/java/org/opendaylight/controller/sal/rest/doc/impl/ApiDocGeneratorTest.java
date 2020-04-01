/*
 * Copyright (c) 2014, 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.controller.sal.rest.doc.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Preconditions;
import java.sql.Date;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.netconf.sal.rest.doc.impl.ApiDocGeneratorDraftO2;
import org.opendaylight.netconf.sal.rest.doc.impl.ApiDocServiceImpl;
import org.opendaylight.netconf.sal.rest.doc.impl.ApiDocServiceImpl.URIType;
import org.opendaylight.netconf.sal.rest.doc.swagger.SwaggerObject;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.Module;

public class ApiDocGeneratorTest {

    private static final String NAMESPACE = "http://netconfcentral.org/ns/toaster2";
    private static final String STRING_DATE = "2009-11-20";
    private static final Date DATE = Date.valueOf(STRING_DATE);
    private static final String NAMESPACE_2 = "http://netconfcentral.org/ns/toaster";
    private static final Date REVISION_2 = Date.valueOf(STRING_DATE);
    private ApiDocGeneratorDraftO2 generator;
    private DocGenTestHelper helper;
    private EffectiveModelContext schemaContext;

    @Before
    public void setUp() throws Exception {
        this.helper = new DocGenTestHelper();
        this.helper.setUp();

        this.schemaContext = this.helper.getSchemaContext();

        this.generator = new ApiDocGeneratorDraftO2(this.helper.createMockSchemaService(this.schemaContext));
    }

    @After
    public void after() throws Exception {
    }

    /**
     * Method: getApiDeclaration(String module, String revision, UriInfo uriInfo).
     */
    @Test
    public void testGetModuleDoc() throws Exception {
        Preconditions.checkArgument(this.helper.getModules() != null, "No modules found");

        for (final Module m : this.helper.getSchemaContext().getModules()) {
            if (m.getQNameModule().getNamespace().toString().equals(NAMESPACE)
                    && m.getQNameModule().getRevision().equals(DATE)) {
                final SwaggerObject doc = this.generator.getSwaggerDocSpec(m, "http","localhost:8181", "/", "",
                        this.schemaContext, URIType.DRAFT02, ApiDocServiceImpl.OAversion.V2_0);
                validateToaster(doc);
                validateSwaggerModules(doc);
            }
        }
    }

    /**
     * Validates whether doc {@code doc} contains concrete specified models.
     */
    private void validateSwaggerModules(final SwaggerObject doc) {
        final ObjectNode definitions = doc.getDefinitions();
        assertNotNull(definitions);

        final JsonNode configLstTop = definitions.get("toaster2_config_lst_TOP");
        assertNotNull(configLstTop);

        containsReferences(configLstTop, "lst", "toaster2_config_lst");

        final JsonNode configLst = definitions.get("toaster2_config_lst");
        assertNotNull(configLst);

        containsReferences(configLst, "lst1", "toaster2_lst_config_lst1");
        containsReferences(configLst, "cont1", "toaster2_lst_config_cont1");

        final JsonNode configLst1Top = definitions.get("toaster2_lst_config_lst1_TOP");
        assertNotNull(configLst1Top);

        containsReferences(configLst1Top, "lst1", "toaster2_config_lst");

        final JsonNode configLst1 = definitions.get("toaster2_lst_config_lst1");
        assertNotNull(configLst1);

        final JsonNode configCont1Top = definitions.get("toaster2_lst_config_cont1_TOP");
        assertNotNull(configCont1Top);

        containsReferences(configCont1Top, "cont1", "toaster2_config_lst");
        final JsonNode configCont1 = definitions.get("toaster2_lst_config_cont1");
        assertNotNull(configCont1);

        containsReferences(configCont1, "cont11", "toaster2_lst_config_cont1");
        containsReferences(configCont1, "lst11", "toaster2_lst_config_lst1");

        final JsonNode configCont11Top = definitions.get("toaster2_lst_cont1_config_cont11_TOP");
        assertNotNull(configCont11Top);

        containsReferences(configCont11Top, "cont11", "toaster2_lst_config_cont1");
        final JsonNode configCont11 = definitions.get("toaster2_lst_cont1_config_cont11");
        assertNotNull(configCont11);

        final JsonNode configLst11Top = definitions.get("toaster2_lst_cont1_config_lst11_TOP");
        assertNotNull(configLst11Top);

        containsReferences(configLst11Top, "lst11", "toaster2_lst_config_cont1");
        final JsonNode configLst11 = definitions.get("toaster2_lst_cont1_config_lst11");
        assertNotNull(configLst11);
    }

    /**
     * Checks whether object {@code mainObject} contains in properties/items key $ref with concrete value.
     */
    private void containsReferences(final JsonNode mainObject, final String childObject,
                                    final String expectedRef) {
        final JsonNode properties = mainObject.get("properties");
        assertNotNull(properties);

        final JsonNode childNode = properties.get(childObject);
        assertNotNull(childNode);

        //list case
        JsonNode refWrapper = childNode.get("items");
        if (refWrapper == null) {
            //container case
            refWrapper = childNode;
        }
        assertEquals(expectedRef, refWrapper.get("$ref").asText());
    }

    @Test
    public void testEdgeCases() throws Exception {
        Preconditions.checkArgument(this.helper.getModules() != null, "No modules found");

        for (final Module m : this.helper.getModules()) {
            if (m.getQNameModule().getNamespace().toString().equals(NAMESPACE_2)
                    && m.getQNameModule().getRevision().equals(REVISION_2)) {
                final SwaggerObject doc = this.generator.getSwaggerDocSpec(m, "http", "localhost:8080", "/restconf", "",
                        this.schemaContext, URIType.DRAFT02, ApiDocServiceImpl.OAversion.V2_0);
                assertNotNull(doc);

                // testing bugs.opendaylight.org bug 1290. UnionType model type.
                final String jsonString = doc.getDefinitions().toString();
                assertTrue(jsonString.contains("testUnion\":{\"required\":false,\"type\":\"-2147483648\","
                        + "\"enum\":[\"-2147483648\",\"Some testUnion\"]}"));
            }
        }
    }

    @Test
    public void testRPCsModel() throws Exception {
        Preconditions.checkArgument(this.helper.getModules() != null, "No modules found");

        for (final Module m : this.helper.getModules()) {
            if (m.getQNameModule().getNamespace().toString().equals(NAMESPACE_2)
                    && m.getQNameModule().getRevision().equals(REVISION_2)) {
                final SwaggerObject doc = this.generator.getSwaggerDocSpec(m, "http","localhost:8181", "/", "",
                        this.schemaContext, URIType.DRAFT02, ApiDocServiceImpl.OAversion.V2_0);
                assertNotNull(doc);

                final ObjectNode definitions = doc.getDefinitions();
                final JsonNode inputTop = definitions.get("toaster_make-toast_input_TOP");
                assertNotNull(inputTop);
                final String testString = "{\"input\":{\"$ref\":\"toaster_make-toast_input\"}}";
                assertEquals(testString, inputTop.get("properties").toString());
                final JsonNode input = definitions.get("toaster_make-toast_input");
                final JsonNode properties = input.get("properties");
                assertTrue(properties.has("toasterDoneness"));
                assertTrue(properties.has("toasterToastType"));
            }
        }
    }

    /**
     * Tests whether from yang files are generated all required paths for HTTP operations (GET, DELETE, PUT, POST)
     *
     * <p>
     * If container | list is augmented then in path there should be specified module name followed with colon (e. g.
     * "/config/module1:element1/element2/module2:element3")
     *
     * @param doc Api declaration
     * @throws Exception if operation fails
     */
    private void validateToaster(final SwaggerObject doc) throws Exception {
        final Set<String> expectedUrls =
                new TreeSet<>(Arrays.asList("/restconf/config", "/restconf/config/toaster2:toaster",
                        "/restconf/config/toaster2:toaster/toasterSlot/{slotId}",
                        "/restconf/config/toaster2:toaster/toasterSlot/{slotId}/toaster-augmented:slotInfo",
                        "/restconf/operational/toaster2:toaster/toasterSlot/{slotId}",
                        "/restconf/operational/toaster2:toaster/toasterSlot/{slotId}/toaster-augmented:slotInfo",
                        "/restconf/config/toaster2:lst",
                        "/restconf/config/toaster2:lst/cont1",
                        "/restconf/config/toaster2:lst/cont1/cont11",
                        "/restconf/config/toaster2:lst/cont1/lst11",
                        "/restconf/config/toaster2:lst/lst1/{key1}/{key2}",
                        "/restconf/operational/toaster2:lst",
                        "/restconf/operational/toaster2:lst/cont1",
                        "/restconf/operational/toaster2:lst/cont1/cont11",
                        "/restconf/operational/toaster2:lst/cont1/lst11",
                        "/restconf/operational/toaster2:lst/lst1/{key1}/{key2}",
                        "/restconf/operational/toaster2:lst/lst1/{key1}/{key2}",
                        "/restconf/operations/toaster2:make-toast",
                        "/restconf/operations/toaster2:cancel-toast"));


        final Set<String> actualUrls = new TreeSet<>();

        final Set<JsonNode> configActualPathItems = new HashSet<>();
        for (final JsonNode pathItem : doc.getPaths()) {
            final String actualUrl = pathItem.fieldNames().next();
            actualUrls.add(actualUrl);
            if (actualUrl.contains("/config/toaster2:toaster/")) {
                configActualPathItems.add(pathItem);
            }
        }

        final boolean isAllDocumented = actualUrls.containsAll(expectedUrls);

        if (!isAllDocumented) {
            expectedUrls.removeAll(actualUrls);
            fail("Missing expected urls: " + expectedUrls);
        }

        final Set<String> expectedConfigMethods = new TreeSet<>(Arrays.asList("get", "put", "delete", "post"));

        for (final JsonNode configPathItem : configActualPathItems) {
            final Set<String> actualConfigMethods = new TreeSet<>();
            final JsonNode operations =  configPathItem.get(0);
            final Iterator<String> it = operations.fieldNames();
            while (it.hasNext()) {
                actualConfigMethods.add(it.next());
            }
            final boolean isAllMethods = actualConfigMethods.containsAll(expectedConfigMethods);
            if (!isAllMethods) {
                expectedConfigMethods.removeAll(actualConfigMethods);
                fail("Missing expected method on config API: " + expectedConfigMethods);
            }
        }

        // TODO: we should really do some more validation of the
        // documentation...
        /*
         * Missing validation: Explicit validation of URLs, and their methods Input / output models.
         */
    }

}
