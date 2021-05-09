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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorTag;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorType;
import org.opendaylight.restconf.nb.rfc8040.TestRestconfUtils;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.common.XMLNamespace;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.AugmentationIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.model.api.AugmentationSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.LeafListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

/**
 * Unit test for {@link ParserFieldsParameter}.
 */
@RunWith(MockitoJUnitRunner.class)
public class ParserFieldsParameterTest {

    @Mock
    private InstanceIdentifierContext<ContainerSchemaNode> identifierJukebox;

    @Mock
    private InstanceIdentifierContext<ContainerSchemaNode> identifierTestServices;

    private static final QNameModule Q_NAME_MODULE_JUKEBOX = QNameModule.create(
        XMLNamespace.of("http://example.com/ns/example-jukebox"), Revision.of("2015-04-04"));
    private static final QNameModule Q_NAME_MODULE_TEST_SERVICES = QNameModule.create(
        XMLNamespace.of("tests:test-services"), Revision.of("2019-03-25"));
    private static final QNameModule Q_NAME_MODULE_AUGMENTED_JUKEBOX = QNameModule.create(
        XMLNamespace.of("http://example.com/ns/augmented-jukebox"), Revision.of("2016-05-05"));

    // container jukebox
    @Mock
    private ContainerSchemaNode containerJukebox;
    private static final QName JUKEBOX_Q_NAME = QName.create(Q_NAME_MODULE_JUKEBOX, "jukebox");

    // container player
    @Mock
    private ContainerSchemaNode containerPlayer;
    private static final QName PLAYER_Q_NAME = QName.create(Q_NAME_MODULE_JUKEBOX, "player");

    // container library
    @Mock
    private ContainerSchemaNode containerLibrary;
    private static final QName LIBRARY_Q_NAME = QName.create(Q_NAME_MODULE_JUKEBOX, "library");

    // container augmented library
    @Mock
    private ContainerSchemaNode augmentedContainerLibrary;
    private static final QName AUGMENTED_LIBRARY_Q_NAME = QName.create(Q_NAME_MODULE_AUGMENTED_JUKEBOX,
            "augmented-library");

    // augmentation that contains speed leaf
    @Mock
    private AugmentationSchemaNode speedAugmentation;

    // leaf speed
    @Mock
    private LeafSchemaNode leafSpeed;
    private static final QName SPEED_Q_NAME = QName.create(Q_NAME_MODULE_AUGMENTED_JUKEBOX, "speed");

    // list album
    @Mock
    private ListSchemaNode listAlbum;
    private static final QName ALBUM_Q_NAME = QName.create(Q_NAME_MODULE_JUKEBOX, "album");

    // leaf name
    @Mock
    private LeafSchemaNode leafName;
    private static final QName NAME_Q_NAME = QName.create(Q_NAME_MODULE_JUKEBOX, "name");

    // container test data
    @Mock
    private ContainerSchemaNode containerTestData;
    private static final QName TEST_DATA_Q_NAME = QName.create(Q_NAME_MODULE_TEST_SERVICES, "test-data");

    // list services
    @Mock
    private ListSchemaNode listServices;
    private static final QName SERVICES_Q_NAME = QName.create(Q_NAME_MODULE_TEST_SERVICES, "services");

    // leaf type-of-service
    @Mock
    private LeafSchemaNode leafTypeOfService;
    private static final QName TYPE_OF_SERVICE_Q_NAME = QName.create(Q_NAME_MODULE_TEST_SERVICES, "type-of-service");

    // list instance
    @Mock
    private ListSchemaNode listInstance;
    private static final QName INSTANCE_Q_NAME = QName.create(Q_NAME_MODULE_TEST_SERVICES, "instance");

    // leaf instance-name
    @Mock
    private LeafSchemaNode leafInstanceName;
    private static final QName INSTANCE_NAME_Q_NAME = QName.create(Q_NAME_MODULE_TEST_SERVICES, "instance-name");

    // leaf provider
    @Mock
    private LeafSchemaNode leafProvider;
    private static final QName PROVIDER_Q_NAME = QName.create(Q_NAME_MODULE_TEST_SERVICES, "provider");

    // container next-data
    @Mock
    private ContainerSchemaNode containerNextData;
    private static final QName NEXT_DATA_Q_NAME = QName.create(Q_NAME_MODULE_TEST_SERVICES, "next-data");

    // leaf next-service
    @Mock
    private LeafSchemaNode leafNextService;
    private static final QName NEXT_SERVICE_Q_NAME = QName.create(Q_NAME_MODULE_TEST_SERVICES, "next-service");

