/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.nb.rfc8040.utils.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.net.URI;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorTag;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorType;
import org.opendaylight.restconf.nb.rfc8040.TestRestconfUtils;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

/**
 * Unit test for {@link ParserFieldsParameter}.
 */
public class ParserFieldsParameterTest {

    @Mock
    private InstanceIdentifierContext<ContainerSchemaNode> identifierContext;

    // container jukebox
    @Mock
    private ContainerSchemaNode containerJukebox;
    private QName jukeboxQName;

    // container player
    @Mock
    private ContainerSchemaNode containerPlayer;
    private QName playerQName;

    // container library
    @Mock
    private ContainerSchemaNode containerLibrary;
    private QName libraryQName;

    // container augmented library
    @Mock
    private ContainerSchemaNode augmentedContainerLibrary;
    private QName augmentedLibraryQName;

    // list album
    @Mock
    private ListSchemaNode listAlbum;
    private QName albumQName;

    // leaf name
    @Mock
    private LeafSchemaNode leafName;
    private QName nameQName;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        final SchemaContext schemaContext =
                YangParserTestUtils.parseYangFiles(TestRestconfUtils.loadFiles("/jukebox"));

        final QNameModule qNameModule = QNameModule.create(URI.create("http://example.com/ns/example-jukebox"),
            Revision.of("2015-04-04"));

        this.jukeboxQName = QName.create(qNameModule, "jukebox");
        this.playerQName = QName.create(qNameModule, "player");
        this.libraryQName = QName.create(qNameModule, "library");
        this.augmentedLibraryQName = QName.create(
                QNameModule.create(
                        URI.create("http://example.com/ns/augmented-jukebox"),
                        Revision.of("2016-05-05")),
                "augmented-library");
        this.albumQName = QName.create(qNameModule, "album");
        this.nameQName = QName.create(qNameModule, "name");

        Mockito.when(this.identifierContext.getSchemaContext()).thenReturn(schemaContext);
        Mockito.when(this.containerJukebox.getQName()).thenReturn(this.jukeboxQName);
        Mockito.when(this.identifierContext.getSchemaNode()).thenReturn(this.containerJukebox);

        Mockito.when(this.containerLibrary.getQName()).thenReturn(this.libraryQName);
        Mockito.when(this.containerJukebox.getDataChildByName(this.libraryQName)).thenReturn(this.containerLibrary);

        Mockito.when(this.augmentedContainerLibrary.getQName()).thenReturn(this.augmentedLibraryQName);
        Mockito.when(this.containerJukebox.getDataChildByName(this.augmentedLibraryQName))
                .thenReturn(this.augmentedContainerLibrary);

        Mockito.when(this.containerPlayer.getQName()).thenReturn(this.playerQName);
        Mockito.when(this.containerJukebox.getDataChildByName(this.playerQName)).thenReturn(this.containerPlayer);

        Mockito.when(this.listAlbum.getQName()).thenReturn(this.albumQName);
        Mockito.when(this.containerLibrary.getDataChildByName(this.albumQName)).thenReturn(this.listAlbum);

