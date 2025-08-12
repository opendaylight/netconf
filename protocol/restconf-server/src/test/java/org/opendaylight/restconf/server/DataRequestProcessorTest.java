/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.opendaylight.restconf.server.TestUtils.answerCompleteWith;
import static org.opendaylight.restconf.server.TestUtils.assertContentSimplified;
import static org.opendaylight.restconf.server.TestUtils.assertResponse;
import static org.opendaylight.restconf.server.TestUtils.assertResponseHeaders;
import static org.opendaylight.restconf.server.TestUtils.buildRequest;
import static org.opendaylight.restconf.server.TestUtils.formattableBody;
import static org.opendaylight.restconf.server.TestUtils.newOptionsRequest;
import static org.opendaylight.restconf.server.TestUtils.newRequest;

import io.netty.handler.codec.DateFormatter;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.opendaylight.netconf.databind.RequestError;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.api.ApiPath.ApiIdentifier;
import org.opendaylight.restconf.api.ApiPath.ListInstance;
import org.opendaylight.restconf.server.TestUtils.TestEncoding;
import org.opendaylight.restconf.server.api.ChildBody;
import org.opendaylight.restconf.server.api.ConfigurationMetadata;
import org.opendaylight.restconf.server.api.CreateResourceResult;
import org.opendaylight.restconf.server.api.DataGetResult;
import org.opendaylight.restconf.server.api.DataPatchResult;
import org.opendaylight.restconf.server.api.DataPostBody;
import org.opendaylight.restconf.server.api.DataPutResult;
import org.opendaylight.restconf.server.api.DataYangPatchResult;
import org.opendaylight.restconf.server.api.InvokeResult;
import org.opendaylight.restconf.server.api.JsonChildBody;
import org.opendaylight.restconf.server.api.JsonDataPostBody;
import org.opendaylight.restconf.server.api.JsonPatchBody;
import org.opendaylight.restconf.server.api.JsonResourceBody;
import org.opendaylight.restconf.server.api.OptionsResult;
import org.opendaylight.restconf.server.api.PatchBody;
import org.opendaylight.restconf.server.api.PatchStatusContext;
import org.opendaylight.restconf.server.api.PatchStatusEntity;
import org.opendaylight.restconf.server.api.ResourceBody;
import org.opendaylight.restconf.server.api.XmlChildBody;
import org.opendaylight.restconf.server.api.XmlDataPostBody;
import org.opendaylight.restconf.server.api.XmlPatchBody;
import org.opendaylight.restconf.server.api.XmlResourceBody;
import org.opendaylight.yangtools.yang.common.Empty;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.UnresolvedQName.Unqualified;

class DataRequestProcessorTest extends AbstractRequestProcessorTest {
    private static final String DATA_PATH_WITH_ID = DATA_PATH + "/" + ID_PATH;
    private static final String DATA_PATH_WITH_ID_SLASH = DATA_PATH_WITH_ID + "/foo=%2f";
    private static final String PATH_CREATED = BASE_URI + "/data/" + NEW_ID_PATH;

    private static final ConfigurationMetadata.EntityTag ETAG =
        new ConfigurationMetadata.EntityTag(Long.toHexString(System.currentTimeMillis()), true);
    private static final Instant LAST_MODIFIED = Instant.now();
    private static final Map<CharSequence, Object> META_HEADERS = Map.of(
        HttpHeaderNames.ETAG, ETAG.value(),
        HttpHeaderNames.LAST_MODIFIED, DateFormatter.format(Date.from(LAST_MODIFIED))
    );

    @Captor
    private ArgumentCaptor<ApiPath> apiPathCaptor;

    private static Stream<Arguments> encodingsWithCreatedFlag() {
        return Stream.of(
            Arguments.of(TestEncoding.JSON, JSON_CONTENT, true),
            Arguments.of(TestEncoding.JSON, JSON_CONTENT, false),
            Arguments.of(TestEncoding.XML, XML_CONTENT, true),
            Arguments.of(TestEncoding.XML, XML_CONTENT, false)
        );
    }

