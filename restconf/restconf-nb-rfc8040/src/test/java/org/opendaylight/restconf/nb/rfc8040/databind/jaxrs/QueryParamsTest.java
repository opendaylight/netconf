/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.databind.jaxrs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.util.List;
import javax.ws.rs.core.MultivaluedHashMap;
import org.junit.Test;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.restconf.nb.rfc8040.ContentParameter;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;

public class QueryParamsTest {
    /**
     * Test when parameter is present at most once.
     */
    @Test
    public void getSingleParameterTest() {
        final MultivaluedHashMap<String, String> parameters = new MultivaluedHashMap<>();
        parameters.putSingle(ContentParameter.uriName(), "all");
        assertEquals("all", QueryParams.getSingleParameter(parameters, ContentParameter.uriName()));
    }

    /**
     * Test when parameter is present more than once.
     */
    @Test
    public void getSingleParameterNegativeTest() {
        final MultivaluedHashMap<String, String> parameters = new MultivaluedHashMap<>();
        parameters.put(ContentParameter.uriName(), List.of("config", "nonconfig", "all"));

        final RestconfDocumentedException ex = assertThrows(RestconfDocumentedException.class,
            () -> QueryParams.getSingleParameter(parameters, ContentParameter.uriName()));
        final List<RestconfError> errors = ex.getErrors();
        assertEquals(1, errors.size());

        final RestconfError error = errors.get(0);
        assertEquals("Error type is not correct", ErrorType.PROTOCOL, error.getErrorType());
        assertEquals("Error tag is not correct", ErrorTag.INVALID_VALUE, error.getErrorTag());
    }
}
