/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;
import java.util.Optional;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.pattern.Patterns;
import org.apache.pekko.util.Timeout;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.spi.DataOperationService;
import org.opendaylight.netconf.client.mdsal.spi.DataStoreService;
import org.opendaylight.netconf.databind.DatabindPath;
import org.opendaylight.netconf.topology.singleton.impl.netconf.ProxyNetconfService;
import org.opendaylight.netconf.topology.singleton.messages.netconf.NetconfDataTreeServiceRequest;
import org.opendaylight.restconf.server.api.DataGetParams;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import scala.concurrent.ExecutionContext;

public class ProxyNetconfDataTreeService implements DataStoreService {

    private final ProxyNetconfService proxyNetconfService;

    /**
     * Constructor for {@code ProxyNetconfDataTreeService}.
     *
     * @param id               id
     * @param masterNode       {@link org.opendaylight.netconf.topology.singleton.impl.actors.NetconfNodeActor} ref
     * @param executionContext ExecutionContext
     * @param askTimeout       ask timeout
     */
    public ProxyNetconfDataTreeService(final RemoteDeviceId id, final ActorRef masterNode,
                                       final ExecutionContext executionContext, final Timeout askTimeout) {
        final var masterActor = Patterns.ask(masterNode, new NetconfDataTreeServiceRequest(), askTimeout);
        proxyNetconfService = new ProxyNetconfService(id, masterActor, executionContext, askTimeout);
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> create(YangInstanceIdentifier path, NormalizedNode data) {
        return proxyNetconfService.create(path, data);
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> delete(YangInstanceIdentifier path) {
        return proxyNetconfService.delete(path);
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> remove(YangInstanceIdentifier path) {
        return proxyNetconfService.remove(path);
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> merge(YangInstanceIdentifier path, NormalizedNode data) {
        return proxyNetconfService.merge(path, data);
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> put(YangInstanceIdentifier path, NormalizedNode data) {
        return proxyNetconfService.put(path, data);
    }

    @Override
    public ListenableFuture<Optional<NormalizedNode>> read(LogicalDatastoreType store, YangInstanceIdentifier path,
            List<YangInstanceIdentifier> fields) {
        return proxyNetconfService.read(store, path, fields);
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> commit() {
        return proxyNetconfService.commit();
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> cancel() {
        return proxyNetconfService.cancel();
    }
}
