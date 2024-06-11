/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import java.io.InputStream;
import org.opendaylight.restconf.api.ConsumableBody;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.yangtools.yang.data.api.YangNetconfError;
import org.opendaylight.yangtools.yang.data.api.YangNetconfErrorAware;

/**
 * An abstract request body backed by an {@link InputStream}. In controls the access to input stream, so that it can
 * only be taken once.
 */
abstract sealed class RequestBody extends ConsumableBody
        permits ChildBody, DataPostBody, OperationInputBody, PatchBody, ResourceBody {
    RequestBody(final InputStream inputStream) {
        super(inputStream);
    }

    /**
     * Throw a {@link RestconfDocumentedException} if the specified exception has a {@link YangNetconfError} attachment.
     *
     * @param cause Proposed cause of a RestconfDocumentedException
     * @deprecated Migrate to using {@link ServerException} instead
     */
    @Deprecated
    static void throwIfYangError(final Exception cause) {
        if (cause instanceof YangNetconfErrorAware infoAware) {
            throw new RestconfDocumentedException(cause, infoAware.getNetconfErrors().stream()
                .map(error -> new RestconfError(error.type(), error.tag(), error.message(), error.appTag(),
                    // FIXME: pass down error info
                    null, error.path()))
                .toList(), null);
        }
    }
}