        Mockito.when(this.leafName.getQName()).thenReturn(this.nameQName);
        Mockito.when(this.listAlbum.getDataChildByName(this.nameQName)).thenReturn(this.leafName);
    }

    /**
     * Test parse fields parameter containing only one child selected.
     */
    @Test
    public void parseFieldsParameterSimplePathTest() {
        final String input = "library";
        final List<Set<QName>> parsedFields = ParserFieldsParameter.parseFieldsParameter(this.identifierContext, input);

        assertNotNull(parsedFields);
        assertEquals(1, parsedFields.size());
        assertEquals(1, parsedFields.get(0).size());
        assertTrue(parsedFields.get(0).contains(this.libraryQName));
    }

    /**
     * Test parse fields parameter containing two child nodes selected.
     */
    @Test
    public void parseFieldsParameterDoublePathTest() {
        final String input = "library;player";
        final List<Set<QName>> parsedFields = ParserFieldsParameter.parseFieldsParameter(this.identifierContext, input);

        assertNotNull(parsedFields);
        assertEquals(1, parsedFields.size());
        assertEquals(2, parsedFields.get(0).size());
        assertTrue(parsedFields.get(0).contains(this.libraryQName));
        assertTrue(parsedFields.get(0).contains(this.playerQName));
    }

    /**
     * Test parse fields parameter containing sub-children selected delimited by slash.
     */
    @Test
    public void parseFieldsParameterSubPathTest() {
        final String input = "library/album/name";
        final List<Set<QName>> parsedFields = ParserFieldsParameter.parseFieldsParameter(this.identifierContext, input);

        assertNotNull(parsedFields);
        assertEquals(3, parsedFields.size());

        assertEquals(1, parsedFields.get(0).size());
        assertTrue(parsedFields.get(0).contains(this.libraryQName));

        assertEquals(1, parsedFields.get(1).size());
        assertTrue(parsedFields.get(1).contains(this.albumQName));

        assertEquals(1, parsedFields.get(2).size());
        assertTrue(parsedFields.get(2).contains(this.nameQName));
    }

    /**
     * Test parse fields parameter containing sub-children selected delimited by parenthesis.
     */
    @Test
    public void parseFieldsParameterChildrenPathTest() {
        final String input = "library(album(name))";
        final List<Set<QName>> parsedFields = ParserFieldsParameter.parseFieldsParameter(this.identifierContext, input);

        assertNotNull(parsedFields);
        assertEquals(3, parsedFields.size());

        assertEquals(1, parsedFields.get(0).size());
        assertTrue(parsedFields.get(0).contains(this.libraryQName));

        assertEquals(1, parsedFields.get(1).size());
        assertTrue(parsedFields.get(1).contains(this.albumQName));

        assertEquals(1, parsedFields.get(2).size());
        assertTrue(parsedFields.get(2).contains(this.nameQName));
    }

    /**
     * Test parse fields parameter when augmentation with different namespace is used.
     */
    @Test
    public void parseFieldsParameterNamespaceTest() {
        final String input = "augmented-jukebox:augmented-library";
        final List<Set<QName>> parsedFields = ParserFieldsParameter.parseFieldsParameter(this.identifierContext, input);

        assertNotNull(parsedFields);
        assertEquals(1, parsedFields.size());

        assertEquals(1, parsedFields.get(0).size());
        assertTrue(parsedFields.get(0).contains(this.augmentedLibraryQName));
    }

    /**
     * Test parse fields parameter containing not expected character.
     */
    @Test
    public void parseFieldsParameterNotExpectedCharacterNegativeTest() {
        final String input = "*";

        try {
            ParserFieldsParameter.parseFieldsParameter(this.identifierContext, input);
            fail("Test should fail due to not expected character used in parameter input value");
        } catch (final RestconfDocumentedException e) {
            // Bad request
            assertEquals("Error type is not correct", ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals("Error tag is not correct", ErrorTag.INVALID_VALUE, e.getErrors().get(0).getErrorTag());
            assertEquals("Error status code is not correct", 400, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }

    /**
     * Test parse fields parameter with missing closing parenthesis.
     */
    @Test
    public void parseFieldsParameterMissingParenthesisNegativeTest() {
        final String input = "library(";

        try {
            ParserFieldsParameter.parseFieldsParameter(this.identifierContext, input);
            fail("Test should fail due to missing closing parenthesis");
        } catch (final RestconfDocumentedException e) {
            // Bad request
            assertEquals("Error type is not correct", ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals("Error tag is not correct", ErrorTag.INVALID_VALUE, e.getErrors().get(0).getErrorTag());
            assertEquals("Error status code is not correct", 400, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }

    /**
     * Test parse fields parameter when not existing child node selected.
     */
    @Test
    public void parseFieldsParameterMissingChildNodeNegativeTest() {
        final String input = "library(not-existing)";

        try {
            ParserFieldsParameter.parseFieldsParameter(this.identifierContext, input);
            fail("Test should fail due to missing child node in parent node");
        } catch (final RestconfDocumentedException e) {
            // Bad request
            assertEquals("Error type is not correct", ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals("Error tag is not correct", ErrorTag.INVALID_VALUE, e.getErrors().get(0).getErrorTag());
            assertEquals("Error status code is not correct", 400, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }

    /**
     * Test parse fields parameter with unexpected character after parenthesis.
     */
    @Test
    public void parseFieldsParameterAfterParenthesisNegativeTest() {
        final String input = "library(album);";

        try {
            ParserFieldsParameter.parseFieldsParameter(this.identifierContext, input);
            fail("Test should fail due to unexpected character after parenthesis");
        } catch (final RestconfDocumentedException e) {
            // Bad request
            assertEquals("Error type is not correct", ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals("Error tag is not correct", ErrorTag.INVALID_VALUE, e.getErrors().get(0).getErrorTag());
            assertEquals("Error status code is not correct", 400, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }

    /**
     * Test parse fields parameter with missing semicolon after parenthesis.
     */
    @Test
    public void parseFieldsParameterMissingSemicolonNegativeTest() {
        final String input = "library(album)player";

        try {
            ParserFieldsParameter.parseFieldsParameter(this.identifierContext, input);
            fail("Test should fail due to missing semicolon after parenthesis");
        } catch (final RestconfDocumentedException e) {
            // Bad request
            assertEquals("Error type is not correct", ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals("Error tag is not correct", ErrorTag.INVALID_VALUE, e.getErrors().get(0).getErrorTag());
            assertEquals("Error status code is not correct", 400, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }
}