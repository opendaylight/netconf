/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.jaxrs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.function.Consumer;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
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
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.restconf.nb.rfc8040.AbstractJukeboxTest;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindContext;
import org.opendaylight.restconf.nb.rfc8040.legacy.NormalizedNodePayload;
import org.opendaylight.restconf.server.mdsal.MdsalRestconfServer;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

// FIXME: hide this class
@ExtendWith(MockitoExtension.class)
public abstract class AbstractRestconfTest extends AbstractJukeboxTest {
    static final JaxRsApiPath JUKEBOX_API_PATH = new JaxRsApiPath("example-jukebox:jukebox");

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

    JaxRsRestconf restconf;

    @BeforeEach
    final void setupRestconf() {
        restconf = new JaxRsRestconf(new MdsalRestconfServer(() -> DatabindContext.ofModel(modelContext()), dataBroker,
            rpcService, actionService, mountPointService));
    }

    EffectiveModelContext modelContext() {
        return JUKEBOX_SCHEMA;
    }

    static final NormalizedNode assertNormalizedNode(final int status, final Consumer<AsyncResponse> invocation) {
        return assertEntity(NormalizedNodePayload.class, status, invocation).data();
    }

    // FIXME: hide this method
    public static final <T> T assertEntity(final Class<T> expectedType, final int expectedStatus,
            final Consumer<AsyncResponse> invocation) {
        return assertInstanceOf(expectedType, assertEntity(expectedStatus, invocation));
    }

    static final Object assertEntity(final int expectedStatus, final Consumer<AsyncResponse> invocation) {
        return assertResponse(expectedStatus, invocation).getEntity();
    }

    // FIXME: hide this method
    public static final RestconfError assertError(final Consumer<AsyncResponse> invocation) {
        final var errors = assertErrors(invocation);
        assertEquals(1, errors.size());
        final var error = errors.get(0);
        assertNotNull(error);
        return error;
    }

    // FIXME: hide this method
    public static final List<RestconfError> assertErrors(final Consumer<AsyncResponse> invocation) {
        final var ar = mock(AsyncResponse.class);
        final var captor = ArgumentCaptor.forClass(RestconfDocumentedException.class);
        doReturn(true).when(ar).resume(captor.capture());
        invocation.accept(ar);
        verify(ar).resume(any(RestconfDocumentedException.class));
        return captor.getValue().getErrors();
    }

    static final Response assertResponse(final int expectedStatus, final Consumer<AsyncResponse> invocation) {
        final var ar = mock(AsyncResponse.class);
        final var captor = ArgumentCaptor.forClass(Response.class);
        doReturn(true).when(ar).resume(captor.capture());
        invocation.accept(ar);
        verify(ar).resume(any(Response.class));
        final var response = captor.getValue();
        assertEquals(expectedStatus, response.getStatus());
        return response;
    }
}
