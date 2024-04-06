/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.restconf.api.ImmutableQueryParameters;
import org.opendaylight.restconf.api.QueryParameters;
import org.opendaylight.restconf.api.query.ContentParam;
import org.opendaylight.restconf.api.query.DepthParam;
import org.opendaylight.restconf.api.query.InsertParam;
import org.opendaylight.restconf.api.query.RestconfQueryParam;
import org.opendaylight.restconf.api.query.WithDefaultsParam;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.Insert;
import org.opendaylight.restconf.nb.rfc8040.legacy.WriterParameters;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.MdsalRestconfStrategy;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContext;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.stmt.ContainerEffectiveStatement;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class DataGetParamsTest {
    /**
     * Test when not allowed parameter type is used.
     */
    @Test
    public void checkParametersTypesNegativeTest() {
        final var mockDatabind = DatabindContext.ofModel(mock(EffectiveModelContext.class));
        assertInvalidIAE(EventStreamGetParams::ofQueryParameters);
        assertUnknownParam(DataGetParams::ofQueryParameters);
        assertInvalidIAE(queryParams -> Insert.ofQueryParameters(mockDatabind, queryParams));

        assertInvalidIAE(EventStreamGetParams::ofQueryParameters, ContentParam.ALL);
        assertInvalidParam(DataGetParams::ofQueryParameters, InsertParam.LAST);
        assertInvalidIAE(queryParams -> Insert.ofQueryParameters(mockDatabind, queryParams), ContentParam.ALL);
    }

    /**
     * Test of parsing default parameters from URI request.
     */
    @Test
    public void parseUriParametersDefaultTest() {
        // no parameters, default values should be used
        final var params = DataGetParams.ofQueryParameters(ImmutableQueryParameters.of());
        assertEquals(ContentParam.ALL, params.content());
        assertNull(params.depth());
        assertNull(params.fields());
    }

    @Test
    public void testInvalidValueReadDataParams() {
        assertInvalidValue(DataGetParams::ofQueryParameters, ContentParam.uriName);
        assertInvalidValue(DataGetParams::ofQueryParameters, DepthParam.uriName);
        assertInvalidValue(DataGetParams::ofQueryParameters, WithDefaultsParam.uriName);

        // inserted value is too high
        assertInvalidValue(DataGetParams::ofQueryParameters, DepthParam.uriName, "65536");
        // inserted value is too low
        assertInvalidValue(DataGetParams::ofQueryParameters, DepthParam.uriName, "0");
    }

    /**
     * Testing parsing of with-defaults parameter which value matches 'report-all-tagged' setting.
     */
    @Test
    public void parseUriParametersWithDefaultAndTaggedTest() {
        final var params = assertParams(DataGetParams::ofQueryParameters, WithDefaultsParam.uriName,
            "report-all-tagged");
        assertEquals(WithDefaultsParam.REPORT_ALL_TAGGED, params.withDefaults());
    }

    /**
     * Testing parsing of with-defaults parameter which value matches 'report-all' setting.
     */
    @Test
    public void parseUriParametersWithDefaultAndReportAllTest() {
        final var params = assertParams(DataGetParams::ofQueryParameters, WithDefaultsParam.uriName,
            "report-all");
        assertEquals(WithDefaultsParam.REPORT_ALL, params.withDefaults());
    }

    /**
     * Testing parsing of with-defaults parameter which value doesn't match report-all or report-all-tagged patterns
     * - non-reporting setting.
     */
    @Test
    public void parseUriParametersWithDefaultAndNonTaggedTest() {
        final var params = assertParams(DataGetParams::ofQueryParameters, WithDefaultsParam.uriName,
            "explicit");
        assertEquals(WithDefaultsParam.EXPLICIT, params.withDefaults());
    }

    /**
     * Test of parsing user defined parameters from URI request.
     */
    @Test
    public void parseUriParametersUserDefinedTest() {
        final QName containerChild = QName.create("ns", "container-child");

        final var params = DataGetParams.ofQueryParameters(ImmutableQueryParameters.of(Map.of(
            "content", "config",
            "depth", "10",
            "fields", "container-child")));
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

        final var writerParameters = WriterParameters.of(params, MdsalRestconfStrategy.translateFieldsParam(
            mock(EffectiveModelContext.class), DataSchemaContext.of(containerSchema), paramsFields));
        final var fields = writerParameters.fields();
        assertNotNull(fields);
        assertEquals(1, fields.size());
        assertEquals(Set.of(containerChild), fields.get(0));
    }

    private static void assertInvalidParam(final Function<QueryParameters, ?> paramsMethod,
            final RestconfQueryParam<?> param) {
        assertParamsThrows(ErrorTag.MALFORMED_MESSAGE, paramsMethod,
            ImmutableQueryParameters.of(param.paramName(), "odl-test-value"));
    }

    private static void assertInvalidIAE(final Function<QueryParameters, ?> paramsMethod,
            final RestconfQueryParam<?> param) {
        final var ex = assertThrows(IllegalArgumentException.class,
            () -> paramsMethod.apply(ImmutableQueryParameters.of(param.paramName(), "odl-test-value")));
        assertEquals("Invalid parameter: " + param.paramName(), ex.getMessage());
    }

    private static void assertInvalidIAE(final Function<QueryParameters, ?> paramsMethod) {
        final var ex = assertThrows(IllegalArgumentException.class,
            () -> paramsMethod.apply(ImmutableQueryParameters.of("odl-unknown-param", "odl-test-value")));
        assertEquals("Invalid parameter: odl-unknown-param", ex.getMessage());
    }

    private static void assertUnknownParam(final Function<QueryParameters, ?> paramsMethod) {
        assertParamsThrows(ErrorTag.UNKNOWN_ATTRIBUTE, paramsMethod,
            ImmutableQueryParameters.of("odl-unknown-param", "odl-test-value"));
    }

    private static void assertInvalidValue(final Function<QueryParameters, ?> paramsMethod, final String name) {
        assertInvalidValue(paramsMethod, name, "odl-invalid-value");
    }

    private static void assertInvalidValue(final Function<QueryParameters, ?> paramsMethod, final String name,
            final String value) {
        assertParamsThrows(ErrorTag.INVALID_VALUE, paramsMethod, ImmutableQueryParameters.of(name, value));
    }

    private static void assertParamsThrows(final ErrorTag expectedTag, final Function<QueryParameters, ?> paramsMethod,
            final QueryParameters params) {
        final var ex = assertThrows(RestconfDocumentedException.class, () -> paramsMethod.apply(params));
        final var errors = ex.getErrors();
        assertEquals(1, errors.size());

        final var error = errors.get(0);
        assertEquals(ErrorType.PROTOCOL, error.getErrorType());
        assertEquals(expectedTag, error.getErrorTag());
    }

    private static <T> T assertParams(final Function<QueryParameters, T> paramsMethod, final String name,
            final String value) {
        return paramsMethod.apply(ImmutableQueryParameters.of(name, value));
    }
}
