/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.mdsal.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.text.ParseException;
import java.util.List;
import org.eclipse.jdt.annotation.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opendaylight.restconf.api.query.FieldsParam;
import org.opendaylight.restconf.server.api.ServerException;
import org.opendaylight.restconf.server.spi.AbstractJukeboxTest;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContext;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextTree;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

abstract class AbstractFieldsTranslatorTest<T> extends AbstractJukeboxTest {
    private static final QNameModule Q_NAME_MODULE_TEST_SERVICES =
        QNameModule.ofRevision("tests:test-services", "2019-03-25");
    private static final QNameModule Q_NAME_MODULE_AUGMENTED_JUKEBOX =
        QNameModule.ofRevision("http://example.com/ns/augmented-jukebox", "2016-05-05");
    private static final QNameModule Q_NAME_MODULE_FOO = QNameModule.ofRevision("urn:foo", "2023-03-27");

    private static final EffectiveModelContext TEST_SERVICES_SCHEMA =
        YangParserTestUtils.parseYangResourceDirectory("/test-services");
    private static final EffectiveModelContext FOO_SCHEMA =
        YangParserTestUtils.parseYangResourceDirectory("/same-qname-nodes");

    private DataSchemaContext jukeboxSchemaNode;
    private DataSchemaContext testServices;
    private DataSchemaContext foo;

    // container augmented library
    protected static final QName AUGMENTED_LIBRARY_Q_NAME = QName.create(Q_NAME_MODULE_AUGMENTED_JUKEBOX,
            "augmented-library");

    // leaf speed
    protected static final QName SPEED_Q_NAME = QName.create(Q_NAME_MODULE_AUGMENTED_JUKEBOX, "speed");

    // container test data
    private static final QName TEST_DATA_Q_NAME = QName.create(Q_NAME_MODULE_TEST_SERVICES, "test-data");

    // list services
    protected static final QName SERVICES_Q_NAME = QName.create(Q_NAME_MODULE_TEST_SERVICES, "services");

    // leaf type-of-service
    protected static final QName TYPE_OF_SERVICE_Q_NAME = QName.create(Q_NAME_MODULE_TEST_SERVICES, "type-of-service");

    // list instance
    protected static final QName INSTANCE_Q_NAME = QName.create(Q_NAME_MODULE_TEST_SERVICES, "instance");

    // leaf instance-name
    protected static final QName INSTANCE_NAME_Q_NAME = QName.create(Q_NAME_MODULE_TEST_SERVICES, "instance-name");

    // leaf provider
    protected static final QName PROVIDER_Q_NAME = QName.create(Q_NAME_MODULE_TEST_SERVICES, "provider");

    // container next-data
    protected static final QName NEXT_DATA_Q_NAME = QName.create(Q_NAME_MODULE_TEST_SERVICES, "next-data");

    // leaf next-service
    protected static final QName NEXT_SERVICE_Q_NAME = QName.create(Q_NAME_MODULE_TEST_SERVICES, "next-service");

    // leaf-list protocols
    protected static final QName PROTOCOLS_Q_NAME = QName.create(Q_NAME_MODULE_TEST_SERVICES, "protocols");

    // container foo
    protected static final QName FOO_Q_NAME = QName.create(Q_NAME_MODULE_FOO, "foo");

    // container bar
    protected static final QName BAR_Q_NAME = QName.create(Q_NAME_MODULE_FOO, "bar");

    // container baz
    protected static final QName BAZ_Q_NAME = QName.create(Q_NAME_MODULE_FOO, "baz");

    // leaf alpha
    protected static final QName ALPHA_Q_NAME = QName.create(Q_NAME_MODULE_FOO, "alpha");

    // container beta
    protected static final QName BETA_Q_NAME = QName.create(Q_NAME_MODULE_FOO, "beta");

    // container foo
    protected static final QName GAMMA_Q_NAME = QName.create(Q_NAME_MODULE_FOO, "gamma");

    // container foo
    protected static final QName DELTA_Q_NAME = QName.create(Q_NAME_MODULE_FOO, "delta");

    // container foo
    protected static final QName EPSILON_Q_NAME = QName.create(Q_NAME_MODULE_FOO, "epsilon");

    @BeforeEach
    void setUp() {
        jukeboxSchemaNode = DataSchemaContextTree.from(JUKEBOX_SCHEMA).getRoot().childByQName(JUKEBOX_QNAME);
        assertNotNull(jukeboxSchemaNode);
        testServices = DataSchemaContextTree.from(TEST_SERVICES_SCHEMA).getRoot().childByQName(TEST_DATA_Q_NAME);
        assertNotNull(testServices);
        foo = DataSchemaContextTree.from(FOO_SCHEMA).getRoot().childByQName(FOO_Q_NAME);
        assertNotNull(foo);
    }

    protected abstract List<T> translateFields(@NonNull EffectiveModelContext modelContext,
        @NonNull DataSchemaContext startNode, @NonNull FieldsParam fields) throws ServerException;

    /**
     * Test parse fields parameter containing only one child selected.
     */
    @Test
    void testSimplePath() throws Exception {
        final var result = translateFields(JUKEBOX_SCHEMA, jukeboxSchemaNode, assertFields("library"));
        assertNotNull(result);
        assertSimplePath(result);
    }

    protected abstract void assertSimplePath(@NonNull List<T> result);

    /**
     * Test parse fields parameter containing two child nodes selected.
     */
    @Test
    void testDoublePath() throws Exception {
        final var result = translateFields(JUKEBOX_SCHEMA, jukeboxSchemaNode, assertFields("library;player"));
        assertNotNull(result);
        assertDoublePath(result);
    }