    @Test
    void optionsDataStore() {
        final var request = newOptionsRequest(DATA_PATH);
        doAnswer(answerCompleteWith(OptionsResult.DATASTORE)).when(server).dataOPTIONS(any());

        final var response = dispatch(request);
        assertResponse(response, HttpResponseStatus.OK);
        assertResponseHeaders(response, Map.of(
            HttpHeaderNames.ALLOW, "GET, HEAD, OPTIONS, PATCH, POST, PUT",
            HttpHeaderNames.ACCEPT_PATCH, """
                application/yang-data+json, \
                application/yang-data+xml, \
                application/yang-patch+json, \
                application/yang-patch+xml, \
                application/json, \
                application/xml, \
                text/xml"""));
    }

    @Test
    void optionsDataStoreReadOnly() {
        final var request = newOptionsRequest(DATA_PATH);
        doAnswer(answerCompleteWith(OptionsResult.READ_ONLY)).when(server).dataOPTIONS(any());

        final var response = dispatch(request);
        assertResponse(response, HttpResponseStatus.OK);
        assertResponseHeaders(response, Map.of(HttpHeaderNames.ALLOW, "GET, HEAD, OPTIONS"));
    }

    @Test
    void optionsOperation() {
        final var request = newOptionsRequest(DATA_PATH_WITH_ID);
        doAnswer(answerCompleteWith(OptionsResult.ACTION)).when(server).dataOPTIONS(any(), any());

        final var response = dispatch(request);
        assertResponse(response, HttpResponseStatus.OK);
        assertResponseHeaders(response, Map.of(HttpHeaderNames.ALLOW, "OPTIONS, POST"));
    }

    @Test
    void optionsResource() {
        final var request = newOptionsRequest(DATA_PATH_WITH_ID);
        doAnswer(answerCompleteWith(OptionsResult.RESOURCE)).when(server).dataOPTIONS(any(), any());

        final var response = dispatch(request);
        assertResponse(response, HttpResponseStatus.OK);
        assertResponseHeaders(response, Map.of(
            HttpHeaderNames.ALLOW, "DELETE, GET, HEAD, OPTIONS, PATCH, POST, PUT",
            HttpHeaderNames.ACCEPT_PATCH, """
                application/yang-data+json, \
                application/yang-data+xml, \
                application/yang-patch+json, \
                application/yang-patch+xml, \
                application/json, \
                application/xml, \
                text/xml"""));
    }

    @Test
    void optionsReadOnly() {
        final var request = newOptionsRequest(DATA_PATH_WITH_ID);
        doAnswer(answerCompleteWith(OptionsResult.READ_ONLY)).when(server).dataOPTIONS(any(), any());

        final var response = dispatch(request);
        assertResponse(response, HttpResponseStatus.OK);
        assertResponseHeaders(response, Map.of(HttpHeaderNames.ALLOW, "GET, HEAD, OPTIONS"));
    }

    @ParameterizedTest
    @MethodSource("encodings")
    void getDataRoot(final TestEncoding encoding, final String content) {
        mockSession();
        final var result = new DataGetResult(formattableBody(encoding, content), ETAG, LAST_MODIFIED);
        doAnswer(answerCompleteWith(result)).when(server).dataGET(any());

        final var request = buildRequest(HttpMethod.GET, DATA_PATH, encoding, null);
        final var response = dispatchWithAlloc(request);

        assertResponse(response, HttpResponseStatus.OK, encoding.responseType, content);
        assertResponseHeaders(response, Map.of(HttpHeaderNames.CACHE_CONTROL, HttpHeaderValues.NO_CACHE));
        assertResponseHeaders(response, META_HEADERS);
    }