    // leaf-list protocols
    @Mock
    private LeafListSchemaNode leafListProtocols;
    private static final QName PROTOCOLS_Q_NAME = QName.create(Q_NAME_MODULE_TEST_SERVICES, "protocols");

    @Before
    public void setUp() throws Exception {
        final EffectiveModelContext schemaContextJukebox =
                YangParserTestUtils.parseYangFiles(TestRestconfUtils.loadFiles("/jukebox"));
        initJukeboxSchemaNodes(schemaContextJukebox);

        final EffectiveModelContext schemaContextTestServices =
                YangParserTestUtils.parseYangFiles(TestRestconfUtils.loadFiles("/test-services"));
        initTestServicesSchemaNodes(schemaContextTestServices);
    }

    private void initJukeboxSchemaNodes(final EffectiveModelContext schemaContext) {
        when(identifierJukebox.getSchemaContext()).thenReturn(schemaContext);
        when(containerJukebox.getQName()).thenReturn(JUKEBOX_Q_NAME);
        when(identifierJukebox.getSchemaNode()).thenReturn(containerJukebox);

        when(containerLibrary.getQName()).thenReturn(LIBRARY_Q_NAME);
        when(containerJukebox.dataChildByName(LIBRARY_Q_NAME)).thenReturn(containerLibrary);

        when(augmentedContainerLibrary.getQName()).thenReturn(AUGMENTED_LIBRARY_Q_NAME);
        when(containerJukebox.dataChildByName(AUGMENTED_LIBRARY_Q_NAME))
                .thenReturn(augmentedContainerLibrary);

        when(containerPlayer.getQName()).thenReturn(PLAYER_Q_NAME);
        when(containerJukebox.dataChildByName(PLAYER_Q_NAME)).thenReturn(containerPlayer);

        when(listAlbum.getQName()).thenReturn(ALBUM_Q_NAME);
        when(containerLibrary.dataChildByName(ALBUM_Q_NAME)).thenReturn(listAlbum);

        when(leafName.getQName()).thenReturn(NAME_Q_NAME);
        when(listAlbum.dataChildByName(NAME_Q_NAME)).thenReturn(leafName);

        when(leafSpeed.getQName()).thenReturn(SPEED_Q_NAME);
        when(leafSpeed.isAugmenting()).thenReturn(true);
        when(containerPlayer.dataChildByName(SPEED_Q_NAME)).thenReturn(leafSpeed);
        when(containerPlayer.getDataChildByName(SPEED_Q_NAME)).thenReturn(leafSpeed);
        doReturn(Collections.singletonList(leafSpeed)).when(speedAugmentation).getChildNodes();
        doReturn(Collections.singleton(speedAugmentation)).when(containerPlayer).getAvailableAugmentations();
        when(speedAugmentation.findDataChildByName(SPEED_Q_NAME)).thenReturn(Optional.of(leafSpeed));
    }

    private void initTestServicesSchemaNodes(final EffectiveModelContext schemaContext) {
        when(identifierTestServices.getSchemaContext()).thenReturn(schemaContext);
        when(containerTestData.getQName()).thenReturn(TEST_DATA_Q_NAME);
        when(identifierTestServices.getSchemaNode()).thenReturn(containerTestData);

        when(listServices.getQName()).thenReturn(SERVICES_Q_NAME);
        when(containerTestData.dataChildByName(SERVICES_Q_NAME)).thenReturn(listServices);

        when(leafListProtocols.getQName()).thenReturn(PROTOCOLS_Q_NAME);
        when(containerTestData.dataChildByName(PROTOCOLS_Q_NAME)).thenReturn(leafListProtocols);

        when(leafTypeOfService.getQName()).thenReturn(TYPE_OF_SERVICE_Q_NAME);
        when(listServices.dataChildByName(TYPE_OF_SERVICE_Q_NAME)).thenReturn(leafTypeOfService);

        when(listInstance.getQName()).thenReturn(INSTANCE_Q_NAME);
        when(listServices.dataChildByName(INSTANCE_Q_NAME)).thenReturn(listInstance);

        when(leafInstanceName.getQName()).thenReturn(INSTANCE_NAME_Q_NAME);
        when(listInstance.dataChildByName(INSTANCE_NAME_Q_NAME)).thenReturn(leafInstanceName);

        when(leafProvider.getQName()).thenReturn(PROVIDER_Q_NAME);
        when(listInstance.dataChildByName(PROVIDER_Q_NAME)).thenReturn(leafProvider);

        when(containerNextData.getQName()).thenReturn(NEXT_DATA_Q_NAME);
        when(listServices.dataChildByName(NEXT_DATA_Q_NAME)).thenReturn(containerNextData);

        when(leafNextService.getQName()).thenReturn(NEXT_SERVICE_Q_NAME);
        when(containerNextData.dataChildByName(NEXT_SERVICE_Q_NAME)).thenReturn(leafNextService);
    }

