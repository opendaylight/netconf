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

import java.text.ParseException;
import java.util.List;
import org.eclipse.jdt.annotation.NonNull;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.restconf.api.query.FieldsParam;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.AbstractJukeboxTest;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.common.XMLNamespace;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public abstract class AbstractFieldsTranslatorTest<T> extends AbstractJukeboxTest {
    private static final QNameModule Q_NAME_MODULE_TEST_SERVICES = QNameModule.create(
        XMLNamespace.of("tests:test-services"), Revision.of("2019-03-25"));
    private static final QNameModule Q_NAME_MODULE_AUGMENTED_JUKEBOX = QNameModule.create(
        XMLNamespace.of("http://example.com/ns/augmented-jukebox"), Revision.of("2016-05-05"));
    private static final QNameModule Q_NAME_MODULE_FOO = QNameModule.create(
        XMLNamespace.of("urn:foo"), Revision.of("2023-03-27"));

    private InstanceIdentifierContext identifierJukebox;
    private InstanceIdentifierContext identifierTestServices;
    private InstanceIdentifierContext identifierFoo;


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

    @Before
    public void setUp() {
        identifierJukebox = InstanceIdentifierContext.ofStack(
            SchemaInferenceStack.ofDataTreePath(JUKEBOX_SCHEMA, JUKEBOX_QNAME));

        identifierTestServices = InstanceIdentifierContext.ofStack(
            SchemaInferenceStack.ofDataTreePath(YangParserTestUtils.parseYangResourceDirectory("/test-services"),
                TEST_DATA_Q_NAME));

        identifierFoo = InstanceIdentifierContext.ofStack(SchemaInferenceStack.ofDataTreePath(
            YangParserTestUtils.parseYangResourceDirectory("/same-qname-nodes"), FOO_Q_NAME));
    }

    protected abstract List<T> translateFields(InstanceIdentifierContext context, FieldsParam fields);

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
        final var result = translateFields(identifierJukebox, assertFields("library/artist/album/name"));
        assertNotNull(result);
        assertSubPath(result);
    }

    protected abstract void assertSubPath(@NonNull List<T> result);

    /**
     * Test parse fields parameter containing sub-children selected delimited by parenthesis.
     */
    @Test
    public void testChildrenPath() {
        final var result = translateFields(identifierJukebox, assertFields("library(artist(album(name)))"));
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

    @Test
    public void testDuplicateNodes1() {
        final var result = translateFields(identifierFoo,
            assertFields("bar(alpha;beta/gamma);baz(alpha;beta/gamma)"));
        assertNotNull(result);
        assertDuplicateNodes1(result);
    }

    protected abstract void assertDuplicateNodes1(List<T> result);

    @Test
    public void testDuplicateNodes2() {
        final var result = translateFields(identifierFoo,
            assertFields("bar(alpha;beta/delta);baz(alpha;beta/epsilon)"));
        assertNotNull(result);
        assertDuplicateNodes2(result);
    }

    protected abstract void assertDuplicateNodes2(List<T> result);

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
