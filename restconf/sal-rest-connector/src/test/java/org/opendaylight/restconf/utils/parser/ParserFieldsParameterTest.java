/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.utils.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.rest.common.TestRestconfUtils;
import org.opendaylight.netconf.sal.restconf.impl.InstanceIdentifierContext;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorTag;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

/**
 * Unit test for {@link ParserFieldsParameter}
 */
public class ParserFieldsParameterTest {

    private SchemaContext schemaContext;

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
    private QName librayQName;

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

        final QNameModule qNameModule = QNameModule.create(URI.create("http://example.com/ns/example-jukebox"),
                new SimpleDateFormat("yyyy-MM-dd").parse("2015-04-04"));

        jukeboxQName = QName.create(qNameModule, "jukebox");
        playerQName = QName.create(qNameModule, "player");
        librayQName = QName.create(qNameModule, "library");
        albumQName = QName.create(qNameModule, "album");
        nameQName = QName.create(qNameModule, "name");

        schemaContext = TestRestconfUtils.loadSchemaContext("/jukebox");

        Mockito.when(identifierContext.getSchemaContext()).thenReturn(schemaContext);
        Mockito.when(containerJukebox.getQName()).thenReturn(jukeboxQName);
        Mockito.when(identifierContext.getSchemaNode()).thenReturn(containerJukebox);

        Mockito.when(containerLibrary.getQName()).thenReturn(librayQName);
        Mockito.when(containerJukebox.getDataChildByName(librayQName)).thenReturn(containerLibrary);

        Mockito.when(containerPlayer.getQName()).thenReturn(playerQName);
        Mockito.when(containerJukebox.getDataChildByName(playerQName)).thenReturn(containerPlayer);

        Mockito.when(listAlbum.getQName()).thenReturn(albumQName);
        Mockito.when(containerLibrary.getDataChildByName(albumQName)).thenReturn(listAlbum);

        Mockito.when(leafName.getQName()).thenReturn(nameQName);
        Mockito.when(listAlbum.getDataChildByName(nameQName)).thenReturn(leafName);
    }

    /**
     * Test parse fields parameter containing only one child selected
     */
    @Test
    public void parseFieldsParameterSimplePathTest() {
        final String input = "library";
        final List<Set<QName>> parsedFields = ParserFieldsParameter.parseFieldsParameter(identifierContext, input);

        assertNotNull(parsedFields);
        assertEquals(1, parsedFields.size());
        assertEquals(1, parsedFields.get(0).size());
        assertTrue(parsedFields.get(0).contains(librayQName));
    }

    /**
     * Test parse fields parameter containing two child nodes selected
     */
    @Test
    public void parseFieldsParameterDoublePathTest() {
        final String input = "library;player";
        final List<Set<QName>> parsedFields = ParserFieldsParameter.parseFieldsParameter(identifierContext, input);

        assertNotNull(parsedFields);
        assertEquals(1, parsedFields.size());
        assertEquals(2, parsedFields.get(0).size());
        assertTrue(parsedFields.get(0).contains(librayQName));
        assertTrue(parsedFields.get(0).contains(playerQName));
    }

    /**
     * Test parse fields parameter containing sub-children selected delimited by slash
     */
    @Test
    public void parseFieldsParameterSubPathTest() {
        final String input = "library/album/name";
        final List<Set<QName>> parsedFields = ParserFieldsParameter.parseFieldsParameter(identifierContext, input);

        assertNotNull(parsedFields);
        assertEquals(3, parsedFields.size());

        assertEquals(1, parsedFields.get(0).size());
        assertTrue(parsedFields.get(0).contains(librayQName));

        assertEquals(1, parsedFields.get(1).size());
        assertTrue(parsedFields.get(1).contains(albumQName));

        assertEquals(1, parsedFields.get(2).size());
        assertTrue(parsedFields.get(2).contains(nameQName));
    }

    /**
     * Test parse fields parameter containing sub-children selected delimited by parenthesis
     */
    @Test
    public void parseFieldsParameterChildrenPathTest() {
        final String input = "library(album(name))";
        final List<Set<QName>> parsedFields = ParserFieldsParameter.parseFieldsParameter(identifierContext, input);

        assertNotNull(parsedFields);
        assertEquals(3, parsedFields.size());

        assertEquals(1, parsedFields.get(0).size());
        assertTrue(parsedFields.get(0).contains(librayQName));

        assertEquals(1, parsedFields.get(1).size());
        assertTrue(parsedFields.get(1).contains(albumQName));

        assertEquals(1, parsedFields.get(2).size());
        assertTrue(parsedFields.get(2).contains(nameQName));
    }

    /**
     * Test parse fields parameter containing not expected character
     */
    @Test
    public void parseFieldsParameterNoeExpectedCharacterNegativeTest() {
        final String input = "*";

        try {
            ParserFieldsParameter.parseFieldsParameter(identifierContext, input);
            fail("Test should fail due to not expected character used in parameter input value");
        } catch (final RestconfDocumentedException e) {
            // Bad request
            assertEquals("Error type is not correct", ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals("Error tag is not correct", ErrorTag.INVALID_VALUE, e.getErrors().get(0).getErrorTag());
            assertEquals("Error status code is not correct", 400, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }

    /**
     * Test parse fields parameter with missing closing parenthesis
     */
    @Test
    public void parseFieldsParameterMissingParenthesisNegativeTest() {
        final String input = "library(";

        try {
            ParserFieldsParameter.parseFieldsParameter(identifierContext, input);
            fail("Test should fail due to missing closing parenthesis");
        } catch (final RestconfDocumentedException e) {
            // Bad request
            assertEquals("Error type is not correct", ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals("Error tag is not correct", ErrorTag.INVALID_VALUE, e.getErrors().get(0).getErrorTag());
            assertEquals("Error status code is not correct", 400, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }

    /**
     * Test parse fields parameter not existing child node selected
     */
    @Test
    public void parseFieldsParameterMissingChildNodeNegativeTest() {
        final String input = "library(not-existing)";

        try {
            ParserFieldsParameter.parseFieldsParameter(identifierContext, input);
            fail("Test should fail due to missing child node in parent node");
        } catch (final RestconfDocumentedException e) {
            // Bad request
            assertEquals("Error type is not correct", ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals("Error tag is not correct", ErrorTag.INVALID_VALUE, e.getErrors().get(0).getErrorTag());
            assertEquals("Error status code is not correct", 400, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }
}