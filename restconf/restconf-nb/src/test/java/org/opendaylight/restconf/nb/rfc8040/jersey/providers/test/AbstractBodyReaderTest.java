/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.jersey.providers.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;
import org.junit.function.ThrowingRunnable;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.spi.FixedDOMSchemaService;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.restconf.common.patch.PatchContext;
import org.opendaylight.restconf.nb.rfc8040.TestRestconfUtils;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindContext;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindProvider;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.spi.AbstractIdentifierAwareJaxRsProvider;
import org.opendaylight.restconf.nb.rfc8040.legacy.NormalizedNodePayload;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

public abstract class AbstractBodyReaderTest {
    public static final QName LEAF_NAME_QNAME = QName.create("instance:identifier:patch:module", "2015-11-21","name")
            .intern();

    protected final MediaType mediaType;
    protected final DatabindProvider databindProvider;
    protected final DOMMountPointService mountPointService;

    protected AbstractBodyReaderTest(final EffectiveModelContext schemaContext) throws NoSuchFieldException,
            IllegalAccessException {
        mediaType = getMediaType();

        final var databindContext = DatabindContext.ofModel(schemaContext);
        databindProvider = () -> databindContext;

        mountPointService = mock(DOMMountPointService.class);
        final var mountPoint = mock(DOMMountPoint.class);
        doReturn(Optional.of(mountPoint)).when(mountPointService).getMountPoint(any(YangInstanceIdentifier.class));
        doReturn(Optional.of(FixedDOMSchemaService.of(schemaContext))).when(mountPoint)
            .getService(DOMSchemaService.class);
    }

    protected abstract MediaType getMediaType();

    protected static EffectiveModelContext schemaContextLoader(final String yangPath,
            final EffectiveModelContext schemaContext) {
        return TestRestconfUtils.loadSchemaContext(yangPath, schemaContext);
    }

    protected static <T extends AbstractIdentifierAwareJaxRsProvider<?>> void mockBodyReader(
            final String identifier, final T normalizedNodeProvider, final boolean isPost) {
        final UriInfo uriInfoMock = mock(UriInfo.class);
        final MultivaluedMap<String, String> pathParm = new MultivaluedHashMap<>(1);

        if (!identifier.isEmpty()) {
            pathParm.put("identifier", List.of(identifier));
        }

        when(uriInfoMock.getPathParameters()).thenReturn(pathParm);
        when(uriInfoMock.getPathParameters(false)).thenReturn(pathParm);
        when(uriInfoMock.getPathParameters(true)).thenReturn(pathParm);
        normalizedNodeProvider.setUriInfo(uriInfoMock);

        final Request request = mock(Request.class);
        if (isPost) {
            when(request.getMethod()).thenReturn("POST");
        } else {
            when(request.getMethod()).thenReturn("PUT");
        }

        normalizedNodeProvider.setRequest(request);
    }

    protected static void checkMountPointNormalizedNodePayload(final NormalizedNodePayload nnContext) {
        checkNormalizedNodePayload(nnContext);
        assertNotNull(nnContext.getInstanceIdentifierContext().getMountPoint());
    }

    protected static void checkNormalizedNodePayload(final NormalizedNodePayload nnContext) {
        assertNotNull(nnContext.getData());
        assertNotNull(nnContext.getInstanceIdentifierContext()
                .getInstanceIdentifier());
        assertNotNull(nnContext.getInstanceIdentifierContext()
                .getSchemaContext());
        assertNotNull(nnContext.getInstanceIdentifierContext().getSchemaNode());
    }

    protected static void checkPatchContext(final PatchContext patchContext) {
        assertNotNull(patchContext.getData());
        assertNotNull(patchContext.getInstanceIdentifierContext().getInstanceIdentifier());
        assertNotNull(patchContext.getInstanceIdentifierContext().getSchemaContext());
        assertNotNull(patchContext.getInstanceIdentifierContext().getSchemaNode());
    }

    protected static void checkPatchContextMountPoint(final PatchContext patchContext) {
        checkPatchContext(patchContext);
        assertNotNull(patchContext.getInstanceIdentifierContext().getMountPoint());
    }

    protected static EffectiveModelContext modelContext(final DOMMountPoint mountPoint) {
        return mountPoint.getService(DOMSchemaService.class)
            .flatMap(svc -> Optional.ofNullable(svc.getGlobalContext()))
            .orElse(null);
    }

    protected static void assertRangeViolation(final ThrowingRunnable runnable) {
        final RestconfDocumentedException ex = assertThrows(RestconfDocumentedException.class, runnable);
        assertEquals(Status.BAD_REQUEST, ex.getResponse().getStatusInfo());

        final List<RestconfError> errors = ex.getErrors();
        assertEquals(1, errors.size());

        final RestconfError error = errors.get(0);
        assertEquals(ErrorType.APPLICATION, error.getErrorType());
        assertEquals(ErrorTag.INVALID_VALUE, error.getErrorTag());
        assertEquals("bar error app tag", error.getErrorAppTag());
        assertEquals("bar error message", error.getErrorMessage());
    }
}
