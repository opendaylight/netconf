/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.jaxrs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.text.ParseException;
import java.util.List;
import java.util.function.Consumer;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.spi.FixedDOMSchemaService;
import org.opendaylight.netconf.databind.RequestError;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.server.api.YangErrorsBody;
import org.opendaylight.restconf.server.api.testlib.AbstractJukeboxTest;
import org.opendaylight.restconf.server.mdsal.MdsalDatabindProvider;
import org.opendaylight.restconf.server.mdsal.MdsalRestconfServer;
import org.opendaylight.restconf.server.spi.ErrorTagMapping;
import org.opendaylight.restconf.server.spi.NormalizedFormattableBody;
import org.opendaylight.restconf.server.spi.RestconfStream.Registry;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

@ExtendWith(MockitoExtension.class)
abstract class AbstractRestconfTest extends AbstractJukeboxTest {
    static final ApiPath JUKEBOX_API_PATH = apiPath("example-jukebox:jukebox");

    @Mock
    UriInfo uriInfo;
    @Mock
    DOMDataBroker dataBroker;
    @Mock
    DOMActionService actionService;
    @Mock
    DOMRpcService rpcService;
    @Mock
    DOMMountPointService mountPointService;
    @Mock
    DOMMountPoint mountPoint;
    @Mock
    SecurityContext sc;
    @Mock
    Registry streamRegistry;
    @Mock
    SSESenderFactory senderFactory;

    JaxRsRestconf restconf;

    @BeforeEach
    final void setupRestconf() {
        restconf = new JaxRsRestconf(
            new MdsalRestconfServer(new MdsalDatabindProvider(new FixedDOMSchemaService(modelContext())),
                dataBroker, rpcService, actionService, mountPointService),
            streamRegistry, senderFactory, ErrorTagMapping.RFC8040, PrettyPrintParam.FALSE);
    }

    EffectiveModelContext modelContext() {
        return JUKEBOX_SCHEMA;
    }

    @NonNullByDefault
    static final <N extends NormalizedNode> NormalizedFormattableBody<N> assertNormalizedBody(final int status,
            final Consumer<AsyncResponse> invocation) {
        return assertInstanceOf(NormalizedFormattableBody.class, assertFormattableBody(status, invocation));
    }

    static final FormattableBody assertFormattableBody(final int status, final Consumer<AsyncResponse> invocation) {
        return assertEntity(JaxRsFormattableBody.class, status, invocation).body();
    }

    static final ContainerNode assertOperationOutput(final int status, final Consumer<AsyncResponse> invocation) {
        return assertInstanceOf(ContainerNode.class, assertOperationOutputBody(status, invocation).data());
    }

    static final NormalizedFormattableBody<?> assertOperationOutputBody(final int status,
            final Consumer<AsyncResponse> invocation) {
        return assertInstanceOf(NormalizedFormattableBody.class, assertFormattableBody(status, invocation));
    }

    static final <T> T assertEntity(final Class<T> expectedType, final int expectedStatus,
            final Consumer<AsyncResponse> invocation) {
        return assertInstanceOf(expectedType, assertEntity(expectedStatus, invocation));
    }

    static final Object assertEntity(final int expectedStatus, final Consumer<AsyncResponse> invocation) {
        return assertResponse(expectedStatus, invocation).getEntity();
    }

    static final RequestError assertError(final int expectedStatus, final Consumer<AsyncResponse> invocation) {
        final var errors = assertErrors(expectedStatus, invocation);
        assertEquals(1, errors.size());
        final var error = errors.get(0);
        assertNotNull(error);
        return error;
    }

    static final List<RequestError> assertErrors(final int expectedStatus, final Consumer<AsyncResponse> invocation) {
        return assertInstanceOf(YangErrorsBody.class,
            assertEntity(JaxRsFormattableBody.class, expectedStatus, invocation).body()).errors();
    }

    static final Response assertResponse(final int expectedStatus, final Consumer<AsyncResponse> invocation) {
        final var ar = mock(AsyncResponse.class);
        doReturn(true).when(ar).resume(any(Response.class));

        invocation.accept(ar);

        final var captor = ArgumentCaptor.forClass(Response.class);
        verify(ar).resume(captor.capture());
        final var response = captor.getValue();
        assertEquals(expectedStatus, response.getStatus());
        return response;
    }

    static final @NonNull ApiPath apiPath(final String str) {
        try {
            return ApiPath.parse(str);
        } catch (ParseException e) {
            throw new AssertionError(e);
        }
    }
}
