/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.parser.builder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.collect.Iterables;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opendaylight.controller.md.sal.rest.common.TestRestconfUtils;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

/**
 * Unit tests for {@link YangInstanceIdentifierDeserializer}
 */
public class YangInstanceIdentifierDeserializerTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();
    private SchemaContext schemaContext;

    @Before
    public void init() throws Exception {
        schemaContext = TestRestconfUtils.loadSchemaContext("/restconf/parser/deserializer");
    }

    /**
     * Test of deserialization <code>String</code> URI with container to <code>YangInstanceIdentifier</code>.
     */
    @Test
    public void deserializeContainerTest() {
        final Iterable<YangInstanceIdentifier.PathArgument> result = YangInstanceIdentifierDeserializer
                .create(schemaContext, "/deserializer-test:contA");

        assertEquals("Result does not contains expected number of paths", 1, Iterables.size(result));
        assertEquals("Not expected path argument",
                YangInstanceIdentifier.NodeIdentifier.create(QName.create("deserializer:test", "2016-06-06", "contA")),
                result.iterator().next());
    }

    /**
     * Test of deserialization <code>String</code> URI with container containing leaf to
     * <code>YangInstanceIdentifier</code>.
     */
    @Test
    public void deserializeContainerWithLeafTest() {
        final Iterable<YangInstanceIdentifier.PathArgument> result = YangInstanceIdentifierDeserializer
                .create(schemaContext, "/deserializer-test:contA/leaf-A");

        assertEquals("Result does not contains expected number of paths", 2, Iterables.size(result));

        final Iterator<YangInstanceIdentifier.PathArgument> iterator = result.iterator();
        assertEquals("Not expected path argument",
                YangInstanceIdentifier.NodeIdentifier.create(QName.create("deserializer:test", "2016-06-06", "contA")),
                iterator.next());
        assertEquals("Not expected path argument",
                YangInstanceIdentifier.NodeIdentifier.create(QName.create("deserializer:test", "2016-06-06", "leaf-A")),
                iterator.next());
    }

    @Test
    public void deserializeContainerWithListWithLeafListTest() {
        final Iterable<YangInstanceIdentifier.PathArgument> result = YangInstanceIdentifierDeserializer
                .create(schemaContext, "/deserializer-test:contA/list-A=100/leaf-list-AA=value");

        assertEquals("Result does not contains expected number of paths", 5, Iterables.size(result));
        
        final Iterator<YangInstanceIdentifier.PathArgument> iterator = result.iterator();

        // container
        assertEquals("Not expected path argument",
                YangInstanceIdentifier.NodeIdentifier.create(QName.create("deserializer:test", "2016-06-06", "contA")),
                iterator.next());

        // list
        final QName list = QName.create("deserializer:test", "2016-06-06", "list-A");
        assertEquals("Not expected path argument",
                YangInstanceIdentifier.NodeIdentifier.create(list),
                iterator.next());
        assertEquals("Not expected path argument",
                new YangInstanceIdentifier.NodeIdentifierWithPredicates(list, QName.create(list, "list-key"), 100).toString(),
                iterator.next().toString());

        // leaf list
        final QName leafList = QName.create("deserializer:test", "2016-06-06", "leaf-list-AA");
        assertEquals("Not expected path argument",
                YangInstanceIdentifier.NodeIdentifier.create(leafList),
                iterator.next());
        assertEquals("Not expected path argument",
                new YangInstanceIdentifier.NodeWithValue(leafList, "instance"),
                iterator.next());
    }

    @Test
    public void deserializeListWithNoKeysTest() {
        final Iterable<YangInstanceIdentifier.PathArgument> result = YangInstanceIdentifierDeserializer
                .create(schemaContext, "/deserializer-test:list-no-key");

        // fixme why not only 1?
        assertEquals("Result does not contains expected number of paths", 2, Iterables.size(result));

        final Iterator<YangInstanceIdentifier.PathArgument> iterator = result.iterator();
        final QName list = QName.create("deserializer:test", "2016-06-06", "list-no-key");

        assertEquals("Not expected path argument",
                YangInstanceIdentifier.NodeIdentifier.create(list),
                iterator.next());
        assertEquals("Not expected path argument",
                YangInstanceIdentifier.NodeIdentifier.create(list),
                iterator.next());
    }

    @Test
    public void deserializeListWithOneKeyTest() {
        final Iterable<YangInstanceIdentifier.PathArgument> result = YangInstanceIdentifierDeserializer
                .create(schemaContext, "/deserializer-test:list-one-key=value");

        assertEquals("Result does not contains expected number of paths", 2, Iterables.size(result));

        final Iterator<YangInstanceIdentifier.PathArgument> iterator = result.iterator();
        final QName list = QName.create("deserializer:test", "2016-06-06", "list-one-key");

        assertEquals("Not expected path argument",
                YangInstanceIdentifier.NodeIdentifier.create(list),
                iterator.next());
        assertEquals("Not expected path argument",
                new YangInstanceIdentifier.NodeIdentifierWithPredicates(list, QName.create(list, "name"), "value"),
                iterator.next());
    }

    @Test
    public void deserializeListWithMultipleKeysTest() {
        final QName list = QName.create("deserializer:test", "2016-06-06", "list-multiple-keys");
        final Map<QName, Object> values = new LinkedHashMap<>();
        values.put(QName.create(list, "name"), "value");
        values.put(QName.create(list, "number"), 100);
        values.put(QName.create(list, "enabled"), false);

        final Iterable<YangInstanceIdentifier.PathArgument> result = YangInstanceIdentifierDeserializer
                .create(schemaContext, "/deserializer-test:list-multiple-keys=value,100,false");

        assertEquals("Result does not contains expected number of paths", 2, Iterables.size(result));

        final Iterator<YangInstanceIdentifier.PathArgument> iterator = result.iterator();

        assertEquals("Not expected path argument",
                YangInstanceIdentifier.NodeIdentifier.create(list),
                iterator.next());
        assertEquals("Not expected path argument",
                new YangInstanceIdentifier.NodeIdentifierWithPredicates(list, values).toString(),
                iterator.next().toString()); //fixme
    }

    /**
     * Positive test of deserialization of supplied data containing leaf list
     */
    @Test
    public void deserializeLeafListTest() {
        final Iterable<YangInstanceIdentifier.PathArgument> result = YangInstanceIdentifierDeserializer
                .create(schemaContext, "/deserializer-test:leaf-list-0=true");

        assertEquals("Result does not contains expected number of paths", 2, Iterables.size(result));

        final Iterator<YangInstanceIdentifier.PathArgument> iterator = result.iterator();
        final QName leafList = QName.create("deserializer:test", "2016-06-06", "leaf-list-0");

        assertEquals("Not expected path argument",
                new YangInstanceIdentifier.NodeIdentifier(leafList),
                iterator.next());
        assertEquals("Not expected path argument",
                new YangInstanceIdentifier.NodeWithValue(leafList, true).toString(),
                iterator.next().toString()); //fixme
    }

    /**
     * Negative test when supplied <code>SchemaContext</code> is null. Test is expected to fail with
     * <code>NullPointerException</code>.
     */
    @Test
    public void deserializeNullSchemaContextNegativeTest() {
        thrown.expect(NullPointerException.class);
        YangInstanceIdentifierDeserializer.create(null, "/deserializer-test:contA");
    }

    /**
     * Negative test when supplied data to deserialize is null. Test is expected to fail with
     * <code>NullPointerException</code>.
     */
    @Test
    public void nullDataNegativeTest() {
        thrown.expect(NullPointerException.class);
        YangInstanceIdentifierDeserializer.create(schemaContext, null);
    }

    /**
     * Positive test when empty data is supplied as an input. Deserialization should return empty result.
     */
    @Test
    public void deserializeEmptyDataDeserializeTest() {
        final Iterable<YangInstanceIdentifier.PathArgument> result =
                YangInstanceIdentifierDeserializer.create(schemaContext, "");

        assertTrue("Result does not contains expected number of paths", Iterables.isEmpty(result));
    }

    /**
     * Negative test when identifier is not followed by slash or equals. Test is expected to fail with
     * <code>IllegalArgumentException</code> and error message is verified against expected error message.
     */
    @Test
    public void deserializeBadCharMissingSlashNegativeTest() {
        thrown.expect(IllegalArgumentException.class);
        YangInstanceIdentifierDeserializer.create(null, "/deserializer-test:cont*leaf-A");
    }

    /**
     * Negative test when identifier is not followed by slash or equals. Test is expected to fail with
     * <code>IllegalArgumentException</code> and error message is verified against expected error message.
     */
    @Test
    public void deserializeBadCharMissingEqualNegativeTest() {
        thrown.expect(IllegalArgumentException.class);
        YangInstanceIdentifierDeserializer.create(null, "/deserializer-test:list-one-key*value");
    }

    // -- validArg() testing

    /**
     * Negative test of validating identifier when identifier does not start with slash.
     * <code>IllegalArgumentException</code> is expected and error message is verified against expected message.
     */
    @Test
    public void deserializeNoBeginningSlashNegativeTest() {
        thrown.expect(IllegalArgumentException.class);
        YangInstanceIdentifierDeserializer.create(null, "deserializer-test:contA");
    }

    /**
     * Negative test of validating identifier when identifier contains slash only.
     * <code>IllegalArgumentException</code> is expected and error message is verified against expected message.
     */
    @Test
    public void validArgOnlySlashNegativeTest() {
        thrown.expect(IllegalArgumentException.class);
        YangInstanceIdentifierDeserializer.create(null, "/");
    }

    /**
     * Negative test of validating identifier when identifier ends with slash. Test is expected to fail with
     * <code>IllegalArgumentException</code> and error message is verified against expected error message.
     */
    @Test
    public void validArgIdentifierEndsWithSlash() {
        thrown.expect(IllegalArgumentException.class);
        YangInstanceIdentifierDeserializer.create(null, "/deserializer-test:contA/");
    }

    // -- validArg testing

    // -- prepareQName testing

    /**
     * Negative test of creating <code>QName</code> when identifier is empty (example: '//'). Test is expected to fail
     * with <code>IllegalArgumentException</code> and error message is verified against expected message.
     */
    @Test
    public void prepareQnameEmptyIdentifierNegativeTest() {
        thrown.expect(IllegalArgumentException.class);
        YangInstanceIdentifierDeserializer.create(null, "//");
    }

    /**
     * Negative test of creating <code>QName</code> when two identifiers are separated by two slashes. Test is
     * expected to fail with <code>IllegalArgumentException</code> and error message is verified against expected
     * error message.
     */
    @Test
    public void prepareQnameTwoSlashesNegativeTest() {
        thrown.expect(IllegalArgumentException.class);
        YangInstanceIdentifierDeserializer.create(null, "/deserializer-test:cont//leaf-A");
    }

    /**
     * Negative test of creating <code>QName</code> when identifier there is another sign than colon or equals.
     * Test is expected to fail with <code>IllegalArgumentException</code> and error message is verified against
     * expected message.
     */
    @Ignore
    @Test //fixme
    public void prepareQnameBuildPathNegativeTest() {}

    /**
     * Negative test of creating <code>QName</code> when it is not possible to find module for specified prefix. Test is
     * expected to fail with <code>IllegalArgumentException</code> and error message is verified against expected
     * error message.
     */
    @Test
    public void prepareQnameNotExistingPrefixNegativeTest() {
        thrown.expect(IllegalArgumentException.class);
        YangInstanceIdentifierDeserializer.create(null, "/not-existing:contA");
    }

    /**
     * Negative test of creating <code>QName</code> when after prefix and colon there is not parsable identifier as
     * local name. Test is expected to fail with <code>IllegalArgumentException</code> and error message is verified
     * against expected error message.
     */
    @Test
    public void prepareQnameNotValidPrefixAndLocalNameNegativeTest() {
        thrown.expect(IllegalArgumentException.class);
        YangInstanceIdentifierDeserializer.create(null, "/deserializer-test:***");
    }

    /**
     * Negative test of creating <code>QName</code> when data ends after prefix and colon. Test is expected to fail
     * with <code>StringIndexOutOfBoundsException</code>.
     */
    @Test
    public void prepareQnameErrorParsingNegativeTest() {
        thrown.expect(StringIndexOutOfBoundsException.class);
        YangInstanceIdentifierDeserializer.create(null, "/deserializer-test:");
    }

    /**
     * Negative test of creating <code>QName</code> when after identifier and colon there is node name of unknown
     * node in current container. Test is expected to fail with <code>RestconfDocumentedException</code> and error
     * type, error tag and error status code are compared to expected values.
     */
    @Test
    public void prepareQnameNotValidContainerNameNegativeTest() {
        try {
            YangInstanceIdentifierDeserializer.create(schemaContext, ""); //fixme
            fail("Test should fail due to unknown child node in container");
        } catch (final RestconfDocumentedException e) {
            assertEquals("Not expected error type",
                    RestconfError.ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals("Not expected error tag",
                    RestconfError.ErrorTag.INVALID_VALUE, e.getErrors().get(0).getErrorTag());
            assertEquals("Not expected error status code",
                    400, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }

    /**
     * Negative test of creating <code>QName</code> when after identifier and equals there is node name of unknown
     * node in current list. Test is expected to fail with <code>RestconfDocumentedException</code> and error
     * type, error tag and error status code are compared to expected values.
     */
    @Test
    public void prepareQnameNotValidListNameNegativeTest() {
        try {
            YangInstanceIdentifierDeserializer.create(schemaContext, "/deserializer-test:list-no-key/enabled=false");
            fail("Test should fail due to unknown child node in list");
        } catch (final RestconfDocumentedException e) {
            assertEquals("Not expected error type",
                    RestconfError.ErrorType.PROTOCOL, e.getErrors().get(0).getErrorType());
            assertEquals("Not expected error tag",
                    RestconfError.ErrorTag.INVALID_VALUE, e.getErrors().get(0).getErrorTag());
            assertEquals("Not expected error status code",
                    400, e.getErrors().get(0).getErrorTag().getStatusCode());
        }
    }

    /**
     * Negative test of creating <code>QName</code> when after identifier and colon there is local name of module
     * which contains not-allowed characters. Test is expected to fail with <code>IllegalArgumentException</code>.
     */
    @Test
    public void prepareQnameIllegalCharacterNegativeTest() {
        thrown.expect(IllegalArgumentException.class);
    }
    // -- prepareQName testing

    // -- next context node
    /**
     * Negative test of traversing to next context node when desired node is not child of current node. Test is
     * expected to fail with<code>IllegalArgumentException</code>.
     * //TODO mixin
     */
    @Test
    public void deserializeNextContextNodeNegativeTest() {
        thrown.expect(IllegalArgumentException.class);
        YangInstanceIdentifierDeserializer.create(schemaContext, "/deserializer-test:contA/not-existing-leaf");
    }

    // -- next context node

    /**
     * Negative test of getting next identifier when current node is not keyed entry or value. Test is expected to
     * fail with <code>IllegalArgumentException</code> and error message is verified against expected error message.
     */
    @Test
    public void prepareIdentifierNotKeyedEntryNegativeTest() {}

    /**
     * Negative test when there is a comma also after the last key. Test is expected to fail with
     * <code>IllegalArgumentException</code>.
     */
    @Test
    public void deserializeKeysEndsWithComaNegativeTest() {
        thrown.expect(IllegalArgumentException.class);
        YangInstanceIdentifierDeserializer
                .create(schemaContext, "/deserializer-test:list-multiple-keys=value,100,false,");
    }

    /** FIXME - this is bug in current version
     * Negative test when not all keys of list are encoded. Test is expected to fail with
     * <code>RestconfDocumentedException</code> and error type, error tag and error status code are compared to
     * expected values.
     */
    @Ignore
    @Test
    public void notAllListKeysEncodedNegativeTest() {}

    /**
     * Negative test of preparing node with value when <code>QName</code> is <code>null</code>. Test is expected to
     * fail with <code>NullPointerException</code>.
     */
    @Ignore
    @Test
    public void deserializePrepareNodeWithValueNullQnameNegativeTest() {
    }

    /**
     * Negative test of preparing node with predicates when it is not possible to get <code>DataSchemaNode</code>.
     * Test is expected to fail with <code>NullPointerException</code>.
     */
    @Ignore
    @Test
    public void prepareNodeWithPredicatesNegativeTest() {}

    /**
     * Test URI with list where key value starts with percent encoded characters and ends with not percent encoded
     * character or string. The end of the key cannot be ignored.
     */
    @Test //fixme check for types?
    public void percentEncodedKeyEndsWithNoPercentEncodedChars() {
        final String URI = "/deserializer-test:list-multiple-keys=%3Afoo,%3Abar,%3Afoobar";
        final YangInstanceIdentifier identifier = YangInstanceIdentifier.create(
                YangInstanceIdentifierDeserializer.create(schemaContext, URI));

        final Iterator<Map.Entry<QName, Object>> listKeys = ((YangInstanceIdentifier.NodeIdentifierWithPredicates)
                identifier.getLastPathArgument()).getKeyValues().entrySet().iterator();

        assertEquals(":foo", listKeys.next().getValue());
        assertEquals(":bar", listKeys.next().getValue());
        assertEquals(":foobar", listKeys.next().getValue());
    }

    /**
     * Positive test when all keys of list are empty.
     */
    @Test //fixme types? defaults?
    public void deserialzieAllKeysEmptyTest() {
        final QName list = QName.create("deserializer:test", "2016-06-06", "list-multiple-keys");
        final Map<QName, Object> values = new LinkedHashMap<>();
        values.put(QName.create(list, "name"), "");
        values.put(QName.create(list, "number"), "");
        values.put(QName.create(list, "enabled"), "");

        final Iterable<YangInstanceIdentifier.PathArgument> result = YangInstanceIdentifierDeserializer
                .create(schemaContext, "/deserializer-test:list-multiple-keys=,,");

        assertEquals("Result does not contains expected number of paths", 2, Iterables.size(result));

        final Iterator<YangInstanceIdentifier.PathArgument> iterator = result.iterator();

        assertEquals("Not expected path argument",
                YangInstanceIdentifier.NodeIdentifier.create(list),
                iterator.next());
        assertEquals("Not expected path argument",
                new YangInstanceIdentifier.NodeIdentifierWithPredicates(list, values).toString(),
                iterator.next().toString()); //fixme
    }

    /**
     * When for leaf list there is no specified instance value an <code>IllegalArgumentException</code> is expected.
     */
    @Test
    public void leafListMissingKeyNegativeTest() {
        thrown.expect(IllegalArgumentException.class);
        YangInstanceIdentifierDeserializer.create(schemaContext, "/deserializer-test:leaf-list-0=");
    }
}
