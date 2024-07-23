/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.mdsal;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import org.opendaylight.mdsal.dom.api.DOMSchemaService.YangTextSourceExtension;
import org.opendaylight.restconf.server.api.ModulesGetResult;
import org.opendaylight.restconf.server.api.ServerException;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.restconf.server.spi.ServerModulesOperations;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.model.api.source.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.api.source.SourceRepresentation;
import org.opendaylight.yangtools.yang.model.api.source.YangTextSource;

/**
 * {@link ServerModulesOperations} based on {@link YangTextSourceExtension} or deferring to a delegate.
 */
public final class MdsalServerModulesOperations implements ServerModulesOperations {
    private final YangTextSourceExtension sourceProvider;
    private final ServerModulesOperations delegate;

    public MdsalServerModulesOperations(final YangTextSourceExtension sourceProvider,
            final ServerModulesOperations delegate) {
        this.sourceProvider = requireNonNull(sourceProvider);
        this.delegate = requireNonNull(delegate);
    }

    @Override
    public void getModelSource(final ServerRequest<ModulesGetResult> request, final SourceIdentifier source,
            final Class<? extends SourceRepresentation> representation) {
        if (YangTextSource.class.isAssignableFrom(representation)) {
            Futures.addCallback(sourceProvider.getYangTexttSource(source), new FutureCallback<>() {
                @Override
                public void onSuccess(final YangTextSource result) {
                    request.completeWith(new ModulesGetResult(result));
                }

                @Override
                public void onFailure(final Throwable cause) {
                    request.completeWith(cause instanceof ServerException e ? e
                        : new ServerException(ErrorType.RPC, ErrorTag.OPERATION_FAILED, cause));
                }
            }, MoreExecutors.directExecutor());
        } else {
            delegate.getModelSource(request, source, representation);
        }
    }
}