    @ParameterizedTest
    @MethodSource("encodings")
    void getDataById(final TestEncoding encoding, final String content) {
        mockSession();
        final var result = new DataGetResult(formattableBody(encoding, content), null, null);
        doAnswer(answerCompleteWith(result)).when(server).dataGET(any(), any(ApiPath.class));

        final var request = buildRequest(HttpMethod.GET, DATA_PATH_WITH_ID, encoding, null);
        final var response = dispatchWithAlloc(request);
        verify(server).dataGET(any(), apiPathCaptor.capture());

        assertEquals(API_PATH, apiPathCaptor.getValue());
        assertResponse(response, HttpResponseStatus.OK, encoding.responseType, content);
        assertResponseHeaders(response, Map.of(HttpHeaderNames.CACHE_CONTROL, HttpHeaderValues.NO_CACHE));
    }

    @ParameterizedTest
    @MethodSource("encodings")
    void postDataRoot(final TestEncoding encoding, final String content) throws Exception {
        final var result = new CreateResourceResult(NEW_API_PATH, ETAG, LAST_MODIFIED);
        final var answer = new FuglyRestconfServerAnswer(encoding.isJson() ? JsonChildBody.class : XmlChildBody.class,
            1, result);
        doAnswer(answer).when(server).dataPOST(any(), any(ChildBody.class));

        final var request = buildRequest(HttpMethod.POST, DATA_PATH, encoding, content);
        final var response = dispatch(request);

        answer.assertContent(content);

        assertResponse(response, HttpResponseStatus.CREATED);
        assertResponseHeaders(response, Map.of(HttpHeaderNames.LOCATION, PATH_CREATED));
        assertResponseHeaders(response, META_HEADERS);
    }

    @ParameterizedTest
    @MethodSource("encodings")
    void postDataWithId(final TestEncoding encoding, final String content) throws Exception {
        final var result = new CreateResourceResult(NEW_API_PATH, null, null);
        final var answer = new FuglyRestconfServerAnswer(
            encoding.isJson() ? JsonDataPostBody.class : XmlDataPostBody.class, 2, result);
        doAnswer(answer).when(server).dataPOST(any(), any(ApiPath.class), any(DataPostBody.class));

        final var request = buildRequest(HttpMethod.POST, DATA_PATH_WITH_ID, encoding, content);
        final var response = dispatch(request);
        verify(server).dataPOST(any(), apiPathCaptor.capture(), any());

        assertEquals(API_PATH, apiPathCaptor.getValue());
        answer.assertContent(content);

        assertResponse(response, HttpResponseStatus.CREATED);
        assertResponseHeaders(response, Map.of(HttpHeaderNames.LOCATION, PATH_CREATED));
    }

    @ParameterizedTest
    @MethodSource("encodings")
    void postDataWithIdRpc(final TestEncoding encoding, final String content) throws Exception {
        mockSession();
        final var invokeResult = new InvokeResult(formattableBody(encoding, content));
        final var answer = new FuglyRestconfServerAnswer(
            encoding.isJson() ? JsonDataPostBody.class : XmlDataPostBody.class, 2, invokeResult);
        doAnswer(answer).when(server).dataPOST(any(), any(ApiPath.class), any(DataPostBody.class));

        final var request = buildRequest(HttpMethod.POST, DATA_PATH_WITH_ID, encoding, content);
        final var response = dispatchWithAlloc(request);
        verify(server).dataPOST(any(), apiPathCaptor.capture(), any());

        assertEquals(API_PATH, apiPathCaptor.getValue());
        answer.assertContent(content);

        assertResponse(response, HttpResponseStatus.OK);
    }

