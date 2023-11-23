/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.mdsal;

import static java.util.Objects.requireNonNull;

import com.google.common.io.CharSource;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMYangTextSourceProvider;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfFuture;
import org.opendaylight.restconf.common.errors.SettableRestconfFuture;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindContext.SourceResolver;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaSourceRepresentation;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;

record DOMSourceResolver(@NonNull DOMYangTextSourceProvider domProvider) implements SourceResolver {
    DOMSourceResolver {
        requireNonNull(domProvider);
    }

    @Override
    public RestconfFuture<CharSource> resolveSource(final SourceIdentifier source,
            final Class<? extends SchemaSourceRepresentation> representation) {
        if (!YangTextSchemaSource.class.isAssignableFrom(representation)) {
            return null;
        }

        final var ret = new SettableRestconfFuture<CharSource>();
        Futures.addCallback(domProvider.getSource(source), new FutureCallback<YangTextSchemaSource>() {
            @Override
            public void onSuccess(final YangTextSchemaSource result) {
                ret.set(result);
            }

            @Override
            public void onFailure(final Throwable cause) {
                ret.setFailure(cause instanceof RestconfDocumentedException e ? e
                    : new RestconfDocumentedException(cause.getMessage(), ErrorType.RPC,
                        ErrorTag.OPERATION_FAILED, cause));
            }
        }, MoreExecutors.directExecutor());
        return ret;
    }
}