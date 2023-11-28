/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.databind.jaxrs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriInfo;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.restconf.api.query.ContentParam;
import org.opendaylight.restconf.api.query.DepthParam;
import org.opendaylight.restconf.api.query.InsertParam;
import org.opendaylight.restconf.api.query.RestconfQueryParam;
import org.opendaylight.restconf.api.query.WithDefaultsParam;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.Insert;
import org.opendaylight.restconf.nb.rfc8040.legacy.QueryParameters;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.WriterFieldsTranslator;
import org.opendaylight.restconf.server.api.DatabindContext;
import org.opendaylight.restconf.server.api.EventStreamGetParams;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContext;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.stmt.ContainerEffectiveStatement;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class QueryParamsTest {
    /**
     * Test when parameter is present at most once.
     */
    @Test
    public void optionalParamTest() {
        assertEquals("all", QueryParams.optionalParam(ContentParam.uriName, List.of("all")));
    }

    /**
     * Test when parameter is present more than once.
     */
    @Test
    public void optionalParamMultipleTest() {
        final var ex = assertThrows(RestconfDocumentedException.class,
            () -> QueryParams.optionalParam(ContentParam.uriName, List.of("config", "nonconfig", "all")));
        final var errors = ex.getErrors();
        assertEquals(1, errors.size());

        final var error = errors.get(0);
        assertEquals("Error type is not correct", ErrorType.PROTOCOL, error.getErrorType());
        assertEquals("Error tag is not correct", ErrorTag.INVALID_VALUE, error.getErrorTag());
    }

    /**
     * Test when not allowed parameter type is used.
     */
    @Test
    public void checkParametersTypesNegativeTest() {
        final var mockDatabind = DatabindContext.ofModel(mock(EffectiveModelContext.class));
        assertInvalidIAE(EventStreamGetParams::ofQueryParameters);
        assertUnknownParam(QueryParams::newDataGetParams);
        assertInvalidIAE(queryParams -> Insert.ofQueryParameters(mockDatabind, queryParams));

        assertInvalidIAE(EventStreamGetParams::ofQueryParameters, ContentParam.ALL);
        assertInvalidParam(QueryParams::newDataGetParams, InsertParam.LAST);
        assertInvalidIAE(queryParams -> Insert.ofQueryParameters(mockDatabind, queryParams), ContentParam.ALL);
    }

    /**
     * Test of parsing default parameters from URI request.
     */
    @Test
    public void parseUriParametersDefaultTest() {
        // no parameters, default values should be used
        final var params = assertParams(QueryParams::newDataGetParams, new MultivaluedHashMap<>());
        assertEquals(ContentParam.ALL, params.content());
        assertNull(params.depth());
        assertNull(params.fields());
    }

    @Test
    public void testInvalidValueReadDataParams() {
        assertInvalidValue(QueryParams::newDataGetParams, ContentParam.uriName);
        assertInvalidValue(QueryParams::newDataGetParams, DepthParam.uriName);
        assertInvalidValue(QueryParams::newDataGetParams, WithDefaultsParam.uriName);

        // inserted value is too high
        assertInvalidValue(QueryParams::newDataGetParams, DepthParam.uriName, "65536");
        // inserted value is too low
        assertInvalidValue(QueryParams::newDataGetParams, DepthParam.uriName, "0");
    }

    /**
     * Testing parsing of with-defaults parameter which value matches 'report-all-tagged' setting.
     */
    @Test
    public void parseUriParametersWithDefaultAndTaggedTest() {
        final var params = assertParams(QueryParams::newDataGetParams, WithDefaultsParam.uriName, "report-all-tagged");
        assertEquals(WithDefaultsParam.REPORT_ALL_TAGGED, params.withDefaults());
    }

    /**
     * Testing parsing of with-defaults parameter which value matches 'report-all' setting.
     */
    @Test
    public void parseUriParametersWithDefaultAndReportAllTest() {
        final var params = assertParams(QueryParams::newDataGetParams, WithDefaultsParam.uriName, "report-all");
        assertEquals(WithDefaultsParam.REPORT_ALL, params.withDefaults());
    }

    /**
     * Testing parsing of with-defaults parameter which value doesn't match report-all or report-all-tagged patterns
     * - non-reporting setting.
     */
    @Test
    public void parseUriParametersWithDefaultAndNonTaggedTest() {
        final var params = assertParams(QueryParams::newDataGetParams, WithDefaultsParam.uriName, "explicit");
        assertEquals(WithDefaultsParam.EXPLICIT, params.withDefaults());
    }

    /**
     * Test of parsing user defined parameters from URI request.
     */
    @Test
    public void parseUriParametersUserDefinedTest() {
        final QName containerChild = QName.create("ns", "container-child");

        final var parameters = new MultivaluedHashMap<String, String>();
        parameters.putSingle("content", "config");
        parameters.putSingle("depth", "10");
        parameters.putSingle("fields", "container-child");

        final var params = assertParams(QueryParams::newDataGetParams, parameters);
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

        final var queryParameters = QueryParameters.of(params, WriterFieldsTranslator.translate(
            mock(EffectiveModelContext.class), DataSchemaContext.of(containerSchema), paramsFields));
        final var fields = queryParameters.fields();
        assertNotNull(fields);
        assertEquals(1, fields.size());
        assertEquals(Set.of(containerChild), fields.get(0));
    }

    private static void assertInvalidParam(final Function<UriInfo, ?> paramsMethod, final RestconfQueryParam<?> param) {
        final var params = new MultivaluedHashMap<String, String>();
        params.putSingle(param.paramName(), "odl-test-value");
        assertParamsThrows(ErrorTag.MALFORMED_MESSAGE, paramsMethod, params);
    }

    private static void assertInvalidIAE(final Function<Map<String, String>, ?> paramsMethod,
            final RestconfQueryParam<?> param) {
        final var ex = assertThrows(IllegalArgumentException.class,
            () -> paramsMethod.apply(Map.of(param.paramName(), "odl-test-value")));
        assertEquals("Invalid parameter: " + param.paramName(), ex.getMessage());
    }

    private static void assertInvalidIAE(final Function<Map<String, String>, ?> paramsMethod) {
        final var ex = assertThrows(IllegalArgumentException.class,
            () -> paramsMethod.apply(Map.of("odl-unknown-param", "odl-test-value")));
        assertEquals("Invalid parameter: odl-unknown-param", ex.getMessage());
    }

    private static void assertUnknownParam(final Function<UriInfo, ?> paramsMethod) {
        final var params = new MultivaluedHashMap<String, String>();
        params.putSingle("odl-unknown-param", "odl-test-value");
        assertParamsThrows(ErrorTag.UNKNOWN_ATTRIBUTE, paramsMethod, params);
    }

    private static void assertInvalidValue(final Function<UriInfo, ?> paramsMethod, final String name) {
        assertInvalidValue(paramsMethod, name, "odl-invalid-value");
    }

    private static void assertInvalidValue(final Function<UriInfo, ?> paramsMethod, final String name,
            final String value) {
        final var params = new MultivaluedHashMap<String, String>();
        params.putSingle(name, value);
        assertParamsThrows(ErrorTag.INVALID_VALUE, paramsMethod, params);
    }

    private static void assertParamsThrows(final ErrorTag expectedTag, final Function<UriInfo, ?> paramsMethod,
            final MultivaluedMap<String, String> params) {
        final var uriInfo = mock(UriInfo.class);
        doReturn(params).when(uriInfo).getQueryParameters();

        final var ex = assertThrows(RestconfDocumentedException.class,  () -> paramsMethod.apply(uriInfo));
        final var errors = ex.getErrors();
        assertEquals(1, errors.size());

        final var error = errors.get(0);
        assertEquals(ErrorType.PROTOCOL, error.getErrorType());
        assertEquals(expectedTag, error.getErrorTag());
    }

    private static <T> T assertParams(final Function<UriInfo, T> paramsMethod, final String name,
            final String value) {
        final var params = new MultivaluedHashMap<String, String>();
        params.putSingle(name, value);
        return assertParams(paramsMethod, params);
    }

    private static <T> T assertParams(final Function<UriInfo, T> paramsMethod,
            final MultivaluedMap<String, String> params) {
        final var uriInfo = mock(UriInfo.class);
        doReturn(params).when(uriInfo).getQueryParameters();
        return paramsMethod.apply(uriInfo);
    }
}
