/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.databind.jaxrs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;

import java.util.List;
import java.util.Set;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriInfo;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.restconf.nb.rfc8040.ContentParameter;
import org.opendaylight.restconf.nb.rfc8040.DepthParameter;
import org.opendaylight.restconf.nb.rfc8040.WithDefaultsParameter;
import org.opendaylight.restconf.nb.rfc8040.legacy.QueryParameters;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class QueryParamsTest {
    @Mock
    public InstanceIdentifierContext<ContainerSchemaNode> context;
    @Mock
    public UriInfo uriInfo;
    @Mock
    public EffectiveModelContext modelContext;
    @Mock
    public ContainerSchemaNode containerSchema;
    @Mock
    public LeafSchemaNode containerChildSchema;

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

    /**
     * Test when all parameters are allowed.
     */
    @Test
    public void checkParametersTypesTest() {
        QueryParams.checkParametersTypes(Set.of("content"),
            Set.of(ContentParameter.uriName(), DepthParameter.uriName()));
    }

    /**
     * Test when not allowed parameter type is used.
     */
    @Test
    public void checkParametersTypesNegativeTest() {
        final RestconfDocumentedException ex = assertThrows(RestconfDocumentedException.class,
            () -> QueryParams.checkParametersTypes(Set.of("not-allowed-parameter"),
                Set.of(ContentParameter.uriName(), DepthParameter.uriName())));
        final List<RestconfError> errors = ex.getErrors();
        assertEquals(1, errors.size());

        final RestconfError error = errors.get(0);
        assertEquals("Error type is not correct", ErrorType.PROTOCOL, error.getErrorType());
        assertEquals("Error tag is not correct", ErrorTag.INVALID_VALUE, error.getErrorTag());
    }

    /**
     * Test of parsing default parameters from URI request.
     */
    @Test
    public void parseUriParametersDefaultTest() {
        // no parameters, default values should be used
        mockQueryParameters(new MultivaluedHashMap<String, String>());

        final QueryParameters parsedParameters = QueryParams.newReadDataParams(context, uriInfo);

        assertEquals(ContentParameter.ALL, parsedParameters.getContent());
        assertNull(parsedParameters.getDepth());
        assertNull(parsedParameters.getFields());
    }

    /**
     * Testing parsing of with-defaults parameter which value which is not supported.
     */
    @Test
    public void parseUriParametersWithDefaultInvalidTest() {
        // preparation of input data
        mockQueryParameter("with-defaults", "invalid");

        final RestconfDocumentedException ex = assertThrows(RestconfDocumentedException.class,
            () -> QueryParams.newReadDataParams(context, uriInfo));
        final List<RestconfError> errors = ex.getErrors();
        assertEquals(1, errors.size());
        assertEquals(ErrorTag.INVALID_VALUE, errors.get(0).getErrorTag());
    }

    /**
     * Negative test of parsing request URI parameters when depth parameter has not allowed value.
     */
    @Test
    public void parseUriParametersDepthParameterNegativeTest() {
        // inserted value is not allowed
        mockQueryParameter("depth", "bounded");

        RestconfDocumentedException ex = assertThrows(RestconfDocumentedException.class,
            () -> QueryParams.newReadDataParams(context, uriInfo));
        // Bad request
        assertEquals("Error type is not correct", ErrorType.PROTOCOL, ex.getErrors().get(0).getErrorType());
        assertEquals("Error tag is not correct", ErrorTag.INVALID_VALUE, ex.getErrors().get(0).getErrorTag());
    }

    /**
     * Negative test of parsing request URI parameters when content parameter has not allowed value.
     */
    @Test
    public void parseUriParametersContentParameterNegativeTest() {
        mockQueryParameter("content", "not-allowed-parameter-value");

        final RestconfDocumentedException ex = assertThrows(RestconfDocumentedException.class,
            () -> QueryParams.newReadDataParams(context, uriInfo));
        // Bad request
        assertEquals("Error type is not correct", ErrorType.PROTOCOL, ex.getErrors().get(0).getErrorType());
        assertEquals("Error tag is not correct", ErrorTag.INVALID_VALUE, ex.getErrors().get(0).getErrorTag());
    }

    /**
     * Negative test of parsing request URI parameters when depth parameter has not allowed value (more than maximum).
     */
    @Test
    public void parseUriParametersDepthMaximalParameterNegativeTest() {
        // inserted value is too high
        mockQueryParameter("depth", "65536");

        RestconfDocumentedException ex = assertThrows(RestconfDocumentedException.class,
            () -> QueryParams.newReadDataParams(context, uriInfo));
        // Bad request
        assertEquals("Error type is not correct", ErrorType.PROTOCOL, ex.getErrors().get(0).getErrorType());
        assertEquals("Error tag is not correct", ErrorTag.INVALID_VALUE, ex.getErrors().get(0).getErrorTag());
    }

    /**
     * Negative test of parsing request URI parameters when depth parameter has not allowed value (less than minimum).
     */
    @Test
    public void parseUriParametersDepthMinimalParameterNegativeTest() {
        // inserted value is too low
        mockQueryParameter("depth", "0");

        RestconfDocumentedException ex = assertThrows(RestconfDocumentedException.class,
            () -> QueryParams.newReadDataParams(context, uriInfo));
        // Bad request
        assertEquals("Error type is not correct", ErrorType.PROTOCOL, ex.getErrors().get(0).getErrorType());
        assertEquals("Error tag is not correct", ErrorTag.INVALID_VALUE, ex.getErrors().get(0).getErrorTag());
    }

    /**
     * Testing parsing of with-defaults parameter which value matches 'report-all-tagged' setting - default value should
     * be set to {@code null} and tagged flag should be set to {@code true}.
     */
    @Test
    public void parseUriParametersWithDefaultAndTaggedTest() {
        // preparation of input data
        mockQueryParameter("with-defaults", "report-all-tagged");

        final QueryParameters writerParameters = QueryParams.newReadDataParams(context, uriInfo);
        assertNull(writerParameters.getWithDefault());
        assertTrue(writerParameters.isTagged());
    }

    /**
     * Testing parsing of with-defaults parameter which value matches 'report-all' setting - default value should
     * be set to {@code null} and tagged flag should be set to {@code false}.
     */
    @Test
    public void parseUriParametersWithDefaultAndReportAllTest() {
        // preparation of input data
        mockQueryParameter("with-defaults", "report-all");

        final QueryParameters writerParameters = QueryParams.newReadDataParams(context, uriInfo);
        assertNull(writerParameters.getWithDefault());
        assertFalse(writerParameters.isTagged());
    }

    /**
     * Testing parsing of with-defaults parameter which value doesn't match report-all or report-all-tagged patterns
     * - non-reporting setting.
     */
    @Test
    public void parseUriParametersWithDefaultAndNonTaggedTest() {
        // preparation of input data
        mockQueryParameter("with-defaults", "explicit");

        final QueryParameters writerParameters = QueryParams.newReadDataParams(context, uriInfo);
        assertSame(WithDefaultsParameter.EXPLICIT, writerParameters.getWithDefault());
        assertFalse(writerParameters.isTagged());
    }

    /**
     * Test of parsing user defined parameters from URI request.
     */
    @Test
    public void parseUriParametersUserDefinedTest() {
        final QName containerChild = QName.create("ns", "container-child");

        final MultivaluedMap<String, String> parameters = new MultivaluedHashMap<>();
        parameters.putSingle("content", "config");
        parameters.putSingle("depth", "10");
        parameters.putSingle("fields", "container-child");
        mockQueryParameters(parameters);

        doReturn(QName.create(containerChild, "container")).when(containerSchema).getQName();
        doReturn(containerChildSchema).when(containerSchema).dataChildByName(containerChild);
        doReturn(containerChild).when(containerChildSchema).getQName();

        doReturn(modelContext).when(context).getSchemaContext();
        doReturn(containerSchema).when(context).getSchemaNode();

        final QueryParameters parsedParameters = QueryParams.newReadDataParams(context, uriInfo);

        // content
        assertEquals(ContentParameter.CONFIG, parsedParameters.getContent());

        // depth
        final DepthParameter depth = parsedParameters.getDepth();
        assertNotNull(depth);
        assertEquals(10, depth.value());

        // fields
        assertNotNull(parsedParameters.getFields());
        assertEquals(1, parsedParameters.getFields().size());
        assertEquals(1, parsedParameters.getFields().get(0).size());
        assertEquals(containerChild, parsedParameters.getFields().get(0).iterator().next());
    }

    private void mockQueryParameter(final String name, final String value) {
        final MultivaluedMap<String, String> parameters = new MultivaluedHashMap<>();
        parameters.putSingle(name, value);
        mockQueryParameters(parameters);
    }

    private void mockQueryParameters(final MultivaluedMap<String, String> parameters) {
        doReturn(parameters).when(uriInfo).getQueryParameters();
    }
}