    /**
     * Test parse fields parameter containing only one child selected.
     */
    @Test
    public void parseFieldsParameterSimplePathTest() {
        final String input = "library";
        final List<Set<QName>> parsedFields = ParserFieldsParameter.parseFieldsParameter(identifierJukebox, input);

        assertNotNull(parsedFields);
        assertEquals(1, parsedFields.size());
        assertEquals(1, parsedFields.get(0).size());
        assertTrue(parsedFields.get(0).contains(LIBRARY_Q_NAME));
    }

    /**
     * Test parse fields parameter containing two child nodes selected.
     */
    @Test
    public void parseFieldsParameterDoublePathTest() {
        final String input = "library;player";
        final List<Set<QName>> parsedFields = ParserFieldsParameter.parseFieldsParameter(identifierJukebox, input);

        assertNotNull(parsedFields);
        assertEquals(1, parsedFields.size());
        assertEquals(2, parsedFields.get(0).size());
        assertTrue(parsedFields.get(0).contains(LIBRARY_Q_NAME));
        assertTrue(parsedFields.get(0).contains(PLAYER_Q_NAME));
    }

    /**
     * Test parse fields parameter containing sub-children selected delimited by slash.
     */
    @Test
    public void parseFieldsParameterSubPathTest() {
        final String input = "library/album/name";
        final List<Set<QName>> parsedFields = ParserFieldsParameter.parseFieldsParameter(identifierJukebox, input);

        assertNotNull(parsedFields);
        assertEquals(3, parsedFields.size());

        assertEquals(1, parsedFields.get(0).size());
        assertTrue(parsedFields.get(0).contains(LIBRARY_Q_NAME));

        assertEquals(1, parsedFields.get(1).size());
        assertTrue(parsedFields.get(1).contains(ALBUM_Q_NAME));

        assertEquals(1, parsedFields.get(2).size());
        assertTrue(parsedFields.get(2).contains(NAME_Q_NAME));
    }

    /**
     * Test parse fields parameter containing sub-children selected delimited by parenthesis.
     */
    @Test
    public void parseFieldsParameterChildrenPathTest() {
        final String input = "library(album(name))";
        final List<Set<QName>> parsedFields = ParserFieldsParameter.parseFieldsParameter(identifierJukebox, input);

        assertNotNull(parsedFields);
        assertEquals(3, parsedFields.size());

        assertEquals(1, parsedFields.get(0).size());
        assertTrue(parsedFields.get(0).contains(LIBRARY_Q_NAME));

        assertEquals(1, parsedFields.get(1).size());
        assertTrue(parsedFields.get(1).contains(ALBUM_Q_NAME));

        assertEquals(1, parsedFields.get(2).size());
        assertTrue(parsedFields.get(2).contains(NAME_Q_NAME));
    }

    /**
     * Test parse fields parameter when augmentation with different namespace is used.
     */
    @Test
    public void parseFieldsParameterNamespaceTest() {
        final String input = "augmented-jukebox:augmented-library";
        final List<Set<QName>> parsedFields = ParserFieldsParameter.parseFieldsParameter(identifierJukebox, input);

        assertNotNull(parsedFields);
        assertEquals(1, parsedFields.size());

        assertEquals(1, parsedFields.get(0).size());
        assertTrue(parsedFields.get(0).contains(AUGMENTED_LIBRARY_Q_NAME));
    }

    /**
     * Testing of fields parameter parsing when multiple nodes are wrapped in brackets and these nodes are not
     * direct children of parent node - multiple children which are constructed using '/'.
     */
    @Test
    public void parseFieldsParameterWithMultipleChildrenTest1() {
        final String input = "services(type-of-service;instance/instance-name;instance/provider)";
        final List<Set<QName>> parsedFields = ParserFieldsParameter.parseFieldsParameter(identifierTestServices, input);

        assertNotNull(parsedFields);
        assertEquals(parsedFields.size(), 3);

        assertEquals(parsedFields.get(0).size(), 1);
        assertTrue(parsedFields.get(0).contains(SERVICES_Q_NAME));

        assertEquals(parsedFields.get(1).size(), 2);
        assertTrue(parsedFields.get(1).containsAll(Sets.newHashSet(TYPE_OF_SERVICE_Q_NAME, INSTANCE_Q_NAME)));

        assertEquals(parsedFields.get(2).size(), 2);
        assertTrue(parsedFields.get(2).containsAll(Sets.newHashSet(INSTANCE_NAME_Q_NAME, PROVIDER_Q_NAME)));
    }

