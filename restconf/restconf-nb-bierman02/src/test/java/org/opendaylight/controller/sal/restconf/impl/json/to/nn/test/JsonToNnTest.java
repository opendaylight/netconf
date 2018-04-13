/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.controller.sal.restconf.impl.json.to.nn.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.controller.md.sal.rest.common.TestRestconfUtils;
import org.opendaylight.controller.sal.rest.impl.test.providers.AbstractBodyReaderTest;
import org.opendaylight.netconf.sal.rest.impl.JsonNormalizedNodeBodyReader;
import org.opendaylight.restconf.common.context.NormalizedNodeContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNodes;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JsonToNnTest extends AbstractBodyReaderTest {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractBodyReaderTest.class);

    private final JsonNormalizedNodeBodyReader jsonBodyReader;
    private static SchemaContext schemaContext;

    public JsonToNnTest() {
        super(schemaContext, null);
        this.jsonBodyReader = new JsonNormalizedNodeBodyReader(controllerContext);
    }

    @BeforeClass
    public static void initialize() throws FileNotFoundException {
        final Collection<File> testFiles = TestRestconfUtils.loadFiles("/json-to-nn/simple-list-yang/1");
        testFiles.addAll(TestRestconfUtils.loadFiles("/json-to-nn/simple-list-yang/3"));
        testFiles.addAll(TestRestconfUtils.loadFiles("/json-to-nn/simple-list-yang/4"));
        testFiles.addAll(TestRestconfUtils.loadFiles("/json-to-nn/simple-container-yang"));
        testFiles.addAll(TestRestconfUtils.loadFiles("/common/augment/yang"));
        schemaContext = YangParserTestUtils.parseYangFiles(testFiles);
    }

    @Test
    public void simpleListTest() throws Exception {
        simpleTest("/json-to-nn/simple-list.json",
                "lst", "simple-list-yang1");
    }

    @Test
    public void simpleContainerTest() throws Exception {
        simpleTest("/json-to-nn/simple-container.json",
                "cont", "simple-container-yang");
    }

    @Test
    public void multipleItemsInLeafListTest() throws Exception {
        final NormalizedNodeContext normalizedNodeContext = prepareNNC(
                "/json-to-nn/multiple-leaflist-items.json",
                "simple-list-yang1:lst");
        assertNotNull(normalizedNodeContext);

        final String dataTree = NormalizedNodes.toStringTree(normalizedNodeContext
                .getData());
        assertTrue(dataTree.contains("45"));
        assertTrue(dataTree.contains("55"));
        assertTrue(dataTree.contains("66"));
    }

    @Test
    public void multipleItemsInListTest() throws Exception {
        final NormalizedNodeContext normalizedNodeContext = prepareNNC(
                "/json-to-nn/multiple-items-in-list.json",
                "multiple-items-yang:lst");
        assertNotNull(normalizedNodeContext);

        assertEquals("lst", normalizedNodeContext.getData().getNodeType()
                .getLocalName());

        verityMultipleItemsInList(normalizedNodeContext);
    }

    @Test
    public void nullArrayToSimpleNodeWithNullValueTest() throws Exception {
        final NormalizedNodeContext normalizedNodeContext = prepareNNC(
                "/json-to-nn/array-with-null.json", "array-with-null-yang:cont");
        assertNotNull(normalizedNodeContext);

        assertEquals("cont", normalizedNodeContext.getData().getNodeType()
                .getLocalName());

        final String dataTree = NormalizedNodes.toStringTree(normalizedNodeContext
                .getData());
        assertTrue(dataTree.contains("lf"));
        assertTrue(dataTree.contains("empty"));
    }

    @Test
    public void incorrectTopLevelElementsTest() throws Exception {
        mockBodyReader("simple-list-yang1:lst", this.jsonBodyReader, false);

        InputStream inputStream = this.getClass().getResourceAsStream(
                "/json-to-nn/wrong-top-level1.json");

        int countExceptions = 0;
        RestconfDocumentedException exception = null;

        try {
            this.jsonBodyReader.readFrom(null, null, null, this.mediaType, null,
                    inputStream);
        } catch (final RestconfDocumentedException e) {
            exception = e;
            countExceptions++;
        }
        assertNotNull(exception);
        assertEquals(
                "Error parsing input: Schema node with name wrong was not found under "
                        + "(urn:ietf:params:xml:ns:netconf:base:1.0)data.",
                exception.getErrors().get(0).getErrorMessage());

        inputStream = this.getClass().getResourceAsStream(
                "/json-to-nn/wrong-top-level2.json");
        exception = null;
        try {
            this.jsonBodyReader.readFrom(null, null, null, this.mediaType, null,
                    inputStream);
        } catch (final RestconfDocumentedException e) {
            exception = e;
            countExceptions++;
        }
        assertNotNull(exception);
        assertEquals(
                "Error parsing input: Schema node with name lst1 was not found under "
                        + "(urn:ietf:params:xml:ns:netconf:base:1.0)data.",
                exception.getErrors().get(0).getErrorMessage());

        inputStream = this.getClass().getResourceAsStream(
                "/json-to-nn/wrong-top-level3.json");
        exception = null;
        try {
            this.jsonBodyReader.readFrom(null, null, null, this.mediaType, null,
                    inputStream);
        } catch (final RestconfDocumentedException e) {
            exception = e;
            countExceptions++;
        }
        assertNotNull(exception);
        assertEquals(
                "Error parsing input: Schema node with name lf was not found under "
                        + "(urn:ietf:params:xml:ns:netconf:base:1.0)data.",
                exception.getErrors().get(0).getErrorMessage());
        assertEquals(3, countExceptions);
    }

    @Test
    public void emptyDataReadTest() throws Exception {
        final NormalizedNodeContext normalizedNodeContext = prepareNNC(
                "/json-to-nn/empty-data.json", "array-with-null-yang:cont");
        assertNotNull(normalizedNodeContext);

        assertEquals("cont", normalizedNodeContext.getData().getNodeType()
                .getLocalName());

        final String dataTree = NormalizedNodes.toStringTree(normalizedNodeContext
                .getData());

        assertTrue(dataTree.contains("lflst1"));

        assertTrue(dataTree.contains("lflst2 45"));

        RestconfDocumentedException exception = null;
        mockBodyReader("array-with-null-yang:cont", this.jsonBodyReader, false);
        final InputStream inputStream = this.getClass().getResourceAsStream(
                "/json-to-nn/empty-data.json1");

        try {
            this.jsonBodyReader.readFrom(null, null, null, this.mediaType, null,
                    inputStream);
        } catch (final RestconfDocumentedException e) {
            exception = e;
        }
        assertNotNull(exception);
        assertEquals("Error parsing input: null", exception.getErrors().get(0)
                .getErrorMessage());
    }

    @Test
    public void testJsonBlankInput() throws Exception {
        final NormalizedNodeContext normalizedNodeContext = prepareNNC("",
                "array-with-null-yang:cont");
        assertNull(normalizedNodeContext);
    }

    @Test
    public void notSupplyNamespaceIfAlreadySupplied()throws Exception {
        final String uri = "simple-list-yang1" + ":" + "lst";

        final NormalizedNodeContext normalizedNodeContext = prepareNNC(
                "/json-to-nn/simple-list.json", uri);
        assertNotNull(normalizedNodeContext);

        verifyNormaluizedNodeContext(normalizedNodeContext, "lst");

        mockBodyReader("simple-list-yang2:lst", this.jsonBodyReader, false);
        final InputStream inputStream = this.getClass().getResourceAsStream(
                "/json-to-nn/simple-list.json");

        try {
            this.jsonBodyReader.readFrom(null, null, null, this.mediaType, null,
                    inputStream);
            fail("NormalizedNodeContext should not be create because of different namespace");
        } catch (final RestconfDocumentedException e) {
            LOG.warn("Read from InputStream failed. Message: {}. Status: {}", e.getMessage(), e.getStatus());
        }

        verifyNormaluizedNodeContext(normalizedNodeContext, "lst");
    }

    @Test
    public void dataAugmentedTest() throws Exception {
        NormalizedNodeContext normalizedNodeContext = prepareNNC(
                "/common/augment/json/dataa.json", "main:cont");

        assertNotNull(normalizedNodeContext);
        assertEquals("cont", normalizedNodeContext.getData().getNodeType()
                .getLocalName());

        String dataTree = NormalizedNodes.toStringTree(normalizedNodeContext
                .getData());
        assertTrue(dataTree.contains("cont1"));
        assertTrue(dataTree.contains("lf11 lf11 value from a"));

        normalizedNodeContext = prepareNNC("/common/augment/json/datab.json",
                "main:cont");

        assertNotNull(normalizedNodeContext);
        assertEquals("cont", normalizedNodeContext.getData().getNodeType()
                .getLocalName());
        dataTree = NormalizedNodes
                .toStringTree(normalizedNodeContext.getData());
        assertTrue(dataTree.contains("cont1"));
        assertTrue(dataTree.contains("lf11 lf11 value from b"));
    }

    private void simpleTest(final String jsonPath, final String topLevelElementName,
            final String moduleName) throws Exception {
        final String uri = moduleName + ":" + topLevelElementName;

        final NormalizedNodeContext normalizedNodeContext = prepareNNC(jsonPath, uri);
        assertNotNull(normalizedNodeContext);

        verifyNormaluizedNodeContext(normalizedNodeContext, topLevelElementName);
    }

    private NormalizedNodeContext prepareNNC(final String jsonPath, final String uri) throws Exception {
        try {
            mockBodyReader(uri, this.jsonBodyReader, false);
        } catch (NoSuchFieldException | SecurityException
                | IllegalArgumentException | IllegalAccessException e) {
            LOG.warn("Operation failed due to: {}", e.getMessage());
        }
        final InputStream inputStream = this.getClass().getResourceAsStream(jsonPath);

        NormalizedNodeContext normalizedNodeContext = null;

        try {
            normalizedNodeContext = this.jsonBodyReader.readFrom(null, null, null,
                    this.mediaType, null, inputStream);
        } catch (WebApplicationException | IOException e) {
            // TODO Auto-generated catch block
        }

        return normalizedNodeContext;
    }

    private static void verifyNormaluizedNodeContext(final NormalizedNodeContext normalizedNodeContext,
            final String topLevelElementName) {
        assertEquals(topLevelElementName, normalizedNodeContext.getData()
                .getNodeType().getLocalName());

        final String dataTree = NormalizedNodes.toStringTree(normalizedNodeContext
                .getData());
        assertTrue(dataTree.contains("cont1"));
        assertTrue(dataTree.contains("lst1"));
        assertTrue(dataTree.contains("lflst1"));
        assertTrue(dataTree.contains("lflst1_1"));
        assertTrue(dataTree.contains("lflst1_2"));
        assertTrue(dataTree.contains("lf1"));
    }

    private static void verityMultipleItemsInList(final NormalizedNodeContext normalizedNodeContext) {

        final String dataTree = NormalizedNodes.toStringTree(normalizedNodeContext
                .getData());
        assertTrue(dataTree.contains("lf11"));
        assertTrue(dataTree.contains("lf11_1"));
        assertTrue(dataTree.contains("lflst11"));
        assertTrue(dataTree.contains("45"));
        assertTrue(dataTree.contains("cont11"));
        assertTrue(dataTree.contains("lst11"));
    }

    @Test
    public void unsupportedDataFormatTest() throws Exception {
        mockBodyReader("simple-list-yang1:lst", this.jsonBodyReader, false);

        final InputStream inputStream = this.getClass().getResourceAsStream(
                "/json-to-nn/unsupported-json-format.json");

        RestconfDocumentedException exception = null;

        try {
            this.jsonBodyReader.readFrom(null, null, null, this.mediaType, null,
                    inputStream);
        } catch (final RestconfDocumentedException e) {
            exception = e;
        }
        LOG.info(exception.getErrors().get(0).getErrorMessage());

        assertTrue(exception.getErrors().get(0).getErrorMessage()
                .contains("is not a simple type"));
    }

    @Test
    public void invalidUriCharacterInValue() throws Exception {
        mockBodyReader("array-with-null-yang:cont", this.jsonBodyReader, false);

        final InputStream inputStream = this.getClass().getResourceAsStream(
                "/json-to-nn/invalid-uri-character-in-value.json");

        final NormalizedNodeContext normalizedNodeContext = this.jsonBodyReader.readFrom(
                null, null, null, this.mediaType, null, inputStream);
        assertNotNull(normalizedNodeContext);

        assertEquals("cont", normalizedNodeContext.getData().getNodeType()
                .getLocalName());

        final String dataTree = NormalizedNodes.toStringTree(normalizedNodeContext
                .getData());
        assertTrue(dataTree.contains("lf1 module<Name:value lf1"));
        assertTrue(dataTree.contains("lf2 module>Name:value lf2"));
    }

    @Override
    protected MediaType getMediaType() {
        return null;
    }

}