    protected abstract void assertDoublePath(@NonNull List<T> result);

    /**
     * Test parse fields parameter containing sub-children selected delimited by slash.
     */
    @Test
    void testSubPath() throws Exception {
        final var result = translateFields(JUKEBOX_SCHEMA, jukeboxSchemaNode,
            assertFields("library/artist/album/name"));
        assertNotNull(result);
        assertSubPath(result);
    }

    protected abstract void assertSubPath(@NonNull List<T> result);

    /**
     * Test parse fields parameter containing sub-children selected delimited by parenthesis.
     */
    @Test
    void testChildrenPath() throws Exception {
        final var result = translateFields(JUKEBOX_SCHEMA, jukeboxSchemaNode,
            assertFields("library(artist(album(name)))"));
        assertNotNull(result);
        assertChildrenPath(result);
    }

    protected abstract void assertChildrenPath(@NonNull List<T> result);

    /**
     * Test parse fields parameter when augmentation with different namespace is used.
     */
    @Test
    void testNamespace() throws Exception {
        final var result = translateFields(JUKEBOX_SCHEMA, jukeboxSchemaNode,
            assertFields("augmented-jukebox:augmented-library"));
        assertNotNull(result);
        assertNamespace(result);
    }

    protected abstract void assertNamespace(@NonNull List<T> result);

    /**
     * Testing of fields parameter parsing when multiple nodes are wrapped in brackets and these nodes are not
     * direct children of parent node - multiple children which are constructed using '/'.
     */
    @Test
    void testMultipleChildren1() throws Exception {
        final var result = translateFields(TEST_SERVICES_SCHEMA, testServices,
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
    void testMultipleChildren2() throws Exception {
        final var result = translateFields(TEST_SERVICES_SCHEMA, testServices,
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
    void testMultipleChildren3() throws Exception {
        final var result = translateFields(TEST_SERVICES_SCHEMA, testServices,
            assertFields("services(instance/instance-name;type-of-service;next-data/next-service)"));
        assertNotNull(result);
        assertMultipleChildren3(result);
    }

    protected abstract void assertMultipleChildren3(@NonNull List<T> result);

    @Test
    void testMultipleChildren4() throws Exception {
        final var result = translateFields(TEST_SERVICES_SCHEMA, testServices,
                assertFields("services(type-of-service;instance(instance-name;provider);next-data(next-service))"));
        assertNotNull(result);
        assertMultipleChildren4(result);
    }

    protected abstract void assertMultipleChildren4(@NonNull List<T> result);

    @Test
    void testMultipleChildren5() throws Exception {
        final var result = translateFields(TEST_SERVICES_SCHEMA, testServices,
                assertFields("services(type-of-service;instance(instance-name;provider);next-data/next-service)"));
        assertNotNull(result);
        assertMultipleChildren5(result);
    }

    protected abstract void assertMultipleChildren5(@NonNull List<T> result);

    @Test
    void testAugmentedChild() throws Exception {
        final var result = translateFields(JUKEBOX_SCHEMA, jukeboxSchemaNode,
            assertFields("player/augmented-jukebox:speed"));
        assertNotNull(result);
        assertAugmentedChild(result);
    }

    protected abstract void assertAugmentedChild(@NonNull List<T> result);

    @Test
    void testListFieldUnderList() throws Exception {
        final var result = translateFields(TEST_SERVICES_SCHEMA, testServices, assertFields("services/instance"));
        assertNotNull(result);
        assertListFieldUnderList(result);
    }

    protected abstract void assertListFieldUnderList(@NonNull List<T> result);

    @Test
    void testLeafList() throws Exception {
        final var result = translateFields(TEST_SERVICES_SCHEMA, testServices, assertFields("protocols"));
        assertNotNull(result);
        assertLeafList(result);
    }

    @Test
    void testKeyedList() throws Exception {
        final var result = translateFields(JUKEBOX_SCHEMA, jukeboxSchemaNode, assertFields("library/artist(name)"));
        assertNotNull(result);
        assertKeyedList(result);
    }

    protected abstract void assertKeyedList(List<T> result);

    protected abstract void assertLeafList(@NonNull List<T> result);

    @Test
    void testDuplicateNodes1() throws Exception {
        final var result = translateFields(FOO_SCHEMA, foo,
            assertFields("bar(alpha;beta/gamma);baz(alpha;beta/gamma)"));
        assertNotNull(result);
        assertDuplicateNodes1(result);
    }

    protected abstract void assertDuplicateNodes1(List<T> result);

    @Test
    void testDuplicateNodes2() throws Exception {
        final var result = translateFields(FOO_SCHEMA, foo,
            assertFields("bar(alpha;beta/delta);baz(alpha;beta/epsilon)"));
        assertNotNull(result);
        assertDuplicateNodes2(result);
    }

    protected abstract void assertDuplicateNodes2(List<T> result);

    /**
     * Test parse fields parameter when not existing child node selected.
     */
    @Test
    void testMissingChildSchema() throws ParseException {
        final var input = FieldsParam.parse("library(not-existing)");

        final var ex = assertThrows(ServerException.class,
            () -> translateFields(JUKEBOX_SCHEMA, jukeboxSchemaNode, input));
        // Bad request
        final var errors = ex.errors();
        assertEquals(1, errors.size());
        final var error = errors.get(0);
        assertEquals(ErrorType.PROTOCOL, error.type());
        assertEquals(ErrorTag.INVALID_VALUE, error.tag());
    }

    private static @NonNull FieldsParam assertFields(final String input) {
        try {
            return FieldsParam.parse(input);
        } catch (ParseException e) {
            throw new AssertionError(e);
        }
    }
}