    @ParameterizedTest
    @MethodSource("encodingsWithCreatedFlag")
    void putDataRoot(final TestEncoding encoding, final String content, final boolean created) throws Exception {
        final var result = new DataPutResult(created, ETAG, LAST_MODIFIED);
        final var answer = new FuglyRestconfServerAnswer(
            encoding.isJson() ? JsonResourceBody.class : XmlResourceBody.class, 1, result);
        doAnswer(answer).when(server).dataPUT(any(), any(ResourceBody.class));

        final var request = buildRequest(HttpMethod.PUT, DATA_PATH, encoding, content);
        final var response = dispatch(request);

        answer.assertContent(content);

        assertResponse(response, created ? HttpResponseStatus.CREATED : HttpResponseStatus.NO_CONTENT);
        assertResponseHeaders(response, META_HEADERS);
    }

    @ParameterizedTest
    @MethodSource("encodingsWithCreatedFlag")
    void putDataWithId(final TestEncoding encoding, final String content, final boolean created) throws Exception {
        final var result = new DataPutResult(created, null, null);
        final var answer = new FuglyRestconfServerAnswer(
            encoding.isJson() ? JsonResourceBody.class : XmlResourceBody.class, 2, result);
        doAnswer(answer).when(server).dataPUT(any(), any(ApiPath.class), any(ResourceBody.class));

        final var request = buildRequest(HttpMethod.PUT, DATA_PATH_WITH_ID, encoding, content);
        final var response = dispatch(request);
        verify(server).dataPUT(any(), apiPathCaptor.capture(), any());

        assertEquals(API_PATH, apiPathCaptor.getValue());
        answer.assertContent(content);

        assertResponse(response, created ? HttpResponseStatus.CREATED : HttpResponseStatus.NO_CONTENT);
    }

    @ParameterizedTest
    @MethodSource("encodings")
    void patchDataRoot(final TestEncoding encoding, final String content) throws Exception {
        final var result = new DataPatchResult(null, null);
        final var answer = new FuglyRestconfServerAnswer(
            encoding.isJson() ? JsonResourceBody.class : XmlResourceBody.class, 1, result);
        doAnswer(answer).when(server).dataPATCH(any(), any(ResourceBody.class));

        final var request = buildRequest(HttpMethod.PATCH, DATA_PATH, encoding, content);
        final var response = dispatch(request);
        answer.assertContent(content);
        assertResponse(response, HttpResponseStatus.OK);
    }

    @ParameterizedTest
    @MethodSource("encodings")
    void patchDataWithId(final TestEncoding encoding, final String content) throws Exception {
        final var result = new DataPatchResult(ETAG, LAST_MODIFIED);
        final var answer = new FuglyRestconfServerAnswer(
            encoding.isJson() ? JsonResourceBody.class : XmlResourceBody.class, 2, result);
        doAnswer(answer).when(server).dataPATCH(any(), any(ApiPath.class), any(ResourceBody.class));

        final var request = buildRequest(HttpMethod.PATCH, DATA_PATH_WITH_ID, encoding, content);
        final var response = dispatch(request);
        verify(server).dataPATCH(any(), apiPathCaptor.capture(), any(ResourceBody.class));

        assertEquals(API_PATH, apiPathCaptor.getValue());
        answer.assertContent(content);
        assertResponse(response, HttpResponseStatus.OK);
        assertResponseHeaders(response, META_HEADERS);
    }

    @ParameterizedTest
    @MethodSource
    void yangPatch(final TestEncoding encoding, final String input, final PatchStatusContext output,
            final ErrorTag expectedErrorTag, final List<String> expectedContentMessage) throws Exception {
        writableResponseWriter();
        final var result = new DataYangPatchResult(output);
        final var answer = new FuglyRestconfServerAnswer(
            encoding.isJson() ? JsonPatchBody.class : XmlPatchBody.class, 1, result);
        doAnswer(answer).when(server).dataPATCH(any(), any(PatchBody.class));

        final var request = buildRequest(HttpMethod.PATCH, DATA_PATH, encoding, input);
        final var response = dispatchChunked(request);
        answer.assertContent(input);

        assertInstanceOf(DefaultHttpResponse.class, response.getFirst());
        assertInstanceOf(DefaultLastHttpContent.class, response.getLast());

        final var expectedStatus = expectedErrorTag == null ? HttpResponseStatus.OK
            : HttpResponseStatus.valueOf(TestUtils.ERROR_TAG_MAPPING.statusOf(expectedErrorTag).code());

        assertResponse((HttpResponse) response.getFirst(), expectedStatus);
        assertResponseHeaders((HttpResponse) response.getFirst(), Map.of(HttpHeaderNames.CONTENT_TYPE,
            encoding.responseType));

        final var content = ((HttpContent)response.get(1)).content().toString(StandardCharsets.UTF_8);
        assertContentSimplified(content, encoding, expectedContentMessage);
    }

