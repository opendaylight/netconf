/*
 * Copyright © 2020 FRINX s.r.o. and others.  All rights reserved.
 * Copyright © 2021 PANTHEON.tech, s.r.o.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.restconf.server.api.testlib.AbstractFieldsTranslatorTest;
import org.opendaylight.yangtools.databind.RequestException;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;

/**
 * Unit test for {@link FieldsParamParser}.
 */
@ExtendWith(MockitoExtension.class)
class NetconfFieldsParamTest extends AbstractFieldsTranslatorTest {

    @BeforeEach
    void setUp() {
        initFieldsTranslatorTest();
        assertNotNull(jukeboxSchemaNode());
        assertNotNull(testServices());
        assertNotNull(foo());
    }

    /**
     * Test parse fields parameter containing only one child selected.
     */
    @Test
    void testSimplePath() throws Exception {
        final var result = FieldsParamParser.fieldsParamsToPaths(JUKEBOX_SCHEMA, jukeboxSchemaNode(),
            assertFields("library"));
        assertNotNull(result);
        assertEquals(1, result.size());
        final var pathArguments = result.getFirst().getPathArguments();
        assertEquals(1, pathArguments.size());
        assertEquals(LIBRARY_QNAME, pathArguments.getFirst().getNodeType());
    }

    /**
     * Test parse fields parameter containing two child nodes selected.
     */
    @Test
    void testDoublePath() throws Exception {
        final var result = FieldsParamParser.fieldsParamsToPaths(JUKEBOX_SCHEMA, jukeboxSchemaNode(),
            assertFields("library;player"));
        assertNotNull(result);
        assertEquals(2, result.size());

        final var libraryPath = assertPath(result, LIBRARY_QNAME);
        assertEquals(1, libraryPath.getPathArguments().size());

        final var playerPath = assertPath(result, PLAYER_QNAME);
        assertEquals(1, playerPath.getPathArguments().size());
    }

    /**
     * Test parse fields parameter containing sub-children selected delimited by slash.
     */
    @Test
    void testSubPath() throws Exception {
        final var result = FieldsParamParser.fieldsParamsToPaths(JUKEBOX_SCHEMA, jukeboxSchemaNode(),
            assertFields("library/artist/album/name"));
        assertNotNull(result);
        assertEquals(1, result.size());
        final var pathArguments = result.getFirst().getPathArguments();
        assertEquals(6, pathArguments.size());
        assertEquals(LIBRARY_QNAME, pathArguments.get(0).getNodeType());
        assertEquals(ARTIST_QNAME, pathArguments.get(1).getNodeType());
        assertEquals(ARTIST_QNAME, pathArguments.get(2).getNodeType());
        assertEquals(ALBUM_QNAME, pathArguments.get(3).getNodeType());
        assertEquals(ALBUM_QNAME, pathArguments.get(4).getNodeType());
        assertEquals(NAME_QNAME, pathArguments.get(5).getNodeType());
    }

    /**
     * Test parse fields parameter containing sub-children selected delimited by parenthesis.
     */
    @Test
    void testChildrenPath() throws Exception {
        final var result = FieldsParamParser.fieldsParamsToPaths(JUKEBOX_SCHEMA, jukeboxSchemaNode(),
            assertFields("library(artist(album(name)))"));
        assertNotNull(result);
        assertEquals(1, result.size());
        final var pathArguments = result.getFirst().getPathArguments();
        assertEquals(6, pathArguments.size());
        assertEquals(LIBRARY_QNAME, pathArguments.get(0).getNodeType());
        assertEquals(ARTIST_QNAME, pathArguments.get(1).getNodeType());
        assertEquals(ARTIST_QNAME, pathArguments.get(2).getNodeType());
        assertEquals(ALBUM_QNAME, pathArguments.get(3).getNodeType());
        assertEquals(ALBUM_QNAME, pathArguments.get(4).getNodeType());
        assertEquals(NAME_QNAME, pathArguments.get(5).getNodeType());
    }

    /**
     * Test parse fields parameter when augmentation with different namespace is used.
     */
    @Test
    void testNamespace() throws Exception {
        final var result = FieldsParamParser.fieldsParamsToPaths(JUKEBOX_SCHEMA, jukeboxSchemaNode(),
            assertFields("augmented-jukebox:augmented-library"));
        assertNotNull(result);
        assertEquals(1, result.size());
        final var augmentedLibraryPath = assertPath(result, AUGMENTED_LIBRARY_Q_NAME);
        assertEquals(1, augmentedLibraryPath.getPathArguments().size());
    }

