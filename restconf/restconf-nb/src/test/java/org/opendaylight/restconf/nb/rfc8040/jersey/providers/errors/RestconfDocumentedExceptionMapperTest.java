/*
 * Copyright © 2019 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.jersey.providers.errors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.util.Arrays;
import java.util.List;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.XML;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.restconf.nb.jaxrs.JaxRsMediaTypes;
import org.opendaylight.restconf.server.api.DatabindContext;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.common.XMLNamespace;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;
import org.skyscreamer.jsonassert.JSONAssert;

@RunWith(Parameterized.class)
public class RestconfDocumentedExceptionMapperTest {
    private static final QNameModule MONITORING_MODULE_INFO = QNameModule.create(
        XMLNamespace.of("instance:identifier:patch:module"), Revision.of("2015-11-21"));

    private static RestconfDocumentedExceptionMapper exceptionMapper;

    @BeforeClass
    public static void setupExceptionMapper() {
        final var schemaContext = YangParserTestUtils.parseYangResources(
                RestconfDocumentedExceptionMapperTest.class, "/restconf/impl/ietf-restconf@2017-01-26.yang",
                "/instanceidentifier/yang/instance-identifier-patch-module.yang");
        exceptionMapper = new RestconfDocumentedExceptionMapper(() -> DatabindContext.ofModel(schemaContext));
    }

    /**
     * Testing entries 0 - 6: testing of media types and empty responses.
     * Testing entries 7 - 8: testing of deriving of status codes from error entries.
     * Testing entries 9 - 10: testing of serialization of different optional fields of error entries (JSON/XML).
     *
     * @return Testing data for parametrized test.
     */
    @Parameters(name = "{index}: {0}: {1}")
    public static Iterable<Object[]> data() {
        final RestconfDocumentedException sampleComplexError =
            new RestconfDocumentedException("general message", new IllegalStateException("cause"), List.of(
                new RestconfError(ErrorType.APPLICATION, ErrorTag.BAD_ATTRIBUTE, "message 1", "app tag #1"),
                new RestconfError(ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED,
                    "message 2", "app tag #2", "my info"),
                new RestconfError(ErrorType.RPC, ErrorTag.DATA_MISSING,
                    "message 3", " app tag #3", "my error info", YangInstanceIdentifier.builder()
                    .node(QName.create(MONITORING_MODULE_INFO, "patch-cont"))
                    .node(QName.create(MONITORING_MODULE_INFO, "my-list1"))
                    .nodeWithKey(QName.create(MONITORING_MODULE_INFO, "my-list1"),
                        QName.create(MONITORING_MODULE_INFO, "name"), "sample")
                    .node(QName.create(MONITORING_MODULE_INFO, "my-leaf12"))
                    .build())));

        return Arrays.asList(new Object[][] {
            {
                "Mapping of the exception with one error entry but null status code. This status code should"
                        + "be derived from single error entry; JSON output",
                new RestconfDocumentedException("Sample error message"),
                mockHttpHeaders(MediaType.APPLICATION_JSON_TYPE, List.of(JaxRsMediaTypes.APPLICATION_YANG_PATCH_JSON)),
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
            },
            {
                "Mapping of the exception with two error entries but null status code. This status code should"
                        + "be derived from the first error entry that is specified; XML output",
                new RestconfDocumentedException("general message", new IllegalStateException("cause"), List.of(
                                new RestconfError(ErrorType.APPLICATION, ErrorTag.BAD_ATTRIBUTE, "message 1"),
                                new RestconfError(ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED, "message 2"))),
                mockHttpHeaders(MediaType.APPLICATION_JSON_TYPE, List.of(JaxRsMediaTypes.APPLICATION_YANG_PATCH_XML)),
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
            },
            {
                "Mapping of the exception with three entries and optional entries set: error app tag (the first error),"
                        + " error info (the second error), and error path (the last error); JSON output",
                sampleComplexError, mockHttpHeaders(MediaType.APPLICATION_JSON_TYPE, List.of(
                        MediaType.APPLICATION_JSON_TYPE)),
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
                        .build()
            },
            {
                "Mapping of the exception with three entries and optional entries set: error app tag (the first error),"
                        + " error info (the second error), and error path (the last error); XML output",
                sampleComplexError, mockHttpHeaders(JaxRsMediaTypes.APPLICATION_YANG_PATCH_JSON,
                        List.of(JaxRsMediaTypes.APPLICATION_YANG_DATA_XML)),
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
            }
        });
    }

    @Parameter
    public String testDescription;
    @Parameter(1)
    public RestconfDocumentedException thrownException;
    @Parameter(2)
    public HttpHeaders httpHeaders;
    @Parameter(3)
    public Response expectedResponse;

    @Test
    public void testMappingOfExceptionToResponse() throws JSONException {
        exceptionMapper.setHttpHeaders(httpHeaders);
        final Response response = exceptionMapper.toResponse(thrownException);
        compareResponseWithExpectation(expectedResponse, response);
    }

    @Test
    public void testFormattingJson() throws JSONException {
        assumeTrue(expectedResponse.getMediaType().equals(JaxRsMediaTypes.APPLICATION_YANG_DATA_JSON));

        exceptionMapper.setHttpHeaders(httpHeaders);
        final Response response = exceptionMapper.toResponse(thrownException);
        assertEquals(expectedResponse.getEntity().toString(), response.getEntity().toString());
    }

    private static HttpHeaders mockHttpHeaders(final MediaType contentType, final List<MediaType> acceptedTypes) {
        final HttpHeaders httpHeaders = mock(HttpHeaders.class);
        doReturn(contentType).when(httpHeaders).getMediaType();
        doReturn(acceptedTypes).when(httpHeaders).getAcceptableMediaTypes();
        return httpHeaders;
    }

    private static void compareResponseWithExpectation(final Response expectedResponse, final Response actualResponse)
            throws JSONException {
        final String errorMessage = String.format("Actual response %s doesn't equal to expected response %s",
                actualResponse, expectedResponse);
        assertEquals(errorMessage, expectedResponse.getStatus(), actualResponse.getStatus());
        assertEquals(errorMessage, expectedResponse.getMediaType(), actualResponse.getMediaType());
        if (JaxRsMediaTypes.APPLICATION_YANG_DATA_JSON.equals(expectedResponse.getMediaType())) {
            JSONAssert.assertEquals(expectedResponse.getEntity().toString(),
                    actualResponse.getEntity().toString(), true);
        } else {
            final JSONObject expectedResponseInJson = XML.toJSONObject(expectedResponse.getEntity().toString());
            final JSONObject actualResponseInJson = XML.toJSONObject(actualResponse.getEntity().toString());
            JSONAssert.assertEquals(expectedResponseInJson, actualResponseInJson, true);
        }
    }
}
