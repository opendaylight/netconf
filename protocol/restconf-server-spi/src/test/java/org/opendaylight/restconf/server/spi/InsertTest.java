/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.databind.DatabindContext;
import org.opendaylight.restconf.api.QueryParameters;
import org.opendaylight.restconf.api.query.ContentParam;
import org.opendaylight.restconf.api.query.RestconfQueryParam;
import org.opendaylight.restconf.server.api.DataGetParams;
import org.opendaylight.restconf.server.api.EventStreamGetParams;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

@ExtendWith(MockitoExtension.class)
class InsertTest {
    @Mock
    private EffectiveModelContext modelContext;

    /**
     * Test when not allowed parameter type is used.
     */
    @Test
    void checkParametersTypesNegativeTest() {
        final var mockDatabind = DatabindContext.ofModel(modelContext);

        assertInvalidIAE(EventStreamGetParams::of);
        assertInvalidIAE(EventStreamGetParams::of, ContentParam.ALL);

        assertParamsThrows("Unknown parameter in /data GET: insert", DataGetParams::of, "insert",
            "odl-test-value");

        assertInvalidIAE(queryParams -> Insert.of(mockDatabind, queryParams));
        assertInvalidIAE(queryParams -> Insert.of(mockDatabind, queryParams), ContentParam.ALL);
    }

    private static void assertInvalidIAE(final Function<QueryParameters, ?> paramsMethod) {
        assertParamsThrows("Invalid parameter: odl-unknown-param", paramsMethod, "odl-unknown-param", "odl-test-value");
    }

    private static void assertInvalidIAE(final Function<QueryParameters, ?> paramsMethod,
            final RestconfQueryParam<?> param) {
        assertParamsThrows("Invalid parameter: " + param.paramName(), paramsMethod, param.paramName(),
            "odl-test-value");
    }

    private static void assertParamsThrows(final String expectedMessage,
            final Function<QueryParameters, ?> paramsMethod, final String name, final String value) {
        assertParamsThrows(expectedMessage, paramsMethod, QueryParameters.of(name, value));
    }

    private static void assertParamsThrows(final String expectedMessage,
            final Function<QueryParameters, ?> paramsMethod, final QueryParameters params) {
        final var ex = assertThrows(IllegalArgumentException.class, () -> paramsMethod.apply(params));
        assertEquals(expectedMessage, ex.getMessage());
    }
}
