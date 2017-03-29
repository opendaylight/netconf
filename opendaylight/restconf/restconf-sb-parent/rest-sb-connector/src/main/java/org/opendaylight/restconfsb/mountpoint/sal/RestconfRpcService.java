/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.mountpoint.sal;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.util.Collection;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcAvailabilityListener;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcException;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcImplementationNotAvailableException;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcResult;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.controller.md.sal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.restconfsb.communicator.api.RestconfFacade;
import org.opendaylight.restconfsb.communicator.api.http.HttpException;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Restconf rpc service invokes rpcs by HTTP POST request on /operations resource
 */
public class RestconfRpcService implements DOMRpcService {

    private static final Logger LOG = LoggerFactory.getLogger(RestconfRpcService.class);

    private final RestconfFacade restconf;

    public RestconfRpcService(final RestconfFacade restconf) {
        this.restconf = restconf;
    }

    @Nonnull
    @Override
    public CheckedFuture<DOMRpcResult, DOMRpcException> invokeRpc(@Nonnull final SchemaPath schemaPath,
                                                                  @Nullable final NormalizedNode<?, ?> normalizedNode) {
        LOG.debug("Invoking {}", schemaPath);
        final ListenableFuture<Optional<NormalizedNode<?, ?>>> result = restconf.postOperation(schemaPath, (ContainerNode) normalizedNode);
        final SettableFuture<DOMRpcResult> rpcResult = SettableFuture.create();
        Futures.addCallback(result, new FutureCallback<Optional<NormalizedNode<?, ?>>>() {
            @Override
            public void onSuccess(final Optional<NormalizedNode<?, ?>> normalizedNodeOptional) {
                rpcResult.set(new DefaultDOMRpcResult(normalizedNodeOptional.orNull()));
            }

            @Override
            public void onFailure(final Throwable throwable) {
                if (throwable instanceof HttpException) {
                    final HttpException exc = (HttpException) throwable;
                    final Collection<RpcError> rpcErrors = restconf.parseErrors(exc);
                    rpcResult.set(new DefaultDOMRpcResult(rpcErrors));
                } else {
                    rpcResult.setException(throwable);
                }
            }
        });


        return Futures.makeChecked(rpcResult, new Function<Exception, DOMRpcException>() {
            @Nullable
            @Override
            public DOMRpcException apply(@Nullable final Exception e) {
                return new DOMRpcImplementationNotAvailableException(e, "RPC failed %s", schemaPath);
            }
        });
    }

    @Nonnull
    @Override
    public <T extends DOMRpcAvailabilityListener> ListenerRegistration<T> registerRpcListener(@Nonnull final T t) {
        throw new UnsupportedOperationException("RPC availability listeners not supported");
    }

}