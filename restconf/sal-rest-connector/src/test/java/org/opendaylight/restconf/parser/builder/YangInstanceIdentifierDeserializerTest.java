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
     * Positive test of deserialize supplied data.
     */
    @Ignore
    @Test
    public void deserializeTest() {}

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

    /** FIXME - this is bug in current version
     * Negative test when not all keys of list are encoded. Test is expected to fail with
     * <code>RestconfDocumentedException</code> and error type, error tag and error status code are compared to
     * expected values.
     */
    @Ignore
    @Test
    public void notAllListKeysEncodedNegativeTest() {}

}
