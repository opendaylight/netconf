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
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import java.text.ParseException;
import java.util.List;
import java.util.Optional;
import org.eclipse.jdt.annotation.NonNull;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.FieldsParam;
import org.opendaylight.restconf.nb.rfc8040.TestRestconfUtils;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.common.XMLNamespace;
import org.opendaylight.yangtools.yang.model.api.AugmentationSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.LeafListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public abstract class AbstractFieldsTranslatorTest<T> {
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
    protected static final QName PLAYER_Q_NAME = QName.create(Q_NAME_MODULE_JUKEBOX, "player");

    // list artist
    @Mock
    private ListSchemaNode libraryArtist;
    protected static final QName ARTIST_Q_NAME = QName.create(Q_NAME_MODULE_JUKEBOX, "artist");

    // container library
    @Mock
    private ContainerSchemaNode containerLibrary;
    protected static final QName LIBRARY_Q_NAME = QName.create(Q_NAME_MODULE_JUKEBOX, "library");

    // container augmented library
    @Mock
    private ContainerSchemaNode augmentedContainerLibrary;
    protected static final QName AUGMENTED_LIBRARY_Q_NAME = QName.create(Q_NAME_MODULE_AUGMENTED_JUKEBOX,
            "augmented-library");

    // augmentation that contains speed leaf
    @Mock
    private AugmentationSchemaNode speedAugmentation;

    // leaf speed
    @Mock
    private LeafSchemaNode leafSpeed;
    protected static final QName SPEED_Q_NAME = QName.create(Q_NAME_MODULE_AUGMENTED_JUKEBOX, "speed");

    // list album
    @Mock
    private ListSchemaNode listAlbum;
    public static final QName ALBUM_Q_NAME = QName.create(Q_NAME_MODULE_JUKEBOX, "album");

    // leaf name
    @Mock
    private LeafSchemaNode leafName;
    protected static final QName NAME_Q_NAME = QName.create(Q_NAME_MODULE_JUKEBOX, "name");

    // container test data
    @Mock
    private ContainerSchemaNode containerTestData;
    private static final QName TEST_DATA_Q_NAME = QName.create(Q_NAME_MODULE_TEST_SERVICES, "test-data");

    // list services
    @Mock
    private ListSchemaNode listServices;
    protected static final QName SERVICES_Q_NAME = QName.create(Q_NAME_MODULE_TEST_SERVICES, "services");

    // leaf type-of-service
    @Mock
    private LeafSchemaNode leafTypeOfService;
    protected static final QName TYPE_OF_SERVICE_Q_NAME = QName.create(Q_NAME_MODULE_TEST_SERVICES, "type-of-service");

    // list instance
    @Mock
    private ListSchemaNode listInstance;
    protected static final QName INSTANCE_Q_NAME = QName.create(Q_NAME_MODULE_TEST_SERVICES, "instance");

    // leaf instance-name
    @Mock
    private LeafSchemaNode leafInstanceName;
    protected static final QName INSTANCE_NAME_Q_NAME = QName.create(Q_NAME_MODULE_TEST_SERVICES, "instance-name");

    // leaf provider
    @Mock
    private LeafSchemaNode leafProvider;
    protected static final QName PROVIDER_Q_NAME = QName.create(Q_NAME_MODULE_TEST_SERVICES, "provider");

    // container next-data
    @Mock
    private ContainerSchemaNode containerNextData;
    protected static final QName NEXT_DATA_Q_NAME = QName.create(Q_NAME_MODULE_TEST_SERVICES, "next-data");

    // leaf next-service
    @Mock
    private LeafSchemaNode leafNextService;
    protected static final QName NEXT_SERVICE_Q_NAME = QName.create(Q_NAME_MODULE_TEST_SERVICES, "next-service");

    // leaf-list protocols
    @Mock
    private LeafListSchemaNode leafListProtocols;
    protected static final QName PROTOCOLS_Q_NAME = QName.create(Q_NAME_MODULE_TEST_SERVICES, "protocols");

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

        when(libraryArtist.getKeyDefinition()).thenReturn(List.of(NAME_Q_NAME));
        when(libraryArtist.getQName()).thenReturn(ARTIST_Q_NAME);
        when(containerLibrary.dataChildByName(ARTIST_Q_NAME)).thenReturn(libraryArtist);
        when(libraryArtist.dataChildByName(NAME_Q_NAME)).thenReturn(leafName);

        when(leafName.getQName()).thenReturn(NAME_Q_NAME);
        when(listAlbum.dataChildByName(NAME_Q_NAME)).thenReturn(leafName);

        when(leafSpeed.getQName()).thenReturn(SPEED_Q_NAME);
        when(leafSpeed.isAugmenting()).thenReturn(true);
        when(containerPlayer.dataChildByName(SPEED_Q_NAME)).thenReturn(leafSpeed);
        when(containerPlayer.getDataChildByName(SPEED_Q_NAME)).thenReturn(leafSpeed);
        doReturn(List.of(leafSpeed)).when(speedAugmentation).getChildNodes();
        doReturn(List.of(speedAugmentation)).when(containerPlayer).getAvailableAugmentations();
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

    protected abstract List<T> translateFields(InstanceIdentifierContext<?> context, FieldsParam fields);

    /**
     * Test parse fields parameter containing only one child selected.
     */
    @Test
    public void testSimplePath() {
        final var result = translateFields(identifierJukebox, assertFields("library"));
        assertNotNull(result);
        assertSimplePath(result);
    }

    protected abstract void assertSimplePath(@NonNull List<T> result);

    /**
     * Test parse fields parameter containing two child nodes selected.
     */
    @Test
    public void testDoublePath() {
        final var result = translateFields(identifierJukebox, assertFields("library;player"));
        assertNotNull(result);
        assertDoublePath(result);
    }

    protected abstract void assertDoublePath(@NonNull List<T> result);

    /**
     * Test parse fields parameter containing sub-children selected delimited by slash.
     */
    @Test
    public void testSubPath() {
        final var result = translateFields(identifierJukebox, assertFields("library/album/name"));
        assertNotNull(result);
        assertSubPath(result);
    }

    protected abstract void assertSubPath(@NonNull List<T> result);

    /**
     * Test parse fields parameter containing sub-children selected delimited by parenthesis.
     */
    @Test
    public void testChildrenPath() {
        final var result = translateFields(identifierJukebox, assertFields("library(album(name))"));
        assertNotNull(result);
        assertChildrenPath(result);
    }

    protected abstract void assertChildrenPath(@NonNull List<T> result);

    /**
     * Test parse fields parameter when augmentation with different namespace is used.
     */
    @Test
    public void testNamespace() {
        final var result = translateFields(identifierJukebox, assertFields("augmented-jukebox:augmented-library"));
        assertNotNull(result);
        assertNamespace(result);
    }

    protected abstract void assertNamespace(@NonNull List<T> result);

    /**
     * Testing of fields parameter parsing when multiple nodes are wrapped in brackets and these nodes are not
     * direct children of parent node - multiple children which are constructed using '/'.
     */
    @Test
    public void testMultipleChildren1() {
        final var result = translateFields(identifierTestServices,
            assertFields("services(type-of-service;instance/instance-name;instance/provider)"));
        assertNotNull(result);
        assertMultipleChildren1(result);
    }

    protected abstract void assertMultipleChildren1(@NonNull List<T> result);

    /**
     * Testing of fields parameter parsing when multiple nodes are wrapped in brackets and these nodes are not
     * direct children of parent node - one of children nodes is typed using brackets, other is constructed using '/'.
     */
    @Test
    public void testMultipleChildren2() {
        final var result = translateFields(identifierTestServices,
            assertFields("services(type-of-service;instance(instance-name;provider))"));
        assertNotNull(result);
        assertMultipleChildren2(result);
    }

    protected abstract void assertMultipleChildren2(@NonNull List<T> result);

    /**
     * Testing of fields parameter parsing when multiple nodes are wrapped in brackets and these nodes are not
     * direct children of parent node - multiple children with different parent nodes.
     */
    @Test
    public void testMultipleChildren3() {
        final var result = translateFields(identifierTestServices,
            assertFields("services(instance/instance-name;type-of-service;next-data/next-service)"));
        assertNotNull(result);
        assertMultipleChildren3(result);
    }

    protected abstract void assertMultipleChildren3(@NonNull List<T> result);

    @Test
    public void testMultipleChildren4() {
        final var result = translateFields(identifierTestServices,
                assertFields("services(type-of-service;instance(instance-name;provider);next-data(next-service))"));
        assertNotNull(result);
        assertMultipleChildren4(result);
    }

    protected abstract void assertMultipleChildren4(@NonNull List<T> result);

    @Test
    public void testMultipleChildren5() {
        final var result = translateFields(identifierTestServices,
                assertFields("services(type-of-service;instance(instance-name;provider);next-data/next-service)"));
        assertNotNull(result);
        assertMultipleChildren5(result);
    }

    protected abstract void assertMultipleChildren5(@NonNull List<T> result);

    @Test
    public void testAugmentedChild() {
        final var result = translateFields(identifierJukebox, assertFields("player/augmented-jukebox:speed"));
        assertNotNull(result);
        assertAugmentedChild(result);
    }

    protected abstract void assertAugmentedChild(@NonNull List<T> result);

    @Test
    public void testListFieldUnderList() {
        final var result = translateFields(identifierTestServices, assertFields("services/instance"));
        assertNotNull(result);
        assertListFieldUnderList(result);
    }

    protected abstract void assertListFieldUnderList(@NonNull List<T> result);

    @Test
    public void testLeafList() {
        final var result = translateFields(identifierTestServices, assertFields("protocols"));
        assertNotNull(result);
        assertLeafList(result);
    }

    @Test
    public void testKeyedList() {
        final var result = translateFields(identifierJukebox, assertFields("library/artist(name)"));
        assertNotNull(result);
        assertKeyedList(result);
    }

    protected abstract void assertKeyedList(List<T> result);

    protected abstract void assertLeafList(@NonNull List<T> result);

    /**
     * Test parse fields parameter when not existing child node selected.
     */
    @Test
    public void testMissingChildSchema() throws ParseException {
        final FieldsParam input = FieldsParam.parse("library(not-existing)");

        final RestconfDocumentedException ex = assertThrows(RestconfDocumentedException.class,
            () -> translateFields(identifierJukebox, input));
        // Bad request
        assertEquals(ErrorType.PROTOCOL, ex.getErrors().get(0).getErrorType());
        assertEquals(ErrorTag.INVALID_VALUE, ex.getErrors().get(0).getErrorTag());
    }

    private static FieldsParam assertFields(final String input) {
        try {
            return FieldsParam.parse(input);
        } catch (ParseException e) {
            throw new AssertionError(e);
        }
    }
}