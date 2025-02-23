/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.common.collect.ImmutableMap;
import java.text.ParseException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.opendaylight.netconf.databind.DatabindContext;
import org.opendaylight.netconf.databind.DatabindPath.Data;
import org.opendaylight.netconf.databind.ErrorMessage;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.server.api.ServerError;
import org.opendaylight.restconf.server.api.ServerException;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.common.Uint8;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeWithValue;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

/**
 * Unit tests for {@link ApiPathNormalizer#canonicalize(YangInstanceIdentifier)}.
 */
class ApiPathCanonizerTest {
    private static final ApiPathNormalizer NORMALIZER;
    private static final ApiPathCanonizer CANONIZER;

    static {
        final var provider = DatabindContext.ofModel(
            YangParserTestUtils.parseYangResourceDirectory("/restconf/parser/serializer"));

        NORMALIZER = new ApiPathNormalizer(provider);
        CANONIZER = new ApiPathCanonizer(provider);
    }

    /**
     * Positive test of serialization of <code>YangInstanceIdentifier</code> containing container node to
     * <code>String</code>. Returned <code>String</code> is compared to have expected value.
     */
    @Test
    void serializeContainerTest() {
        assertApiPath("serializer-test:contA",
            YangInstanceIdentifier.of(QName.create("serializer:test", "2016-06-06", "contA")));
    }

    /**
     * Positive test of serialization of <code>YangInstanceIdentifier</code> containing container with leaf node to
     * <code>String</code>. Returned <code>String</code> is compared to have expected value.
     */
    @Test
    void serializeContainerWithLeafTest() {
        assertApiPath("serializer-test:contA/leaf-A",
            YangInstanceIdentifier.of(
                QName.create("serializer:test", "2016-06-06", "contA"),
                QName.create("serializer:test", "2016-06-06", "leaf-A")));
    }

    /**
     * Positive test of serialization of <code>YangInstanceIdentifier</code> containing container with list with leaf
     * list node to <code>String</code>. Returned <code>String</code> is compared to have expected value.
     */
    @Test
    void serializeContainerWithListWithLeafListTest() {
        final var list = QName.create("serializer:test", "2016-06-06", "list-A");
        final var leafList = QName.create("serializer:test", "2016-06-06", "leaf-list-AA");

        assertApiPath("serializer-test:contA/list-A=100/leaf-list-AA=instance",
            YangInstanceIdentifier.builder()
                .node(QName.create("serializer:test", "2016-06-06", "contA"))
                .node(list)
                .node(NodeIdentifierWithPredicates.of(list, QName.create(list, "list-key"), 100))
                .node(leafList)
                .node(new NodeWithValue<>(leafList, "instance"))
                .build());
    }

    /**
     * Positive test of serialization of <code>YangInstanceIdentifier</code> to <code>String</code> when serialized
     * <code>YangInstanceIdentifier</code> contains list with no keys. Returned <code>String</code> is compared to have
     * expected value.
     */
    @Test
    void serializeListWithNoKeysTest() {
        assertApiPath("serializer-test:list-no-key",
            YangInstanceIdentifier.of(
                QName.create("serializer:test", "2016-06-06", "list-no-key"),
                QName.create("serializer:test", "2016-06-06", "list-no-key")));
    }

    /**
     * Negative test of serialization of <code>YangInstanceIdentifier</code> to <code>String</code> when serialized
     * <code>YangInstanceIdentifier</code> contains a keyed list, but the path argument does not specify them.
     */
    @Test
    void serializeMapWithNoKeysTest() {
        final var error = assertError(YangInstanceIdentifier.builder()
            .node(QName.create("serializer:test", "2016-06-06", "list-one-key"))
            .nodeWithKey(QName.create("serializer:test", "2016-06-06", "list-one-key"), Map.of())
            .build());
        assertEquals(new ErrorMessage("""
            Argument '(serializer:test?revision=2016-06-06)list-one-key[{}]' does not match required keys \
            [(serializer:test?revision=2016-06-06)name]"""), error.message());
        assertEquals(ErrorType.APPLICATION, error.type());
        assertEquals(ErrorTag.INVALID_VALUE, error.tag());
    }