    private static Stream<Arguments> yangPatch() {
        final var contextOk = new PatchStatusContext("patch-id1", List.of(), true, null);
        final var containsOk = List.of("patch-id1");
        final var contextError1 = new PatchStatusContext("patch-id2", List.of(), false,
            List.of(new RequestError(ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED, "operation-failed-error")));
        final var containsError1 = List.of(
            "patch-id2", ErrorTag.OPERATION_FAILED.elementBody(), "operation-failed-error");
        final var contextError2 = new PatchStatusContext("patch-id3", List.of(
            new PatchStatusEntity("edit-id1", true, null),
            new PatchStatusEntity("edit-id2", false,
                List.of(new RequestError(ErrorType.APPLICATION, ErrorTag.DATA_MISSING, "data-missing-error")))
        ), false, null);
        final var containsError2 = List.of("patch-id3", "edit-id1", "edit-id2", ErrorTag.DATA_MISSING.elementBody(),
            "data-missing-error");

        return Stream.of(
            // encoding, input, resultContext, errorTag, response should contain
            Arguments.of(TestEncoding.YANG_PATCH_JSON, JSON_CONTENT, contextOk, null, containsOk),
            Arguments.of(TestEncoding.YANG_PATCH_JSON, JSON_CONTENT,
                contextError1, ErrorTag.OPERATION_FAILED, containsError1),
            Arguments.of(TestEncoding.YANG_PATCH_JSON, JSON_CONTENT,
                contextError2, ErrorTag.DATA_MISSING, containsError2),
            Arguments.of(TestEncoding.YANG_PATCH_XML, XML_CONTENT, contextOk, null, containsOk),
            Arguments.of(TestEncoding.YANG_PATCH_XML, XML_CONTENT,
                contextError1, ErrorTag.OPERATION_FAILED, containsError1),
            Arguments.of(TestEncoding.YANG_PATCH_XML, XML_CONTENT,
                contextError2, ErrorTag.DATA_MISSING, containsError2)
        );
    }

    @Test
    void deleteData() {
        final var result = Empty.value();
        doAnswer(answerCompleteWith(result)).when(server).dataDELETE(any(), any(ApiPath.class));

        final var request = newRequest(HttpMethod.DELETE, DATA_PATH_WITH_ID);
        final var response = dispatch(request);
        verify(server).dataDELETE(any(), apiPathCaptor.capture());
        assertEquals(API_PATH, apiPathCaptor.getValue());
        assertResponse(response, HttpResponseStatus.NO_CONTENT);
    }

    @Test
    void deleteDataDecode() {
        final var result = Empty.value();
        doAnswer(answerCompleteWith(result)).when(server).dataDELETE(any(), any(ApiPath.class));

        final var request = newRequest(HttpMethod.DELETE, DATA_PATH_WITH_ID_SLASH);
        final var response = dispatch(request);
        verify(server).dataDELETE(any(), apiPathCaptor.capture());

        assertEquals(ApiPath.of(List.of(
            new ApiIdentifier("test-model", Unqualified.of("root")),
            ListInstance.of(null, Unqualified.of("foo"), "/"))), apiPathCaptor.getValue());
        assertResponse(response, HttpResponseStatus.NO_CONTENT);
    }
}
