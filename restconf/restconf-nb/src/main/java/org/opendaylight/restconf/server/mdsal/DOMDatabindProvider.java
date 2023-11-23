/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.mdsal;

import static com.google.common.base.Verify.verifyNotNull;
import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.api.DOMYangTextSourceProvider;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfFuture;
import org.opendaylight.restconf.common.errors.SettableRestconfFuture;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindContext;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindContext.SourceResolver;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindProvider;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContextListener;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaSourceRepresentation;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

/**
 * A {@link DatabindProvider} monitoring a {@link DOMSchemaService}.
 */
@Singleton
@Component(service = DatabindProvider.class)
public final class DOMDatabindProvider implements DatabindProvider, EffectiveModelContextListener, AutoCloseable {
    private record TextSourceResolver(@NonNull DOMYangTextSourceProvider domProvider) implements SourceResolver {
        TextSourceResolver {
            requireNonNull(domProvider);
        }

        @Override
        public RestconfFuture<InputStream> resolveSource(final SourceIdentifier source,
                final Class<? extends SchemaSourceRepresentation> representation) {
            if (!YangTextSchemaSource.class.isAssignableFrom(representation)) {
                return null;
            }

            final var ret = new SettableRestconfFuture<InputStream>();
            Futures.addCallback(domProvider.getSource(source), new FutureCallback<YangTextSchemaSource>() {
                @Override
                public void onSuccess(final YangTextSchemaSource result) {
                    final InputStream stream;
                    try {
                        stream = result.asByteSource(StandardCharsets.UTF_8).openStream();
                    } catch (IOException e) {
                        onFailure(e);
                        return;
                    }
                    ret.set(stream);
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

    private final Registration reg;
    private final SourceResolver sourceProvider;

    private volatile DatabindContext currentContext;

    @Inject
    @Activate
    public DOMDatabindProvider(@Reference final DOMSchemaService schemaService) {
        final var ext = schemaService.getExtensions().getInstance(DOMYangTextSourceProvider.class);
        sourceProvider = ext != null ? new TextSourceResolver(ext) : null;
        currentContext = DatabindContext.ofModel(schemaService.getGlobalContext());
        reg = schemaService.registerSchemaContextListener(this);
    }

    @Override
    public DatabindContext currentContext() {
        return verifyNotNull(currentContext, "Provider already closed");
    }

    @Override
    public void onModelContextUpdated(final EffectiveModelContext newModelContext) {
        final var local = currentContext;
        if (local != null && local.modelContext() != newModelContext) {
            currentContext = DatabindContext.ofModel(newModelContext, sourceProvider);
        }
    }

    @PreDestroy
    @Deactivate
    @Override
    public void close() {
        reg.close();
        currentContext = null;
    }
}
