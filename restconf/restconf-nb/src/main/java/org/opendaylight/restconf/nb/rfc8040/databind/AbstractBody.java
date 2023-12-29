/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.databind;

import static java.util.Objects.requireNonNull;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.yangtools.yang.data.api.YangNetconfError;
import org.opendaylight.yangtools.yang.data.api.YangNetconfErrorAware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An abstract request body backed by an {@link InputStream}.
 */
public abstract sealed class AbstractBody implements AutoCloseable
        permits ChildBody, DataPostBody, OperationInputBody, PatchBody, ResourceBody {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractBody.class);

    private static final VarHandle INPUT_STREAM;

    static {
        try {
            INPUT_STREAM = MethodHandles.lookup().findVarHandle(AbstractBody.class, "inputStream", InputStream.class);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @SuppressWarnings("unused")
    @SuppressFBWarnings(value = "URF_UNREAD_FIELD", justification = "https://github.com/spotbugs/spotbugs/issues/2749")
    private volatile InputStream inputStream;

    AbstractBody(final InputStream inputStream) {
        this.inputStream = requireNonNull(inputStream);
    }

    @Override
    public final void close() {
        final var is = getStream();
        if (is != null) {
            try {
                is.close();
            } catch (IOException e) {
                LOG.info("Failed to close input", e);
            }
        }
    }

    final @NonNull InputStream acquireStream() {
        final var is = getStream();
        if (is == null) {
            throw new IllegalStateException("Input stream has already been consumed");
        }
        return is;
    }


    /**
     * Throw a {@link RestconfDocumentedException} if the specified exception has a {@link YangNetconfError} attachment.
     *
     * @param cause Proposed cause of a RestconfDocumentedException
     */
    static void throwIfYangError(final Exception cause) {
        if (cause instanceof YangNetconfErrorAware infoAware) {
            throw new RestconfDocumentedException(cause, infoAware.getNetconfErrors().stream()
                .map(error -> new RestconfError(error.type(), error.tag(), error.message(), error.appTag(),
                    // FIXME: pass down error info
                    null, error.path()))
                .toList());
        }
    }

    private @Nullable InputStream getStream() {
        return (InputStream) INPUT_STREAM.getAndSet(this, null);
    }
}