    /**
     * Positive test of serialization of <code>YangInstanceIdentifier</code> to <code>String</code> when serialized
     * <code>YangInstanceIdentifier</code> contains list with one key. Returned <code>String</code> is compared to have
     * expected value.
     */
    @Test
    void serializeMapWithOneKeyTest() {
        assertApiPath("serializer-test:list-one-key=value",
            YangInstanceIdentifier.builder()
                .node(QName.create("serializer:test", "2016-06-06", "list-one-key"))
                .nodeWithKey(QName.create("serializer:test", "2016-06-06", "list-one-key"),
                    QName.create("serializer:test", "2016-06-06", "name"), "value")
                .build());
    }

    /**
     * Positive test of serialization of <code>YangInstanceIdentifier</code> to <code>String</code> when serialized
     * <code>YangInstanceIdentifier</code> contains list with multiple keys. Returned <code>String</code> is compared
     * to have expected value.
     */
    @Test
    void serializeMapWithMultipleKeysTest() {
        final var list = QName.create("serializer:test", "2016-06-06", "list-multiple-keys");

        assertApiPath("serializer-test:list-multiple-keys=value-1,2,true",
            YangInstanceIdentifier.builder()
                .node(list)
                .nodeWithKey(list, ImmutableMap.of(
                    QName.create(list, "name"), "value-1",
                    QName.create(list, "number"), Uint8.TWO,
                    QName.create(list, "enabled"), Boolean.TRUE))
                .build());
    }

    /**
     * Positive test of serialization of <code>YangInstanceIdentifier</code> to <code>String</code> when serialized
     * <code>YangInstanceIdentifier</code> contains list with <code>YangInstanceIdentifier</code> as key.
     * Returned <code>String</code> is compared to have expected value.
     */
    @Test
    void serializeMapWithIIDKeyTest() {
        assertApiPath("""
            serializer-test:container-iid-key/list-iid-key=%2Fserializer-test-included%3Aiid-container%2Fiid%5Bid%3D%27\
            0%27%5D""",
            YangInstanceIdentifier.builder()
                .node(QName.create("serializer:test", "2016-06-06", "container-iid-key"))
                .node(QName.create("serializer:test", "2016-06-06", "list-iid-key"))
                .nodeWithKey(QName.create("serializer:test", "2016-06-06", "list-iid-key"),
                    QName.create("serializer:test", "2016-06-06", "name"),
                    YangInstanceIdentifier.builder()
                        .node(QName.create("serializer:test:included", "2016-06-06", "iid-container"))
                        .node(QName.create("serializer:test:included", "2016-06-06", "iid"))
                        .nodeWithKey(QName.create("serializer:test:included", "2016-06-06", "iid"),
                            QName.create("serializer:test:included", "2016-06-06", "id"), 0)
                        .build())
                .build());
    }

    /**
     * Positive test of serialization of <code>YangInstanceIdentifier</code> to <code>String</code> when serialized
     * <code>YangInstanceIdentifier</code> contains leaf node. Returned <code>String</code> is compared to have
     * expected value.
     */
    @Test
    void serializeLeafTest() {
        assertApiPath("serializer-test:leaf-0",
            YangInstanceIdentifier.of(QName.create("serializer:test", "2016-06-06", "leaf-0")));
    }

    /**
     * Positive test of serialization of <code>YangInstanceIdentifier</code> to <code>String</code> when serialized
     * <code>YangInstanceIdentifier</code> contains leaf list node. Returned <code>String</code> is compared to have
     * expected value.
     */
    @Test
    void serializeLeafListTest() {
        assertApiPath("serializer-test:leaf-list-0=true",
            YangInstanceIdentifier.builder()
                .node(QName.create("serializer:test", "2016-06-06", "leaf-list-0"))
                .node(new NodeWithValue<>(QName.create("serializer:test", "2016-06-06", "leaf-list-0"), Boolean.TRUE))
                .build());
    }

