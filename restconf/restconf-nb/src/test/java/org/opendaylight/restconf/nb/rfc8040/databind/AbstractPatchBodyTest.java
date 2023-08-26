/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.databind;

import static java.util.Objects.requireNonNull;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.Function;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.spi.FixedDOMSchemaService;
import org.opendaylight.restconf.common.patch.PatchContext;
import org.opendaylight.restconf.nb.rfc8040.AbstractInstanceIdentifierTest;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.ParserIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;

@RunWith(MockitoJUnitRunner.Silent.class)
abstract class AbstractPatchBodyTest extends AbstractInstanceIdentifierTest {
    private final Function<InputStream, PatchBody> bodyConstructor;

    @Mock
    DOMMountPointService mountPointService;
    @Mock
    DOMMountPoint mountPoint;

    AbstractPatchBodyTest(final Function<InputStream, PatchBody> bodyConstructor) {
        this.bodyConstructor = requireNonNull(bodyConstructor);
    }

    @Before
    public final void before() {
        doReturn(Optional.of(mountPoint)).when(mountPointService).getMountPoint(any(YangInstanceIdentifier.class));
        doReturn(Optional.of(FixedDOMSchemaService.of(IID_SCHEMA))).when(mountPoint).getService(DOMSchemaService.class);
    }

    @NonNull String mountPrefix() {
        return "";
    }

    @Nullable DOMMountPoint mountPoint() {
        return null;
    }

    final void checkPatchContext(final PatchContext patchContext) {
        assertNotNull(patchContext.getData());

        final var iid = patchContext.getInstanceIdentifierContext();
        assertNotNull(iid);

        assertNotNull(iid.getInstanceIdentifier());
        assertNotNull(iid.getSchemaContext());
        assertNotNull(iid.getSchemaNode());
        assertSame(mountPoint(), iid.getMountPoint());
    }

    final @NonNull PatchContext parse(final String uriPath, final String patchBody) throws IOException {
        return parse(uriPath, new ByteArrayInputStream(patchBody.getBytes(StandardCharsets.UTF_8)));
    }

    // FIXME: migrate callers to use the above instead of resources
    @Deprecated
    final @NonNull PatchContext parse(final String uriPath, final InputStream patchBody) throws IOException {
        try (var body = bodyConstructor.apply(patchBody)) {
            return body.toPatchContext(ParserIdentifier.toInstanceIdentifier(uriPath, IID_SCHEMA, mountPointService));
        }
    }
}
