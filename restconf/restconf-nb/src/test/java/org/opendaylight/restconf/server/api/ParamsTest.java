/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.restconf.api.QueryParameters;
import org.opendaylight.restconf.api.query.ContentParam;
import org.opendaylight.restconf.api.query.DepthParam;
import org.opendaylight.restconf.api.query.FieldsParam;
import org.opendaylight.restconf.api.query.RestconfQueryParam;
import org.opendaylight.restconf.api.query.WithDefaultsParam;
import org.opendaylight.restconf.nb.rfc8040.Insert;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.MdsalRestconfStrategy;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContext;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.stmt.ContainerEffectiveStatement;

@ExtendWith(MockitoExtension.class)
class ParamsTest {
    /**
     * Test when not allowed parameter type is used.
     */
    @Test
    void checkParametersTypesNegativeTest() {
        final var mockDatabind = DatabindContext.ofModel(mock(EffectiveModelContext.class));

        assertInvalidIAE(EventStreamGetParams::of);
        assertInvalidIAE(EventStreamGetParams::of, ContentParam.ALL);

        assertParamsThrows("Unknown parameter in /data GET: insert", DataGetParams::of, "insert",
            "odl-test-value");

        assertInvalidIAE(queryParams -> Insert.of(mockDatabind, queryParams));
        assertInvalidIAE(queryParams -> Insert.of(mockDatabind, queryParams), ContentParam.ALL);
    }

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

    /**
     * Test of parsing user defined parameters from URI request.
     */
    @Test
    void parseUriParametersUserDefinedTest() throws Exception {
        final QName containerChild = QName.create("ns", "container-child");

        final var params = assertParams(DataGetParams::of, QueryParameters.of(
            ContentParam.CONFIG, DepthParam.of(10), FieldsParam.forUriValue("container-child")));
        // content
        assertEquals(ContentParam.CONFIG, params.content());

        // depth
        final var depth = params.depth();
        assertNotNull(depth);
        assertEquals(10, depth.value());

        // fields
        final var paramsFields = params.fields();
        assertNotNull(paramsFields);

        // fields for write filtering
        final var containerSchema = mock(ContainerSchemaNode.class,
            withSettings().extraInterfaces(ContainerEffectiveStatement.class));
        final var containerQName = QName.create(containerChild, "container");
        doReturn(containerQName).when(containerSchema).getQName();
        final var containerChildSchema = mock(LeafSchemaNode.class);
        doReturn(containerChild).when(containerChildSchema).getQName();
        doReturn(containerChildSchema).when(containerSchema).dataChildByName(containerChild);

        final var fields = MdsalRestconfStrategy.translateFieldsParam(mock(EffectiveModelContext.class),
            DataSchemaContext.of(containerSchema), paramsFields);
        assertNotNull(fields);
        assertEquals(1, fields.size());
        assertEquals(Set.of(containerChild), fields.get(0));
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
