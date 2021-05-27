/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.controller.sal.restconf.impl.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;

import org.junit.Test;
import org.opendaylight.netconf.sal.restconf.impl.RestCodec;
import org.opendaylight.yangtools.concepts.IllegalArgumentCodec;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.type.IdentityrefTypeDefinition;
import org.opendaylight.yangtools.yang.model.ri.type.BaseTypes;

public class RestCodecExceptionsTest {
    @Test
    public void serializeExceptionTest() {
        final IllegalArgumentCodec<Object, Object> codec = RestCodec.from(
            BaseTypes.bitsTypeBuilder(QName.create("test", "2014-05-30", "test")).build(), null, null);
        final String serializedValue = (String) codec.serialize("incorrect value"); // set
                                                                              // expected
        assertEquals("incorrect value", serializedValue);
    }

    @Test
    public void deserializeExceptionTest() {
        final IdentityrefTypeDefinition mockedIidentityrefType = mock(IdentityrefTypeDefinition.class);

        final IllegalArgumentCodec<Object, Object> codec = RestCodec.from(mockedIidentityrefType, null, null);
        assertNull(codec.deserialize("incorrect value"));
    }
}
