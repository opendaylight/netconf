/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.communicator.impl;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import javax.annotation.Nullable;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.restconfsb.communicator.api.RestconfFacade;
import org.opendaylight.restconfsb.communicator.api.http.HttpException;
import org.opendaylight.restconfsb.communicator.api.http.NotFoundException;
import org.opendaylight.restconfsb.communicator.api.http.Request;
import org.opendaylight.restconfsb.communicator.api.http.Sender;
import org.opendaylight.restconfsb.communicator.api.http.SseListener;
import org.opendaylight.restconfsb.communicator.api.parser.ErrorParser;
import org.opendaylight.restconfsb.communicator.api.parser.Parser;
import org.opendaylight.restconfsb.communicator.api.renderer.Renderer;
import org.opendaylight.restconfsb.communicator.api.stream.RestconfDeviceStreamListener;
import org.opendaylight.restconfsb.communicator.impl.xml.draft04.RestconfErrorXmlParser;
import org.opendaylight.restconfsb.communicator.impl.xml.draft04.XmlParser;
import org.opendaylight.restconfsb.communicator.impl.xml.draft04.XmlRenderer;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RestconfFacadeImpl implements RestconfFacade {

    private static final Logger LOG = LoggerFactory.getLogger(RestconfFacadeImpl.class);

    private final Renderer renderer;
    private final Parser parser;
    private final ErrorParser errorParser;
    private final Sender sender;
    private final RestconfStreamsHandler streamsHandler;
    private final ListenerRegistration<SseListener> sseRegistration;

    private RestconfFacadeImpl(final SchemaContext schemaContext, final Sender sender) {
        this.renderer = new XmlRenderer(schemaContext);
        this.sender = sender;
        this.parser = new XmlParser(schemaContext);
        this.errorParser = new RestconfErrorXmlParser();
        this.streamsHandler = new RestconfStreamsHandler(parser);
        this.sseRegistration = sender.registerSseListener(streamsHandler);
    }

    public static RestconfFacadeImpl createXmlRestconfFacade(final SchemaContext schemaContext, final Sender sender) {
        return new RestconfFacadeImpl(schemaContext, sender);
    }

    @Override
    public ListenableFuture<Void> headData(final LogicalDatastoreType datastore, final YangInstanceIdentifier path) {
        final Request request = renderer.renderGetData(path, datastore);
        return sender.head(request);
    }

    @Override
    public ListenableFuture<Optional<NormalizedNode<?, ?>>> getData(final LogicalDatastoreType datastore, final YangInstanceIdentifier path) {
        final Request request = renderer.renderGetData(path, datastore);
        final ListenableFuture<InputStream> result = sender.get(request);
        return transformReadResult(path, result);
    }

    @Override
    public ListenableFuture<Optional<NormalizedNode<?, ?>>> postOperation(final SchemaPath type, final ContainerNode input) {
        final Request request = renderer.renderOperation(type, input);
        final ListenableFuture<InputStream> result = sender.post(request);
        return Futures.transform(result, new Function<InputStream, Optional<NormalizedNode<?, ?>>>() {
            @Nullable
            @Override
            public Optional<NormalizedNode<?, ?>> apply(@Nullable final InputStream input) {
                return Optional.<NormalizedNode<?, ?>>fromNullable(parser.parseRpcOutput(type, input));
            }
        });
    }

    @Override
    public ListenableFuture<Void> postConfig(final YangInstanceIdentifier path, final NormalizedNode<?, ?> input) {
        final Request request = renderer.renderEditConfig(path, input);
        final ListenableFuture<InputStream> result = sender.post(request);
        return Futures.transform(result, new Function<InputStream, Void>() {
            @Nullable
            @Override
            public Void apply(@Nullable final InputStream input) {
                return null;
            }
        });
    }

    @Override
    public ListenableFuture<Void> putConfig(final YangInstanceIdentifier path, final NormalizedNode<?, ?> input) {
        final Request request = renderer.renderEditConfig(path, input);
        return sender.put(request);
    }

    @Override
    public ListenableFuture<Void> patchConfig(final YangInstanceIdentifier path, final NormalizedNode<?, ?> input) {
        final Request request = renderer.renderEditConfig(path, input);
        return sender.patch(request);
    }

    @Override
    public ListenableFuture<Void> deleteConfig(final YangInstanceIdentifier path) {
        final Request request = renderer.renderDeleteConfig(path);
        return sender.delete(request);
    }

    @Override
    public void close() throws Exception {
        sender.close();
        sseRegistration.close();
    }

    @Override
    public void registerNotificationListener(final RestconfDeviceStreamListener listener) {
        streamsHandler.registerListener(listener);
    }

    @Override
    public Collection<RpcError> parseErrors(final HttpException exception) {
        try {
            return errorParser.parseErrors(exception.getMsg());
        } catch (final Exception e) {
            LOG.warn("Failed to parse exception message, creating new message", e);
            final RpcError rpcError = RpcResultBuilder.newError(RpcError.ErrorType.APPLICATION, "operation-failed", exception.getMessage());
            return Collections.singleton(rpcError);
        }
    }

    private SettableFuture<Optional<NormalizedNode<?, ?>>> transformReadResult(final YangInstanceIdentifier path,
                                                                               final ListenableFuture<InputStream> result) {
        final SettableFuture<Optional<NormalizedNode<?, ?>>> readResult = SettableFuture.create();
        Futures.addCallback(result, new FutureCallback<InputStream>() {
            @Override
            public void onSuccess(@Nullable final InputStream result) {
                readResult.set(Optional.<NormalizedNode<?, ?>>fromNullable(parser.parse(path, result)));
            }

            @Override
            public void onFailure(final Throwable t) {
                if (t instanceof NotFoundException) {
                    readResult.set(Optional.<NormalizedNode<?, ?>>absent());
                } else {
                    readResult.setException(t);
                }
            }
        });
        return readResult;
    }
}