    /**
     * Testing of fields parameter parsing when multiple nodes are wrapped in brackets and these nodes are not
     * direct children of parent node - one of children nodes is typed using brackets, other is constructed using '/'.
     */
    @Test
    public void parseFieldsParameterWithMultipleChildrenTest2() {
        final String input = "services(type-of-service;instance(instance-name;provider))";
        final List<Set<QName>> parsedFields = ParserFieldsParameter.parseFieldsParameter(identifierTestServices, input);

        assertNotNull(parsedFields);
        assertEquals(parsedFields.size(), 3);

        assertEquals(parsedFields.get(0).size(), 1);
        assertTrue(parsedFields.get(0).contains(SERVICES_Q_NAME));

        assertEquals(parsedFields.get(1).size(), 2);
        assertTrue(parsedFields.get(1).containsAll(Sets.newHashSet(TYPE_OF_SERVICE_Q_NAME, INSTANCE_Q_NAME)));

        assertEquals(parsedFields.get(2).size(), 2);
        assertTrue(parsedFields.get(2).containsAll(Sets.newHashSet(INSTANCE_NAME_Q_NAME, PROVIDER_Q_NAME)));
    }

    /**
     * Testing of fields parameter parsing when multiple nodes are wrapped in brackets and these nodes are not
     * direct children of parent node - multiple children with different parent nodes.
     */
    @Test
    public void parseFieldsParameterWithMultipleChildrenTest3() {
        final String input = "services(instance/instance-name;type-of-service;next-data/next-service)";
        final List<Set<QName>> parsedFields = ParserFieldsParameter.parseFieldsParameter(identifierTestServices, input);

        assertNotNull(parsedFields);
        assertEquals(parsedFields.size(), 3);

        assertEquals(parsedFields.get(0).size(), 1);
        assertTrue(parsedFields.get(0).contains(SERVICES_Q_NAME));

        assertEquals(parsedFields.get(1).size(), 3);
        assertTrue(parsedFields.get(1).containsAll(
                Sets.newHashSet(TYPE_OF_SERVICE_Q_NAME, INSTANCE_Q_NAME, NEXT_DATA_Q_NAME)));

        assertEquals(parsedFields.get(2).size(), 2);
        assertTrue(parsedFields.get(2).containsAll(
                Sets.newHashSet(INSTANCE_NAME_Q_NAME, NEXT_SERVICE_Q_NAME)));
    }