    /**
     * Negative test of serialization <code>YangInstanceIdentifier</code> to <code>String</code> when supplied
     * <code>YangInstanceIdentifier</code> is <code>null</code>. Test is expected to fail with
     * <code>NullPointerException</code>.
     */
    @Test
    void serializeNullDataNegativeTest() {
        assertThrows(NullPointerException.class, () -> CANONIZER.dataToApiPath(null));
    }

    /**
     * Test of serialization <code>YangInstanceIdentifier</code> to <code>String</code> when supplied
     * <code>YangInstanceIdentifier</code> is <code>YangInstanceIdentifier.EMPTY</code>.
     * Empty <code>String</code> is expected as a return value.
     */
    @Test
    void serializeEmptyDataTest() {
        assertApiPath("", YangInstanceIdentifier.of());
    }

    /**
     * Negative test when it is not possible to find child node of current node. Test is expected to fail with
     * {@link RestconfDocumentedException} and error message is compared to expected error message.
     */
    @Test
    void serializeChildNodeNotFoundNegativeTest() {
        final var error = assertError(YangInstanceIdentifier.of(
            QName.create("serializer:test", "2016-06-06", "contA"),
            QName.create("serializer:test", "2016-06-06", "not-existing-leaf")));
        assertEquals(new ErrorMessage("""
            Invalid input '/(serializer:test?revision=2016-06-06)contA/not-existing-leaf': schema for argument \
            '(serializer:test?revision=2016-06-06)not-existing-leaf' (after 'serializer-test:contA') not found"""),
            error.message());
        assertEquals(ErrorType.APPLICATION, error.type());
        assertEquals(ErrorTag.UNKNOWN_ELEMENT, error.tag());
    }

    /**
     * Test if URIs with percent encoded characters are all correctly serialized.
     */
    @Test
    void serializePercentEncodingTest() {
        assertApiPath("serializer-test:list-one-key=foo%3Afoo bar%2Ffoo%2Cbar%2F%27bar%27",
            YangInstanceIdentifier.builder()
                .node(QName.create("serializer:test", "2016-06-06", "list-one-key"))
                .nodeWithKey(QName.create("serializer:test", "2016-06-06", "list-one-key"),
                    QName.create("serializer:test", "2016-06-06", "name"), "foo:foo bar/foo,bar/'bar'")
                .build());
    }

    /**
     * Test if URIs with no percent encoded characters are correctly serialized. Input should be untouched.
     */
    @Test
    void serializeNoPercentEncodingTest() {
        assertApiPath("serializer-test:list-one-key=foo\"b\"bar",
            YangInstanceIdentifier.builder()
                .node(QName.create("serializer:test", "2016-06-06", "list-one-key"))
                .nodeWithKey(QName.create("serializer:test", "2016-06-06", "list-one-key"),
                    QName.create("serializer:test", "2016-06-06", "name"), "foo\"b\"bar")
                .build());
    }

    /**
     * Test of serialization when nodes in input <code>YangInstanceIdentifier</code> are defined in two different
     * modules by using augmentation.
     */
    @Test
    void serializeIncludedNodesTest() {
        final var list = QName.create("serializer:test:included", "2016-06-06", "augmented-list");
        final var child = QName.create("serializer:test", "2016-06-06", "augmented-leaf");

        assertApiPath("serializer-test-included:augmented-list=100/serializer-test:augmented-leaf",
            YangInstanceIdentifier.builder()
                .node(list)
                .node(NodeIdentifierWithPredicates.of(list, QName.create(list, "list-key"), 100))
                .node(child)
                .build());
    }

