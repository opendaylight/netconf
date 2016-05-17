/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.parser.builder;

import org.junit.Ignore;
import org.junit.Test;

public class YangInstanceIdentifierDeserializerTest {

    /**
     * Positive test of deserialization of supplied data containing list
     */
    @Ignore
    @Test
    public void deserializeListTest() {}

    /**
     * Positive test of deserialization of supplied data containing leaf list
     */
    @Ignore
    @Test
    public void deserializeLeafListTest() {}

    /**
     * Negative test when supplied <code>SchemaContext</code> is null. Test is expected to fail with
     * <code>NullPointerException</code>.
     */
    @Ignore
    @Test
    public void nullSchemaContextNegativeTest() {}

    /**
     * Negative test when supplied data to deserialize is null. Test is expected to fail with
     * <code>NullPointerException</code>.
     */
    @Ignore
    @Test
    public void nullDataNegativeTest() {}

    /**
     * Positive test when empty data is supplied as an input. Deserialization should return empty result.
     */
    @Ignore
    @Test
    public void emptyDataDeserializeTest() {}

    /**
     * Negative test when identifier is not followed by slash or equals. Test is expected to fail with
     * <code>IllegalArgumentException</code> and error message is verified against expected error message.
     */
    @Ignore
    @Test
    public void badCharNegativeTest() {}

    // -- validArg() testing

    /**
     * Negative test of validating identifier when identifier does not start with slash.
     * <code>IllegalArgumentException</code> is expected and error message is verified against expected message.
     */
    @Ignore
    @Test
    public void validArgNoBeginningSlashNegativeTest() {}

    /**
     * Negative test of validating identifier when identifier contains slash only.
     * <code>IllegalArgumentException</code> is expected and error message is verified against expected message.
     */
    @Ignore
    @Test
    public void validArgOnlySlashNegativeTest() {}

    /**
     * Negative test of validating identifier when identifier ends with slash. Test is expected to fail with
     * <code>IllegalArgumentException</code> and error message is verified against expected error message.
     */
    @Ignore
    @Test
    public void validArgIdentifierEndsWithSlash() {}

    // -- validArg testing

    // -- prepareQName testing

    /**
     * Negative test of creating <code>QName</code> when identifier is empty (example: '//'). Test is expected to fail
     * with <code>IllegalArgumentException</code> and error message is verified against expected message.
     */
    @Ignore
    @Test
    public void prepareQnameEmptyIdentifierNegativeTest() {}

    /**
     * Negative test of creating <code>QName</code> when two identifiers are separated by two slashes. Test is
     * expected to fail with <code>IllegalArgumentException</code> and error message is verified against expected
     * error message.
     */
    @Ignore
    @Test
    public void prepareQnameTwoSlashesNegativeTest() {}

    /**
     * Negative test of creating <code>QName</code> when identifier there is another sign than colon or equals.
     * Test is expected to fail with <code>IllegalArgumentException</code> and error message is verified against
     * expected message.
     */
    @Ignore
    @Test
    public void prepareQnameBuildPathNegativeTest() {}

    /**
     * Negative test of creating <code>QName</code> when it is not possible to find module for specified prefix. Test is
     * expected to fail with <code>IllegalArgumentException</code> and error message is verified against expected
     * error message.
     */
    @Ignore
    @Test
    public void prepareQnameNotExistingPrefixNegativeTest() {}

    /**
     * Negative test of creating <code>QName</code> when after prefix and colon there is not parsable identifier as
     * local name. Test is expected to fail with <code>IllegalArgumentException</code> and error message is verified
     * against expected error message.
     */
    @Ignore
    @Test
    public void prepareQnameNotValidPrefixAndLocalNameNegativeTest() {}

    /**
     * Negative test of creating <code>QName</code> when data ends after prefix and colon. Test is expected to fail
     * with <code>StringIndexOutOfBoundsException</code>.
     */
    @Ignore
    @Test
    public void prepareQnameErrorParsingNegativeTest() {}

    /**
     * Negative test of creating <code>QName</code> when after identifier and equals there is node name of unknown
     * node in current container. Test is expected to fail with <code>RestconfDocumentedException</code> and error
     * type, error tag and error status code are compared to expected values.
     */
    @Ignore
    @Test
    public void prepareQnameNotValidContainerNameNegativeTest() {}

    /**
     * Negative test of creating <code>QName</code> when after identifier and equals there is node name of unknown
     * node in current list. Test is expected to fail with <code>RestconfDocumentedException</code> and error
     * type, error tag and error status code are compared to expected values.
     */
    @Ignore
    @Test
    public void prepareQnameNotValidListNameNegativeTest() {}

    /**
     * Negative test of creating <code>QName</code> when after identifier and colon there is local name of module
     * which contains not-allowed characters. Test is expected to fail with <code>IllegalArgumentException</code> and
     * error message is verified against expected error message.
     */
    @Ignore
    @Test
    public void prepareQnameIllegalCharacterNegativeTest() {}

    // -- prepareQName testing

    // -- next context node
    /**
     * Negative test of traversing to next context node when desired node is not child of current node. Test is
     * expected to fail with<code>IllegalArgumentException</code> and error message is verified against expected
     * error message. //TODO mixin
     */
    @Ignore
    @Test
    public void nextContextNodeNegativeTest() {}

    // -- next context node

    /**
     * Negative test of getting next identifier when current node is not keyed entry or value. Test is expected to
     * fail with <code>IllegalArgumentException</code> and error message is verified against expected error message.
     */
    @Ignore
    @Test
    public void prepareIdentifierNotKeyedEntryNegativeTest() {}

    /**
     * Negative test when there is a comma also after the last key. Test is expected to fail with
     * <code>IllegalArgumentException</code>.
     */
    @Ignore
    @Test
    public void keysEndsWithComaNegativeTest() {}

    /** FIXME - this is bug in current version
     * Negative test when not all keys of list are encoded. Test is expected to fail with
     * <code>RestconfDocumentedException</code> and error type, error tag and error status code are compared to
     * expected values.
     */
    @Ignore
    @Test
    public void notAllListKeysEncodedNegativeTest() {}

    // FIXME prepareNodeWithValue
    // FIXME prepareNodeWithPredicates
    // FIXME findAndParsePercentEncoded

}
