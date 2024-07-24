/*
 * Copyright © 2019 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.jaxrs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.stream.Stream;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.XML;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.opendaylight.restconf.api.ErrorMessage;
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.restconf.api.HttpStatusCode;
import org.opendaylight.restconf.api.QueryParameters;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.server.api.DatabindContext;
import org.opendaylight.restconf.server.api.ServerError;
import org.opendaylight.restconf.server.api.ServerErrorInfo;
import org.opendaylight.restconf.server.api.ServerErrorPath;
import org.opendaylight.restconf.server.api.ServerException;
import org.opendaylight.restconf.server.spi.AbstractJukeboxTest;
import org.opendaylight.restconf.server.spi.ErrorTagMapping;
import org.opendaylight.restconf.server.spi.MappingServerRequest;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;
import org.skyscreamer.jsonassert.JSONAssert;

class MappingServerRequestTest extends AbstractJukeboxTest {
    private abstract static class Req extends MappingServerRequest<Object> {
        Req() {
            super(QueryParameters.of(), PrettyPrintParam.TRUE, ErrorTagMapping.RFC8040);
        }

        @Override
        public abstract void onSuccess(Object result);

        @Override
        public abstract void onFailure(HttpStatusCode status, FormattableBody body);
    }

    private static final QNameModule MONITORING_MODULE_INFO =
        QNameModule.ofRevision("instance:identifier:patch:module", "2015-11-21");
    private static final DatabindContext DATABIND = DatabindContext.ofModel(
        YangParserTestUtils.parseYangResources(MappingServerRequestTest.class,
            "/restconf/impl/ietf-restconf@2017-01-26.yang",
            "/instance-identifier/instance-identifier-patch-module.yang"));

    /**
     * Testing entries 0 - 6: testing of media types and empty responses.
     * Testing entries 7 - 8: testing of deriving of status codes from error entries.
     * Testing entries 9 - 10: testing of serialization of different optional fields of error entries (JSON/XML).
     *
     * @return Testing data for parametrized test.
     */
    static Stream<Arguments> data() {
        final var sampleComplexError = new ServerException(List.of(
           new ServerError(ErrorType.APPLICATION, ErrorTag.BAD_ATTRIBUTE, new ErrorMessage("message 1"), "app tag #1",
                null, null),
            new ServerError(ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED, new ErrorMessage("message 2"),
                "app tag #2", null, new ServerErrorInfo("my info")),
            new ServerError(ErrorType.RPC, ErrorTag.DATA_MISSING, new ErrorMessage("message 3"), " app tag #3",
                new ServerErrorPath(DATABIND, YangInstanceIdentifier.builder()
                    .node(QName.create(MONITORING_MODULE_INFO, "patch-cont"))
                    .node(QName.create(MONITORING_MODULE_INFO, "my-list1"))
                    .nodeWithKey(QName.create(MONITORING_MODULE_INFO, "my-list1"),
                        QName.create(MONITORING_MODULE_INFO, "name"), "sample")
                    .node(QName.create(MONITORING_MODULE_INFO, "my-leaf12"))
                    .build()), new ServerErrorInfo("my error info"))),
            new IllegalStateException("cause"), "general message");

        return Stream.of(
            arguments(
                "Mapping of the exception with one error entry but null status code. This status code should"
                    + " be derived from single error entry; JSON output",
                new ServerException("Sample error message"),
                Response.status(Status.INTERNAL_SERVER_ERROR)
                    .type(JaxRsMediaTypes.APPLICATION_YANG_DATA_JSON)
                    .entity("""
                            {
                              "errors": {
                                "error": [
                                  {
                                    "error-tag": "operation-failed",
                                    "error-message": "Sample error message",
                                    "error-type": "application"
                                  }
                                ]
                              }
                            }""")
                    .build()
            ),
            arguments(
                "Mapping of the exception with two error entries but null status code. This status code should"
                    + "be derived from the first error entry that is specified; XML output",
                new ServerException(List.of(
                    new ServerError(ErrorType.APPLICATION, ErrorTag.BAD_ATTRIBUTE, "message 1"),
                    new ServerError(ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED, "message 2")),
                    new IllegalStateException("cause"), "general message"),
                Response.status(Status.BAD_REQUEST)
                    .type(JaxRsMediaTypes.APPLICATION_YANG_DATA_XML)
                    .entity("""
                            <errors xmlns="urn:ietf:params:xml:ns:yang:ietf-restconf">
                              <error>
                                <error-message>message 1</error-message>
                                <error-tag>bad-attribute</error-tag>
                                <error-type>application</error-type>
                              </error>
                              <error>
                                <error-message>message 2</error-message>
                                <error-tag>operation-failed</error-tag>
                                <error-type>application</error-type>
                              </error>
                            </errors>""")
                    .build()
            ),
            arguments("Mapping of the exception with three entries and optional entries set: error app tag"
                  + " (the first error), error info (the second error), and error path (the last error); JSON output",
                sampleComplexError,
                Response.status(Status.BAD_REQUEST)
                    .type(JaxRsMediaTypes.APPLICATION_YANG_DATA_JSON)
                    .entity("""
                            {
                              "errors": {
                                "error": [
                                  {
                                    "error-tag": "bad-attribute",
                                    "error-app-tag": "app tag #1",
                                    "error-message": "message 1",
                                    "error-type": "application"
                                  },
                                  {
                                    "error-tag": "operation-failed",
                                    "error-app-tag": "app tag #2",
                                    "error-info": "my info",
                                    "error-message": "message 2",
                                    "error-type": "application"
                                  },
                                  {
                                    "error-tag": "data-missing",
                                    "error-app-tag": " app tag #3",
                                    "error-info": "my error info",
                                    "error-message": "message 3",
                                    "error-path": "/instance-identifier-patch-module:patch-cont/\
                            my-list1[name='sample']/my-leaf12",
                                    "error-type": "rpc"
                                  }
                                ]
                              }
                            }""")
                    .build()),
            arguments("Mapping of the exception with three entries and optional entries set: error app tag"
                   + " (the first error), error info (the second error), and error path (the last error); XML output",
                sampleComplexError,
                Response.status(Status.BAD_REQUEST)
                    .type(JaxRsMediaTypes.APPLICATION_YANG_DATA_XML)
                    .entity("""
                            <errors xmlns="urn:ietf:params:xml:ns:yang:ietf-restconf">
                              <error>
                                <error-type>application</error-type>
                                <error-message>message 1</error-message>
                                <error-tag>bad-attribute</error-tag>
                                <error-app-tag>app tag #1</error-app-tag>
                              </error>
                              <error>
                                <error-type>application</error-type>
                                <error-message>message 2</error-message>
                                <error-tag>operation-failed</error-tag>
                                <error-app-tag>app tag #2</error-app-tag>
                                <error-info>my info</error-info></error>
                              <error>
                                <error-type>rpc</error-type>
                                <error-path xmlns:a="instance:identifier:patch:module">/a:patch-cont/\
                            a:my-list1[a:name='sample']/a:my-leaf12</error-path>
                                <error-message>message 3</error-message>
                                <error-tag>data-missing</error-tag>
                                <error-app-tag> app tag #3</error-app-tag>
                                <error-info>my error info</error-info>
                              </error>
                            </errors>""")
                    .build()
            )
         );
    }

    @ParameterizedTest
    @MethodSource("data")
    void testMappingOfExceptionToResponse(final String testDescription, final ServerException thrownException,
            final Response expectedResponse) throws JSONException {
        final Response response = exceptionMapper.toResponse(thrownException);
        compareResponseWithExpectation(expectedResponse, response);
    }

    @ParameterizedTest
    @MethodSource("data")
    void testFormattingJson(final String testDescription, final ServerException thrownException,
            final Response expectedResponse) throws JSONException {
        assumeTrue(expectedResponse.getMediaType().equals(JaxRsMediaTypes.APPLICATION_YANG_DATA_JSON));
        assertFormat(expectedResponse.getEntity().toString(),
            assertMapped(thrownException, new HttpStatusCode(expectedResponse.getStatus(), null))::formatToJSON,
            true);
    }

    private static void compareResponseWithExpectation(final Response expectedResponse, final Response actualResponse)
            throws JSONException {
        final String errorMessage = String.format("Actual response %s doesn't equal to expected response %s",
                actualResponse, expectedResponse);
        assertEquals(expectedResponse.getStatus(), actualResponse.getStatus(), errorMessage);
        assertEquals(expectedResponse.getMediaType(), actualResponse.getMediaType(), errorMessage);
        if (JaxRsMediaTypes.APPLICATION_YANG_DATA_JSON.equals(expectedResponse.getMediaType())) {
            JSONAssert.assertEquals(expectedResponse.getEntity().toString(),
                    actualResponse.getEntity().toString(), true);
        } else {
            final JSONObject expectedResponseInJson = XML.toJSONObject(expectedResponse.getEntity().toString());
            final JSONObject actualResponseInJson = XML.toJSONObject(actualResponse.getEntity().toString());
            JSONAssert.assertEquals(expectedResponseInJson, actualResponseInJson, true);
        }
    }

    private static FormattableBody assertMapped(final ServerException ex, final HttpStatusCode expectedCode) {
        final var req = spy(Req.class);
//        doNothing().when(req).onFailure(any(), any());
        req.completeWith(ex);
        final var captor = ArgumentCaptor.forClass(FormattableBody.class);
        verify(req).onFailure(eq(expectedCode), captor.capture());
        return captor.getValue();
    }
}