    /**
     * Testing of fields parameter parsing when multiple nodes are wrapped in brackets and these nodes are not
     * direct children of parent node - multiple children which are constructed using '/'.
     */
    @Test
    void testMultipleChildren1() throws Exception {
        final var result = FieldsParamParser.fieldsParamsToPaths(TEST_SERVICES_SCHEMA, testServices(),
            assertFields("services(type-of-service;instance/instance-name;instance/provider)"));
        assertNotNull(result);
        assertEquals(3, result.size());

        final var tosPath = assertPath(result, TYPE_OF_SERVICE_Q_NAME);
        assertEquals(3, tosPath.getPathArguments().size());

        final var instanceNamePath = assertPath(result, INSTANCE_NAME_Q_NAME);
        assertEquals(5, instanceNamePath.getPathArguments().size());

        final var providerPath = assertPath(result, PROVIDER_Q_NAME);
        assertEquals(5, providerPath.getPathArguments().size());
    }

    /**
     * Testing of fields parameter parsing when multiple nodes are wrapped in brackets and these nodes are not
     * direct children of parent node - one of children nodes is typed using brackets, other is constructed using '/'.
     */
    @Test
    void testMultipleChildren2() throws Exception {
        final var result = FieldsParamParser.fieldsParamsToPaths(TEST_SERVICES_SCHEMA, testServices(),
            assertFields("services(type-of-service;instance(instance-name;provider))"));
        assertNotNull(result);
        assertEquals(3, result.size());

        final var tosPath = assertPath(result, TYPE_OF_SERVICE_Q_NAME);
        assertEquals(3, tosPath.getPathArguments().size());

        final var instanceNamePath = assertPath(result, INSTANCE_NAME_Q_NAME);
        assertEquals(5, instanceNamePath.getPathArguments().size());

        final var providerPath = assertPath(result, PROVIDER_Q_NAME);
        assertEquals(5, providerPath.getPathArguments().size());
    }

    /**
     * Testing of fields parameter parsing when multiple nodes are wrapped in brackets and these nodes are not
     * direct children of parent node - multiple children with different parent nodes.
     */
    @Test
    void testMultipleChildren3() throws Exception {
        final var result = FieldsParamParser.fieldsParamsToPaths(TEST_SERVICES_SCHEMA, testServices(),
            assertFields("services(instance/instance-name;type-of-service;next-data/next-service)"));
        assertNotNull(result);
        assertEquals(3, result.size());

        final var instanceNamePath = assertPath(result, INSTANCE_NAME_Q_NAME);
        assertEquals(5, instanceNamePath.getPathArguments().size());

        final var tosPath = assertPath(result, TYPE_OF_SERVICE_Q_NAME);
        assertEquals(3, tosPath.getPathArguments().size());

        final var nextServicePath = assertPath(result, NEXT_SERVICE_Q_NAME);
        assertEquals(4, nextServicePath.getPathArguments().size());
    }

    @Test
    void testMultipleChildren4() throws Exception {
        final var result = FieldsParamParser.fieldsParamsToPaths(TEST_SERVICES_SCHEMA, testServices(),
            assertFields("services(type-of-service;instance(instance-name;provider);next-data(next-service))"));
        assertNotNull(result);
        assertEquals(4, result.size());

        final var instanceNamePath = assertPath(result, INSTANCE_NAME_Q_NAME);
        assertEquals(5, instanceNamePath.getPathArguments().size());

        final var tosPath = assertPath(result, TYPE_OF_SERVICE_Q_NAME);
        assertEquals(3, tosPath.getPathArguments().size());

        final var nextServicePath = assertPath(result, NEXT_SERVICE_Q_NAME);
        assertEquals(4, nextServicePath.getPathArguments().size());

        final var providerPath = assertPath(result, PROVIDER_Q_NAME);
        assertEquals(5, providerPath.getPathArguments().size());
    }

    @Test
    void testMultipleChildren5() throws Exception {
        final var result = FieldsParamParser.fieldsParamsToPaths(TEST_SERVICES_SCHEMA, testServices(),
            assertFields("services(type-of-service;instance(instance-name;provider);next-data/next-service)"));
        assertNotNull(result);
        assertEquals(4, result.size());

        final var instanceNamePath = assertPath(result, INSTANCE_NAME_Q_NAME);
        assertEquals(5, instanceNamePath.getPathArguments().size());

        final var tosPath = assertPath(result, TYPE_OF_SERVICE_Q_NAME);
        assertEquals(3, tosPath.getPathArguments().size());

        final var nextServicePath = assertPath(result, NEXT_SERVICE_Q_NAME);
        assertEquals(4, nextServicePath.getPathArguments().size());

        final var providerPath = assertPath(result, PROVIDER_Q_NAME);
        assertEquals(5, providerPath.getPathArguments().size());
    }

    @Test
    void testAugmentedChild() throws Exception {
        final var result = FieldsParamParser.fieldsParamsToPaths(JUKEBOX_SCHEMA, jukeboxSchemaNode(),
            assertFields("player/augmented-jukebox:speed"));
        assertNotNull(result);
        assertEquals(1, result.size());
        final var pathArguments = result.getFirst().getPathArguments();

        assertEquals(2, pathArguments.size());
        assertEquals(PLAYER_QNAME, pathArguments.get(0).getNodeType());
        assertEquals(SPEED_Q_NAME, pathArguments.get(1).getNodeType());
    }

