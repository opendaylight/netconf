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
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import javax.ws.rs.core.UriInfo;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.controller.sal.core.api.model.SchemaService;
import org.opendaylight.netconf.sal.rest.doc.impl.ApiDocGenerator;
import org.opendaylight.netconf.sal.rest.doc.swagger.Api;
import org.opendaylight.netconf.sal.rest.doc.swagger.ApiDeclaration;
import org.opendaylight.netconf.sal.rest.doc.swagger.Operation;
import org.opendaylight.netconf.sal.rest.doc.swagger.Parameter;
import org.opendaylight.netconf.sal.rest.doc.swagger.Resource;
import org.opendaylight.netconf.sal.rest.doc.swagger.ResourceList;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

public class ApiDocGeneratorTest {

    public static final String HTTP_HOST = "http://host";
    private static final String NAMESPACE = "http://netconfcentral.org/ns/toaster2";
    private static final String STRING_DATE = "2009-11-20";
    private static final Date DATE = Date.valueOf(STRING_DATE);
    private static final String NAMESPACE_2 = "http://netconfcentral.org/ns/toaster";
    private static final Date REVISION_2 = Date.valueOf(STRING_DATE);
    private ApiDocGenerator generator;
    private DocGenTestHelper helper;
    private SchemaContext schemaContext;