    /**
     * Test of serialization when nodes in input <code>YangInstanceIdentifier</code> are defined in two different
     * modules by using augmentation. Augmented node in data supplied for serialization has wrong namespace.
     * <code>RestconfDocumentedException</code> is expected because augmented node is defined in other module than its
     * parent and will not be found.
     */
    @Test
    void serializeIncludedNodesSerializationTest() {
        final var list = QName.create("serializer:test:included", "2016-06-06", "augmented-list");

        final var error = assertError(YangInstanceIdentifier.builder()
            .node(list)
            .node(NodeIdentifierWithPredicates.of(list, QName.create(list, "list-key"), 100))
            // child should has different namespace
            .node(QName.create("serializer:test:included", "2016-06-06", "augmented-leaf"))
            .build());
        assertEquals(new ErrorMessage("""
            Invalid input '/(serializer:test:included?revision=2016-06-06)augmented-list/augmented-list[{(\
            serializer:test:included?revision=2016-06-06)list-key=100}]/augmented-leaf': schema for argument \
            '(serializer:test:included?revision=2016-06-06)augmented-leaf' (after \
            'serializer-test-included:augmented-list=100') not found"""), error.message());
        assertEquals(ErrorType.APPLICATION, error.type());
        assertEquals(ErrorTag.UNKNOWN_ELEMENT, error.tag());
    }

    /**
     * Positive test of deserialization URI <code>String</code> to <code>YangInstanceIdentifier</code> and
     * serialization of <code>YangInstanceIdentifier</code> to <code>String</code> when original <code>String</code>
     * URI contains list identifier and leaf identifier.
     */
    @Test
    void codecListAndLeafTest() {
        final var dataYangII = assertNormalized("list-test:top/list1=%2C%27\"%3A\"%20%2F,,foo/list2=a,b/result");
        final var list1 = QName.create("list:test", "2016-04-29", "list1");
        final var list2 = QName.create("list:test", "2016-04-29", "list2");
        assertEquals(YangInstanceIdentifier.builder()
            .node(QName.create("list:test", "2016-04-29", "top"))
            .node(list1)
            .nodeWithKey(list1, Map.<QName, Object>of(
                QName.create(list1, "key1"), ",'\":\" /",
                QName.create(list1, "key2"), "",
                QName.create(list1, "key3"), "foo"))
            .node(list2)
            .nodeWithKey(list2, Map.<QName, Object>of(
                QName.create(list2, "key4"), "a",
                QName.create(list2, "key5"), "b"))
            .node(QName.create("list:test", "2016-04-29", "result"))
            .build(), dataYangII);
        assertApiPath("list-test:top/list1=%2C%27\"%3A\" %2F,,foo/list2=a,b/result", dataYangII);
    }

    /**
     * Positive test of deserialization URI <code>String</code> to <code>YangInstanceIdentifier</code> and
     * serialization of <code>YangInstanceIdentifier</code> to <code>String</code> when original <code>String</code>
     * URI contains leaf list identifier.
     */
    @Test
    void codecLeafListTest() {
        final var str = "list-test:top/Y=4";
        final var dataYangII = assertNormalized(str);
        final var y = QName.create("list:test", "2016-04-29", "Y");
        assertEquals(YangInstanceIdentifier.builder()
            .node(QName.create("list:test", "2016-04-29", "top"))
            .node(y)
            .node(new NodeWithValue<>(y, Uint32.valueOf(4)))
            .build(), dataYangII);
        assertApiPath(str, dataYangII);
    }

    private static void assertApiPath(final String expected, final YangInstanceIdentifier path) {
        try {
            assertEquals(newApiPath(expected), CANONIZER.dataToApiPath(path));
        } catch (ServerException e) {
            throw new AssertionError(e);
        }
    }

    private static YangInstanceIdentifier assertNormalized(final String str) {
        try {
            return assertInstanceOf(Data.class, NORMALIZER.normalizePath(newApiPath(str))).instance();
        } catch (ServerException e) {
            throw new AssertionError(e);
        }
    }

    private static ServerError assertError(final YangInstanceIdentifier path) {
        final var errors = assertThrows(ServerException.class, () -> CANONIZER.dataToApiPath(path)).errors();
        assertEquals(1, errors.size());
        return errors.get(0);
    }

    private static ApiPath newApiPath(final String apiPath) {
        try {
            return ApiPath.parse(apiPath);
        } catch (ParseException e) {
            throw new AssertionError(e);
        }
    }
}