    /**
     * Test parse fields parameter containing not expected character.
     */
    @Test
    public void parseFieldsParameterNotExpectedCharacterNegativeTest() {
        final String input = "*";

        try {
            ParserFieldsParameter.parseFieldsParameter(this.identifierJukebox, input);
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
            ParserFieldsParameter.parseFieldsParameter(this.identifierJukebox, input);
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
            ParserFieldsParameter.parseFieldsParameter(this.identifierJukebox, input);
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
            ParserFieldsParameter.parseFieldsParameter(this.identifierJukebox, input);
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
            ParserFieldsParameter.parseFieldsParameter(this.identifierJukebox, input);
            fail("Test should fail due to missing semicolon after parenthesis");
        } catch (final RestconfDocumentedException e) {
            // Bad request
            assertEquals("Error type is not correct", ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals("Error tag is not correct", ErrorTag.INVALID_VALUE, e.getErrors().get(0).getErrorTag());
            assertEquals("Error status code is not correct", 400, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }

    @Test
    public void parseTopLevelContainerToPathTest() {
        final String input = "library";
        final List<YangInstanceIdentifier> parsedFields = ParserFieldsParameter.parseFieldsPaths(
                identifierJukebox, input);

        assertNotNull(parsedFields);
        assertEquals(1, parsedFields.size());
        final List<PathArgument> pathArguments = parsedFields.get(0).getPathArguments();
        assertEquals(1, pathArguments.size());
        assertEquals(LIBRARY_Q_NAME, pathArguments.get(0).getNodeType());
    }

    @Test
    public void parseTwoTopLevelContainersToPathsTest() {
        final String input = "library;player";
        final List<YangInstanceIdentifier> parsedFields = ParserFieldsParameter.parseFieldsPaths(
                identifierJukebox, input);

        assertNotNull(parsedFields);
        assertEquals(2, parsedFields.size());

        final Optional<YangInstanceIdentifier> libraryPath = findPath(parsedFields, LIBRARY_Q_NAME);
        assertTrue(libraryPath.isPresent());
        assertEquals(1, libraryPath.get().getPathArguments().size());

        final Optional<YangInstanceIdentifier> playerPath = findPath(parsedFields, PLAYER_Q_NAME);
        assertTrue(playerPath.isPresent());
        assertEquals(1, libraryPath.get().getPathArguments().size());
    }

    @Test
    public void parseNestedLeafToPathTest() {
        final String input = "library/album/name";
        final List<YangInstanceIdentifier> parsedFields = ParserFieldsParameter.parseFieldsPaths(
                identifierJukebox, input);

        assertEquals(1, parsedFields.size());
        final List<PathArgument> pathArguments = parsedFields.get(0).getPathArguments();
        assertEquals(3, pathArguments.size());

        assertEquals(LIBRARY_Q_NAME, pathArguments.get(0).getNodeType());
        assertEquals(ALBUM_Q_NAME, pathArguments.get(1).getNodeType());
        assertEquals(NAME_Q_NAME, pathArguments.get(2).getNodeType());
    }

    @Test
    public void parseAugmentedLeafToPathTest() {
        final String input = "player/augmented-jukebox:speed";
        final List<YangInstanceIdentifier> parsedFields = ParserFieldsParameter.parseFieldsPaths(
                identifierJukebox, input);

        assertEquals(1, parsedFields.size());
        final List<PathArgument> pathArguments = parsedFields.get(0).getPathArguments();

        assertEquals(3, pathArguments.size());
        assertEquals(PLAYER_Q_NAME, pathArguments.get(0).getNodeType());
        assertTrue(pathArguments.get(1) instanceof AugmentationIdentifier);
        assertEquals(SPEED_Q_NAME, pathArguments.get(2).getNodeType());
    }

    @Test
    public void parseMultipleFieldsOnDifferentLevelsToPathsTest() {
        final String input = "services(type-of-service;instance/instance-name;instance/provider)";
        final List<YangInstanceIdentifier> parsedFields = ParserFieldsParameter.parseFieldsPaths(
                identifierTestServices, input);

        assertEquals(3, parsedFields.size());

        final Optional<YangInstanceIdentifier> tosPath = findPath(parsedFields, TYPE_OF_SERVICE_Q_NAME);
        assertTrue(tosPath.isPresent());
        assertEquals(2, tosPath.get().getPathArguments().size());

        final Optional<YangInstanceIdentifier> instanceNamePath = findPath(parsedFields, INSTANCE_NAME_Q_NAME);
        assertTrue(instanceNamePath.isPresent());
        assertEquals(3, instanceNamePath.get().getPathArguments().size());

        final Optional<YangInstanceIdentifier> providerPath = findPath(parsedFields, PROVIDER_Q_NAME);
        assertTrue(providerPath.isPresent());
        assertEquals(3, providerPath.get().getPathArguments().size());
    }

    @Test
    public void parseListFieldUnderListToPathTest() {
        final String input = "services/instance";
        final List<YangInstanceIdentifier> parsedFields = ParserFieldsParameter.parseFieldsPaths(
                identifierTestServices, input);

        assertEquals(1, parsedFields.size());
        final List<PathArgument> pathArguments = parsedFields.get(0).getPathArguments();
        assertEquals(2, pathArguments.size());

        assertEquals(SERVICES_Q_NAME, pathArguments.get(0).getNodeType());
        assertTrue(pathArguments.get(0) instanceof NodeIdentifier);
        assertEquals(INSTANCE_Q_NAME, pathArguments.get(1).getNodeType());
        assertTrue(pathArguments.get(1) instanceof NodeIdentifier);
    }

    @Test
    public void parseLeafListFieldToPathTest() {
        final String input = "protocols";
        final List<YangInstanceIdentifier> parsedFields = ParserFieldsParameter.parseFieldsPaths(
                identifierTestServices, input);

        assertEquals(1, parsedFields.size());
        final List<PathArgument> pathArguments = parsedFields.get(0).getPathArguments();
        assertEquals(1, pathArguments.size());
        assertTrue(pathArguments.get(0) instanceof NodeIdentifier);
        assertEquals(PROTOCOLS_Q_NAME, pathArguments.get(0).getNodeType());
    }

    private static Optional<YangInstanceIdentifier> findPath(final List<YangInstanceIdentifier> paths,
                                                             final QName lastPathArg) {
        return paths.stream()
                .filter(path -> lastPathArg.equals(path.getLastPathArgument().getNodeType()))
                .findAny();
    }
}