/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 * Copyright (c) 2021 PANTHEON.tech, s.r.o.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.restconf.server.api.testlib.AbstractFieldsTranslatorTest;
import org.opendaylight.yangtools.databind.RequestException;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;

/**
 * Unit test for {@link MdsalFieldsParam}.
 */
@ExtendWith(MockitoExtension.class)
class MdsalFieldsParamTest extends AbstractFieldsTranslatorTest {

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
        final var result = NormalizedNodeWriter.translateFieldsParam(JUKEBOX_SCHEMA, jukeboxSchemaNode(),
            assertFields("library"));
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(Set.of(LIBRARY_QNAME), result.getFirst());
    }

    /**
     * Test parse fields parameter containing two child nodes selected.
     */
    @Test
    void testDoublePath() throws Exception {
        final var result = NormalizedNodeWriter.translateFieldsParam(JUKEBOX_SCHEMA, jukeboxSchemaNode(),
            assertFields("library;player"));
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(Set.of(LIBRARY_QNAME, PLAYER_QNAME), result.getFirst());
    }

    /**
     * Test parse fields parameter containing sub-children selected delimited by slash.
     */
    @Test
    void testSubPath() throws Exception {
        final var result = NormalizedNodeWriter.translateFieldsParam(JUKEBOX_SCHEMA, jukeboxSchemaNode(),
            assertFields("library/artist/album/name"));
        assertNotNull(result);
        assertEquals(4, result.size());
        assertEquals(Set.of(LIBRARY_QNAME), result.get(0));
        assertEquals(Set.of(ARTIST_QNAME), result.get(1));
        assertEquals(Set.of(ALBUM_QNAME), result.get(2));
        assertEquals(Set.of(NAME_QNAME), result.get(3));
    }

    /**
     * Test parse fields parameter containing sub-children selected delimited by parenthesis.
     */
    @Test
    void testChildrenPath() throws Exception {
        final var result = NormalizedNodeWriter.translateFieldsParam(JUKEBOX_SCHEMA, jukeboxSchemaNode(),
            assertFields("library(artist(album(name)))"));
        assertNotNull(result);
        assertEquals(4, result.size());
        assertEquals(Set.of(LIBRARY_QNAME), result.get(0));
        assertEquals(Set.of(ARTIST_QNAME), result.get(1));
        assertEquals(Set.of(ALBUM_QNAME), result.get(2));
        assertEquals(Set.of(NAME_QNAME), result.get(3));
    }

    /**
     * Test parse fields parameter when augmentation with different namespace is used.
     */
    @Test
    void testNamespace() throws Exception {
        final var result = NormalizedNodeWriter.translateFieldsParam(JUKEBOX_SCHEMA, jukeboxSchemaNode(),
            assertFields("augmented-jukebox:augmented-library"));
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(Set.of(AUGMENTED_LIBRARY_Q_NAME), result.getFirst());
    }

    /**
     * Testing of fields parameter parsing when multiple nodes are wrapped in brackets and these nodes are not
     * direct children of parent node - multiple children which are constructed using '/'.
     */
    @Test
    void testMultipleChildren1() throws Exception {
        final var result = NormalizedNodeWriter.translateFieldsParam(TEST_SERVICES_SCHEMA, testServices(),
            assertFields("services(type-of-service;instance/instance-name;instance/provider)"));
        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals(Set.of(SERVICES_Q_NAME), result.get(0));
        assertEquals(Set.of(TYPE_OF_SERVICE_Q_NAME, INSTANCE_Q_NAME), result.get(1));
        assertEquals(Set.of(INSTANCE_NAME_Q_NAME, PROVIDER_Q_NAME), result.get(2));
    }

    /**
     * Testing of fields parameter parsing when multiple nodes are wrapped in brackets and these nodes are not
     * direct children of parent node - one of children nodes is typed using brackets, other is constructed using '/'.
     */
    @Test
    void testMultipleChildren2() throws Exception {
        final var result = NormalizedNodeWriter.translateFieldsParam(TEST_SERVICES_SCHEMA, testServices(),
            assertFields("services(type-of-service;instance(instance-name;provider))"));
        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals(Set.of(SERVICES_Q_NAME), result.get(0));
        assertEquals(Set.of(TYPE_OF_SERVICE_Q_NAME, INSTANCE_Q_NAME), result.get(1));
        assertEquals(Set.of(INSTANCE_NAME_Q_NAME, PROVIDER_Q_NAME), result.get(2));
    }

    /**
     * Testing of fields parameter parsing when multiple nodes are wrapped in brackets and these nodes are not
     * direct children of parent node - multiple children with different parent nodes.
     */
    @Test
    void testMultipleChildren3() throws Exception {
        final var result = NormalizedNodeWriter.translateFieldsParam(TEST_SERVICES_SCHEMA, testServices(),
            assertFields("services(instance/instance-name;type-of-service;next-data/next-service)"));
        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals(Set.of(SERVICES_Q_NAME), result.get(0));
        assertEquals(Set.of(TYPE_OF_SERVICE_Q_NAME, INSTANCE_Q_NAME, NEXT_DATA_Q_NAME), result.get(1));
        assertEquals(Set.of(INSTANCE_NAME_Q_NAME, NEXT_SERVICE_Q_NAME), result.get(2));
    }

    @Test
    void testMultipleChildren4() throws Exception {
        final var result = NormalizedNodeWriter.translateFieldsParam(TEST_SERVICES_SCHEMA, testServices(),
            assertFields("services(type-of-service;instance(instance-name;provider);next-data(next-service))"));
        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals(Set.of(SERVICES_Q_NAME), result.get(0));
        assertEquals(Set.of(TYPE_OF_SERVICE_Q_NAME, INSTANCE_Q_NAME, NEXT_DATA_Q_NAME), result.get(1));
        assertEquals(Set.of(INSTANCE_NAME_Q_NAME, PROVIDER_Q_NAME, NEXT_SERVICE_Q_NAME), result.get(2));
    }

    @Test
    void testMultipleChildren5() throws Exception {
        final var result = NormalizedNodeWriter.translateFieldsParam(TEST_SERVICES_SCHEMA, testServices(),
            assertFields("services(type-of-service;instance(instance-name;provider);next-data/next-service)"));
        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals(Set.of(SERVICES_Q_NAME), result.get(0));
        assertEquals(Set.of(TYPE_OF_SERVICE_Q_NAME, INSTANCE_Q_NAME, NEXT_DATA_Q_NAME), result.get(1));
        assertEquals(Set.of(INSTANCE_NAME_Q_NAME, PROVIDER_Q_NAME, NEXT_SERVICE_Q_NAME), result.get(2));
    }

    @Test
    void testAugmentedChild() throws Exception {
        final var result = NormalizedNodeWriter.translateFieldsParam(JUKEBOX_SCHEMA, jukeboxSchemaNode(),
            assertFields("player/augmented-jukebox:speed"));
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(Set.of(PLAYER_QNAME), result.get(0));
        assertEquals(Set.of(SPEED_Q_NAME), result.get(1));
    }

    @Test
    void testListFieldUnderList() throws Exception {
        final var result = NormalizedNodeWriter.translateFieldsParam(TEST_SERVICES_SCHEMA, testServices(),
            assertFields("services/instance"));
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(Set.of(SERVICES_Q_NAME), result.get(0));
        assertEquals(Set.of(INSTANCE_Q_NAME), result.get(1));
    }

    @Test
    void testLeafList() throws Exception {
        final var result = NormalizedNodeWriter.translateFieldsParam(TEST_SERVICES_SCHEMA, testServices(),
            assertFields("protocols"));
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(Set.of(PROTOCOLS_Q_NAME), result.getFirst());
    }

    @Test
    void testKeyedList() throws Exception {
        final var result = NormalizedNodeWriter.translateFieldsParam(JUKEBOX_SCHEMA, jukeboxSchemaNode(),
            assertFields("library/artist(name)"));
        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals(Set.of(LIBRARY_QNAME), result.get(0));
        assertEquals(Set.of(ARTIST_QNAME), result.get(1));
        assertEquals(Set.of(NAME_QNAME), result.get(2));
    }

    @Test
    void testDuplicateNodes1() throws Exception {
        final var result = NormalizedNodeWriter.translateFieldsParam(FOO_SCHEMA, foo(),
            assertFields("bar(alpha;beta/gamma);baz(alpha;beta/gamma)"));
        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals(Set.of(BAR_Q_NAME, BAZ_Q_NAME), result.get(0));
        assertEquals(Set.of(ALPHA_Q_NAME, BETA_Q_NAME), result.get(1));
        assertEquals(Set.of(GAMMA_Q_NAME), result.get(2));
    }

    @Test
    void testDuplicateNodes2() throws Exception {
        final var result = NormalizedNodeWriter.translateFieldsParam(FOO_SCHEMA, foo(),
            assertFields("bar(alpha;beta/delta);baz(alpha;beta/epsilon)"));
        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals(Set.of(BAR_Q_NAME, BAZ_Q_NAME), result.get(0));
        assertEquals(Set.of(ALPHA_Q_NAME, BETA_Q_NAME), result.get(1));
        assertEquals(Set.of(DELTA_Q_NAME, EPSILON_Q_NAME), result.get(2));
    }

    /**
     * Test parse fields parameter when not existing child node selected.
     */
    @Test
    void testMissingChildSchema() {
        final var ex = assertThrows(RequestException.class,
            () -> NormalizedNodeWriter.translateFieldsParam(JUKEBOX_SCHEMA, jukeboxSchemaNode(),
                assertFields("library(not-existing)")));
        // Bad request
        final var errors = ex.errors();
        assertEquals(1, errors.size());
        final var error = errors.getFirst();
        assertEquals(ErrorType.PROTOCOL, error.type());
        assertEquals(ErrorTag.INVALID_VALUE, error.tag());
    }
}
