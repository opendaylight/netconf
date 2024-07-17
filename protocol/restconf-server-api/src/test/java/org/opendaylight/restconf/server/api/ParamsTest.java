/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.opendaylight.restconf.api.QueryParameters;
import org.opendaylight.restconf.api.query.ContentParam;
import org.opendaylight.restconf.api.query.DepthParam;
import org.opendaylight.restconf.api.query.RestconfQueryParam;
import org.opendaylight.restconf.api.query.WithDefaultsParam;

class ParamsTest {
    /**
     * Test of parsing default parameters from URI request.
     */
    @Test
    void parseUriParametersDefaultTest() {
        // no parameters, default values should be used
        final var params = assertParams(DataGetParams::of, QueryParameters.of());
        assertEquals(ContentParam.ALL, params.content());
        assertNull(params.depth());
        assertNull(params.fields());
    }

    @Test
    void testInvalidValueReadDataParams() {
        assertParamsThrows(
            "Invalid content value: Value can be 'all', 'config' or 'nonconfig', not 'odl-invalid-value'",
            DataGetParams::of, ContentParam.uriName, "odl-invalid-value");
        assertParamsThrows("""
            The depth parameter must be "unbounded" or an integer between 1 and 65535. "odl-invalid-value" is not a \
            valid integer""", DataGetParams::of, DepthParam.uriName, "odl-invalid-value");
        assertParamsThrows("Invalid with-defaults value: \"odl-invalid-value\" is not a valid name",
            DataGetParams::of, WithDefaultsParam.uriName, "odl-invalid-value");

        // inserted value is too high
        assertParamsThrows("""
            The depth parameter must be "unbounded" or an integer between 1 and 65535. 65536 is not between 1 and 65535\
            """, DataGetParams::of, DepthParam.uriName, "65536");
        // inserted value is too low
        assertParamsThrows("""
            The depth parameter must be "unbounded" or an integer between 1 and 65535. 0 is not between 1 and 65535""",
            DataGetParams::of, DepthParam.uriName, "0");
    }

    /**
     * Testing parsing of with-defaults parameter which value matches 'report-all-tagged' setting.
     */
    @Test
    void parseUriParametersWithDefaultAndTaggedTest() {
        final var params = assertParams(DataGetParams::of, WithDefaultsParam.uriName,
            "report-all-tagged");
        assertEquals(WithDefaultsParam.REPORT_ALL_TAGGED, params.withDefaults());
    }

    /**
     * Testing parsing of with-defaults parameter which value matches 'report-all' setting.
     */
    @Test
    void parseUriParametersWithDefaultAndReportAllTest() {
        final var params = assertParams(DataGetParams::of, WithDefaultsParam.uriName,
            "report-all");
        assertEquals(WithDefaultsParam.REPORT_ALL, params.withDefaults());
    }

    /**
     * Testing parsing of with-defaults parameter which value doesn't match report-all or report-all-tagged patterns
     * - non-reporting setting.
     */
    @Test
    void parseUriParametersWithDefaultAndNonTaggedTest() {
        final var params = assertParams(DataGetParams::of, WithDefaultsParam.uriName,
            "explicit");
        assertEquals(WithDefaultsParam.EXPLICIT, params.withDefaults());
    }

    private static void assertInvalidIAE(final Function<QueryParameters, ?> paramsMethod,
            final RestconfQueryParam<?> param) {
        assertParamsThrows("Invalid parameter: " + param.paramName(), paramsMethod, param.paramName(),
            "odl-test-value");
    }

    private static void assertInvalidIAE(final Function<QueryParameters, ?> paramsMethod) {
        assertParamsThrows("Invalid parameter: odl-unknown-param", paramsMethod, "odl-unknown-param", "odl-test-value");
    }

    private static void assertParamsThrows(final String expectedMessage,
            final Function<QueryParameters, ?> paramsMethod, final String name, final String value) {
        assertParamsThrows(expectedMessage, paramsMethod, QueryParameters.of(name, value));
    }

    private static void assertParamsThrows(final String expectedMessage,
            final Function<QueryParameters, ?> paramsMethod, final QueryParameters params) {
        final var ex = assertThrows(IllegalArgumentException.class, () -> assertParams(paramsMethod, params));
        assertEquals(expectedMessage, ex.getMessage());
    }

    private static <T> T assertParams(final Function<QueryParameters, T> paramsMethod, final QueryParameters params) {
        return paramsMethod.apply(params);
    }

    private static <T> T assertParams(final Function<QueryParameters, T> paramsMethod, final String name,
            final String value) {
        return assertParams(paramsMethod, QueryParameters.of(name, value));
    }
}
