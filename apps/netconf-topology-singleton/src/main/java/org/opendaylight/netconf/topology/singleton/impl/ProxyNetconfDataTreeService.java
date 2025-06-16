/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.Optional;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.pattern.Patterns;
import org.apache.pekko.util.Timeout;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.spi.DataOperationService;
import org.opendaylight.netconf.databind.DatabindPath;
import org.opendaylight.netconf.topology.singleton.impl.netconf.ProxyNetconfService;
import org.opendaylight.netconf.topology.singleton.messages.netconf.NetconfDataTreeServiceRequest;
import org.opendaylight.restconf.server.api.DataGetParams;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import scala.concurrent.ExecutionContext;

public class ProxyNetconfDataTreeService implements DataOperationService {

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
    public ListenableFuture<? extends DOMRpcResult> createData(YangInstanceIdentifier path, NormalizedNode data) {
        return proxyNetconfService.createData(path, data);
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> deleteData(DatabindPath.Data path) {
        return proxyNetconfService.deleteData(path);
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> removeData(DatabindPath.Data path) {
        return proxyNetconfService.removeData(path);
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> mergeData(YangInstanceIdentifier path, NormalizedNode data) {
        return proxyNetconfService.mergeData(path, data);
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> putData(YangInstanceIdentifier path, NormalizedNode data) {
        return proxyNetconfService.putData(path, data);
    }

    @Override
    public ListenableFuture<Optional<NormalizedNode>> getData(DatabindPath.Data path, DataGetParams params) {
        return proxyNetconfService.getData(path, params);
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> commit() {
        return proxyNetconfService.commit();
    }
}
