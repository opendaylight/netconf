/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.databind;

import static com.google.common.base.Verify.verify;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.patch.PatchContext;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.IdentifierCodec;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.ParserIdentifier;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.patch.rev170222.yang.patch.yang.patch.Edit.Operation;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;

/**
 *
 */
public abstract class PatchBody implements AutoCloseable {
    private static final VarHandle INPUT_STREAM;

    static {
        try {
            INPUT_STREAM = MethodHandles.lookup().findVarHandle(PatchBody.class, "inputStream", InputStream.class);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @SuppressWarnings("unused")
    private volatile InputStream inputStream;

    PatchBody(final InputStream inputStream) {
        this.inputStream = requireNonNull(inputStream);
    }

    @Override
    public final void close() throws IOException {
        final var is = acquireStream();
        if (is != null) {
            is.close();
        }
    }

    public final @NonNull PatchContext toPatchContext(final @NonNull InstanceIdentifierContext targetResource)
            throws IOException {
        final var is = acquireStream();
        if (is == null) {
            throw new IllegalStateException("Input stream has already been consumed");
        }

        try {
            return toPatchContext(targetResource, is);
        } finally {
            is.close();
        }
    }

    abstract @NonNull PatchContext toPatchContext(@NonNull InstanceIdentifierContext targetResource,
        @NonNull InputStream inputStream) throws IOException;

    static final YangInstanceIdentifier parsePatchTarget(final InstanceIdentifierContext targetResource,
            final String target) {
        final var urlPath = targetResource.getInstanceIdentifier();
        if (target.equals("/")) {
            verify(!urlPath.isEmpty(),
                "target resource of URI must not be a datastore resource when target is '/'");
            return urlPath;
        }

        final var schemaContext = targetResource.getSchemaContext();
        final String targetUrl;
        if (urlPath.isEmpty()) {
            targetUrl = target.startsWith("/") ? target.substring(1) : target;
        } else {
            targetUrl = IdentifierCodec.serialize(urlPath, schemaContext) + target;
        }

        return ParserIdentifier.toInstanceIdentifier(targetUrl, schemaContext, null).getInstanceIdentifier();
    }

    /**
     * Not all patch operations support value node. Check if operation requires value or not.
     *
     * @param operation Patch edit operation
     * @return true if operation requires value, false otherwise
     */
    static final boolean requiresValue(final Operation operation) {
        return switch (operation) {
            case Create, Insert, Merge, Replace -> true;
            case Delete, Move, Remove -> false;
        };
    }

    private @Nullable InputStream acquireStream() {
        return (InputStream) INPUT_STREAM.getAndSet(this, null);
    }
}