    @Before
    public void setUp() throws Exception {
        this.generator = new ApiDocGenerator();
        generator.setDraft(false);
        this.helper = new DocGenTestHelper();
        this.helper.setUp();

        this.schemaContext = this.helper.getSchemaContext();
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
                final ApiDeclaration doc = this.generator.getSwaggerDocSpec(m, "http://localhost:8080/restconf", "",
                        this.schemaContext);
                validateToaster(doc);
                validateTosterDocContainsModulePrefixes(doc);
                validateSwaggerModules(doc);
                validateSwaggerApisForPost(doc);
            }
        }
    }

    /**
     * Validate whether ApiDelcaration contains Apis with concrete path and whether this Apis contain specified POST
     * operations.
     */
    private void validateSwaggerApisForPost(final ApiDeclaration doc) {
        // two POST URI with concrete schema name in summary
        final Api lstApi = findApi("/config/toaster2:lst", doc);
        assertNotNull("Api /config/toaster2:lst wasn't found", lstApi);
        assertTrue("POST for cont1 in lst is missing",
                findOperation(lstApi.getOperations(), "POST", "(config)lstPOST", "toaster2/lst(config)lst1-TOP",
                        "toaster2/lst(config)cont1-TOP"));

        final Api cont1Api = findApi("/config/toaster2:lst/cont1", doc);
        assertNotNull("Api /config/toaster2:lst/cont1 wasn't found", cont1Api);
        assertTrue("POST for cont11 in cont1 is missing",
            findOperation(cont1Api.getOperations(), "POST", "(config)cont1POST", "toaster2/lst/cont1(config)cont11-TOP",
                    "toaster2/lst/cont1(config)lst11-TOP"));

        // no POST URI
        final Api cont11Api = findApi("/config/toaster2:lst/cont1/cont11", doc);
        assertNotNull("Api /config/toaster2:lst/cont1/cont11 wasn't found", cont11Api);
        assertTrue("POST operation shouldn't be present.", findOperations(cont11Api.getOperations(), "POST").isEmpty());

    }

    /**
     * Tries to find operation with name {@code operationName} and with summary {@code summary}.
     */
    private boolean findOperation(final List<Operation> operations, final String operationName, final String type,
                                  final String... searchedParameters) {
        final Set<Operation> filteredOperations = findOperations(operations, operationName);
        for (final Operation operation : filteredOperations) {
            if (operation.getType().equals(type)) {
                final List<Parameter> parameters = operation.getParameters();
                return containAllParameters(parameters, searchedParameters);
            }
        }
        return false;
    }

    private Set<Operation> findOperations(final List<Operation> operations, final String operationName) {
        final Set<Operation> filteredOperations = new HashSet<>();
        for (final Operation operation : operations) {
            if (operation.getMethod().equals(operationName)) {
                filteredOperations.add(operation);
            }
        }
        return filteredOperations;
    }

    private boolean containAllParameters(final List<Parameter> searchedIns, final String[] searchedWhats) {
        for (final String searchedWhat : searchedWhats) {
            boolean parameterFound = false;
            for (final Parameter searchedIn : searchedIns) {
                if (searchedIn.getType().equals(searchedWhat)) {
                    parameterFound = true;
                }
            }
            if (!parameterFound) {
                return false;
            }
        }
        return true;
    }

    /**
     * Tries to find {@code Api} with path {@code path}.
     */
    private Api findApi(final String path, final ApiDeclaration doc) {
        for (final Api api : doc.getApis()) {
            if (api.getPath().equals(path)) {
                return api;
            }
        }
        return null;
    }

    /**
     * Validates whether doc {@code doc} contains concrete specified models.
     */
    private void validateSwaggerModules(final ApiDeclaration doc) {
        final ObjectNode models = doc.getModels();
        assertNotNull(models);

        final JsonNode configLstTop = models.get("toaster2(config)lst-TOP");
        assertNotNull(configLstTop);

        containsReferences(configLstTop, "toaster2:lst", "toaster2(config)");

        final JsonNode configLst = models.get("toaster2(config)lst");
        assertNotNull(configLst);

        containsReferences(configLst, "toaster2:lst1", "toaster2/lst(config)");
        containsReferences(configLst, "toaster2:cont1", "toaster2/lst(config)");

        final JsonNode configLst1Top = models.get("toaster2/lst(config)lst1-TOP");
        assertNotNull(configLst1Top);

        containsReferences(configLst1Top, "toaster2:lst1", "toaster2/lst(config)");

        final JsonNode configLst1 = models.get("toaster2/lst(config)lst1");
        assertNotNull(configLst1);

        final JsonNode configCont1Top = models.get("toaster2/lst(config)cont1-TOP");
        assertNotNull(configCont1Top);

        containsReferences(configCont1Top, "toaster2:cont1", "toaster2/lst(config)");
        final JsonNode configCont1 = models.get("toaster2/lst(config)cont1");
        assertNotNull(configCont1);

        containsReferences(configCont1, "toaster2:cont11", "toaster2/lst/cont1(config)");
        containsReferences(configCont1, "toaster2:lst11", "toaster2/lst/cont1(config)");

        final JsonNode configCont11Top = models.get("toaster2/lst/cont1(config)cont11-TOP");
        assertNotNull(configCont11Top);

        containsReferences(configCont11Top, "toaster2:cont11", "toaster2/lst/cont1(config)");
        final JsonNode configCont11 = models.get("toaster2/lst/cont1(config)cont11");
        assertNotNull(configCont11);

        final JsonNode configlst11Top = models.get("toaster2/lst/cont1(config)lst11-TOP");
        assertNotNull(configlst11Top);

        containsReferences(configlst11Top, "toaster2:lst11", "toaster2/lst/cont1(config)");
        final JsonNode configLst11 = models.get("toaster2/lst/cont1(config)lst11");
        assertNotNull(configLst11);
    }

    /**
     * Checks whether object {@code mainObject} contains in properties/items key $ref with concrete value.
     */
    private void containsReferences(final JsonNode mainObject, final String childObject, final String prefix) {
        final JsonNode properties = mainObject.get("properties");
        assertNotNull(properties);

        final JsonNode nodeInProperties = properties.get(childObject);
        assertNotNull(nodeInProperties);

        final JsonNode itemsInNodeInProperties = nodeInProperties.get("items");
        assertNotNull(itemsInNodeInProperties);

        final String itemRef = itemsInNodeInProperties.get("$ref").asText();
        assertEquals(prefix + childObject.split(":")[1], itemRef);
    }

    @Test
    public void testEdgeCases() throws Exception {
        Preconditions.checkArgument(this.helper.getModules() != null, "No modules found");

        for (final Module m : this.helper.getModules()) {
            if (m.getQNameModule().getNamespace().toString().equals(NAMESPACE_2)
                    && m.getQNameModule().getRevision().equals(REVISION_2)) {
                final ApiDeclaration doc = this.generator.getSwaggerDocSpec(m, "http://localhost:8080/restconf", "",
                        this.schemaContext);
                assertNotNull(doc);

                // testing bugs.opendaylight.org bug 1290. UnionType model type.
                final String jsonString = doc.getModels().toString();
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
                final ApiDeclaration doc = this.generator.getSwaggerDocSpec(m, "http://localhost:8080/restconf", "",
                        this.schemaContext);
                assertNotNull(doc);

                final ObjectNode models = doc.getModels();
                final JsonNode inputTop = models.get("(make-toast)input-TOP");
                final String testString =
                        "{\"toaster:input\":{\"type\":\"object\",\"items\":{\"$ref\":\"(make-toast)input\"}}}";
                assertEquals(testString, inputTop.get("properties").toString());
                final JsonNode input = models.get("(make-toast)input");
                final JsonNode properties = input.get("properties");
                assertTrue(properties.has("toaster:toasterDoneness"));
                assertTrue(properties.has("toaster:toasterToastType"));
            }
        }
    }

    /**
     * Tests whether from yang files are generated all required paths for HTTP operations (GET, DELETE, PUT, POST)
     *
     * <p>
     * If container | list is augmented then in path there should be specified module name followed with collon (e. g.
     * "/config/module1:element1/element2/module2:element3")
     *
     * @param doc Api declaration
     * @throws Exception if operation fails
     */
    private void validateToaster(final ApiDeclaration doc) throws Exception {
        final Set<String> expectedUrls = new TreeSet<>(Arrays.asList("/config/toaster2:toaster",
                "/operational/toaster2:toaster", "/operations/toaster2:cancel-toast",
                "/operations/toaster2:make-toast", "/operations/toaster2:restock-toaster",
                "/config/toaster2:toaster/toasterSlot/{slotId}/toaster-augmented:slotInfo"));

        final Set<String> actualUrls = new TreeSet<>();

        Api configApi = null;
        for (final Api api : doc.getApis()) {
            actualUrls.add(api.getPath());
            if (api.getPath().contains("/config/toaster2:toaster/")) {
                configApi = api;
            }
        }

        boolean containsAll = actualUrls.containsAll(expectedUrls);
        if (!containsAll) {
            expectedUrls.removeAll(actualUrls);
            fail("Missing expected urls: " + expectedUrls);
        }

        final Set<String> expectedConfigMethods = new TreeSet<>(Arrays.asList("GET", "PUT", "DELETE"));
        final Set<String> actualConfigMethods = new TreeSet<>();
        for (final Operation oper : configApi.getOperations()) {
            actualConfigMethods.add(oper.getMethod());
        }

        containsAll = actualConfigMethods.containsAll(expectedConfigMethods);
        if (!containsAll) {
            expectedConfigMethods.removeAll(actualConfigMethods);
            fail("Missing expected method on config API: " + expectedConfigMethods);
        }

        // TODO: we should really do some more validation of the
        // documentation...
        /*
         * Missing validation: Explicit validation of URLs, and their methods Input / output models.
         */
    }

    @Test
    public void testGetResourceListing() throws Exception {
        final UriInfo info = this.helper.createMockUriInfo(HTTP_HOST);
        final SchemaService mockSchemaService = this.helper.createMockSchemaService(this.schemaContext);

        this.generator.setSchemaService(mockSchemaService);

        final ResourceList resourceListing = this.generator.getResourceListing(info);

        Resource toaster = null;
        Resource toaster2 = null;
        for (final Resource r : resourceListing.getApis()) {
            final String path = r.getPath();
            if (path.contains("toaster2")) {
                toaster2 = r;
            } else if (path.contains("toaster")) {
                toaster = r;
            }
        }

        assertNotNull(toaster2);
        assertNotNull(toaster);

        assertEquals(HTTP_HOST + "/toaster(2009-11-20)", toaster.getPath());
        assertEquals(HTTP_HOST + "/toaster2(2009-11-20)", toaster2.getPath());
    }

    private void validateTosterDocContainsModulePrefixes(final ApiDeclaration doc) {
        final ObjectNode topLevelJson = doc.getModels();

        final JsonNode configToaster = topLevelJson.get("toaster2(config)toaster");
        assertNotNull("(config)toaster JSON object missing", configToaster);
        // without module prefix
        containsProperties(configToaster, "toaster2:toasterSlot");

        final JsonNode toasterSlot = topLevelJson.get("toaster2/toaster(config)toasterSlot");
        assertNotNull("(config)toasterSlot JSON object missing", toasterSlot);
        // with module prefix
        containsProperties(toasterSlot, "toaster2:toaster-augmented:slotInfo");
    }

    private void containsProperties(final JsonNode jsonObject, final String... properties) {
        for (final String property : properties) {
            final JsonNode propertiesObject = jsonObject.get("properties");
            assertNotNull("Properties object missing in ", propertiesObject);
            final JsonNode concretePropertyObject = propertiesObject.get(property);
            assertNotNull(property + " is missing", concretePropertyObject);
        }
    }
}