    @Test
    void testListFieldUnderList() throws Exception {
        final var result = FieldsParamParser.fieldsParamsToPaths(TEST_SERVICES_SCHEMA, testServices(),
            assertFields("services/instance"));
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(List.of(
            NodeIdentifier.create(SERVICES_Q_NAME),
            NodeIdentifierWithPredicates.of(SERVICES_Q_NAME),
            NodeIdentifier.create(INSTANCE_Q_NAME),
            NodeIdentifierWithPredicates.of(INSTANCE_Q_NAME)),
            result.getFirst().getPathArguments());
    }

    @Test
    void testLeafList() throws Exception {
        final var result = FieldsParamParser.fieldsParamsToPaths(TEST_SERVICES_SCHEMA, testServices(),
            assertFields("protocols"));
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(List.of(new NodeIdentifier(PROTOCOLS_Q_NAME)), result.getFirst().getPathArguments());
    }

    @Test
    void testKeyedList() throws Exception {
        final var result = FieldsParamParser.fieldsParamsToPaths(JUKEBOX_SCHEMA, jukeboxSchemaNode(),
            assertFields("library/artist(name)"));
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    void testDuplicateNodes1() throws Exception {
        final var result = FieldsParamParser.fieldsParamsToPaths(FOO_SCHEMA, foo(),
            assertFields("bar(alpha;beta/gamma);baz(alpha;beta/gamma)"));
        assertNotNull(result);
        assertEquals(4, result.size());
        assertEquals(
            Set.of(List.of(NodeIdentifier.create(BAR_Q_NAME), NodeIdentifier.create(ALPHA_Q_NAME)),
                List.of(NodeIdentifier.create(BAR_Q_NAME), NodeIdentifier.create(BETA_Q_NAME),
                    NodeIdentifier.create(GAMMA_Q_NAME)),
                List.of(NodeIdentifier.create(BAZ_Q_NAME), NodeIdentifierWithPredicates.of(BAZ_Q_NAME),
                    NodeIdentifier.create(ALPHA_Q_NAME)),
                List.of(NodeIdentifier.create(BAZ_Q_NAME), NodeIdentifierWithPredicates.of(BAZ_Q_NAME),
                    NodeIdentifier.create(BETA_Q_NAME), NodeIdentifier.create(GAMMA_Q_NAME))),
            result.stream().map(YangInstanceIdentifier::getPathArguments).collect(Collectors.toSet()));
    }

    @Test
    void testDuplicateNodes2() throws Exception {
        final var result = FieldsParamParser.fieldsParamsToPaths(FOO_SCHEMA, foo(),
            assertFields("bar(alpha;beta/delta);baz(alpha;beta/epsilon)"));
        assertNotNull(result);
        assertEquals(4, result.size());
        assertEquals(
            Set.of(List.of(NodeIdentifier.create(BAR_Q_NAME), NodeIdentifier.create(ALPHA_Q_NAME)),
                List.of(NodeIdentifier.create(BAR_Q_NAME), NodeIdentifier.create(BETA_Q_NAME),
                    NodeIdentifier.create(DELTA_Q_NAME)),
                List.of(NodeIdentifier.create(BAZ_Q_NAME), NodeIdentifierWithPredicates.of(BAZ_Q_NAME),
                    NodeIdentifier.create(ALPHA_Q_NAME)),
                List.of(NodeIdentifier.create(BAZ_Q_NAME), NodeIdentifierWithPredicates.of(BAZ_Q_NAME),
                    NodeIdentifier.create(BETA_Q_NAME), NodeIdentifier.create(EPSILON_Q_NAME))),
            result.stream().map(YangInstanceIdentifier::getPathArguments).collect(Collectors.toSet()));
    }

    /**
     * Test parse fields parameter when not existing child node selected.
     */
    @Test
    void testMissingChildSchema() {
        final var input = assertFields("library(not-existing)");
        final var ex = assertThrows(RequestException.class,
            () -> FieldsParamParser.fieldsParamsToPaths(JUKEBOX_SCHEMA, jukeboxSchemaNode(), input));
        // Bad request
        final var errors = ex.errors();
        assertEquals(1, errors.size());
        final var error = errors.getFirst();
        assertEquals(ErrorType.PROTOCOL, error.type());
        assertEquals(ErrorTag.INVALID_VALUE, error.tag());
    }

    private static YangInstanceIdentifier assertPath(final List<YangInstanceIdentifier> paths, final QName lastArg) {
        return paths.stream()
            .filter(path -> lastArg.equals(path.getLastPathArgument().getNodeType()))
            .findAny()
            .orElseThrow(() -> new AssertionError("Path ending with " + lastArg + " not found"));
    }
}
