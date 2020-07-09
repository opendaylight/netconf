package org.opendaylight.netconf.topology.singleton.impl;

import akka.actor.ActorRef;
import akka.pattern.Patterns;
import akka.util.Timeout;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;
import java.util.Optional;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.netconf.api.ModifyAction;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.netconf.topology.singleton.impl.netconf.ProxyNetconfService;
import org.opendaylight.netconf.topology.singleton.messages.netconf.NetconfDataTreeServiceRequest;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;

public class ProxyNetconfDataTreeService implements NetconfDataTreeService {
    private final Timeout askTimeout;
    private final RemoteDeviceId id;
    private final ActorRef masterNode;
    private final ExecutionContext executionContext;

    private volatile ProxyNetconfService proxyNetconfService;

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
        this.id = id;
        this.masterNode = masterNode;
        this.executionContext = executionContext;
        this.askTimeout = askTimeout;
    }

    @Override
    public synchronized List<ListenableFuture<? extends DOMRpcResult>> lock() {
        final Future<Object> masterActor = Patterns.ask(masterNode, new NetconfDataTreeServiceRequest(), askTimeout);
        proxyNetconfService = new ProxyNetconfService(id, masterActor, executionContext, askTimeout);
        return proxyNetconfService.lock();
    }

    @Override
    public void unlock() {
        proxyNetconfService.unlock();
    }

    @Override
    public void discardChanges() {
        proxyNetconfService.discardChanges();
    }

    @Override
    public ListenableFuture<Optional<NormalizedNode<?, ?>>> get(YangInstanceIdentifier path) {
        final Future<Object> masterActor = Patterns.ask(masterNode, new NetconfDataTreeServiceRequest(), askTimeout);
        ProxyNetconfService netconfService = new ProxyNetconfService(id, masterActor, executionContext, askTimeout);
        return netconfService.get(path);
    }


    @Override
    public ListenableFuture<Optional<NormalizedNode<?, ?>>> getConfig(YangInstanceIdentifier path) {
        final Future<Object> masterActor = Patterns.ask(masterNode, new NetconfDataTreeServiceRequest(), askTimeout);
        ProxyNetconfService netconfService = new ProxyNetconfService(id, masterActor, executionContext, askTimeout);
        return netconfService.getConfig(path);
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> merge(LogicalDatastoreType store, YangInstanceIdentifier path,
                                                          NormalizedNode<?, ?> data,
                                                          Optional<ModifyAction> defaultOperation) {
        return proxyNetconfService.merge(store, path, data, defaultOperation);
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> replace(LogicalDatastoreType store, YangInstanceIdentifier path,
                                                            NormalizedNode<?, ?> data,
                                                            Optional<ModifyAction> defaultOperation) {
        return proxyNetconfService.replace(store, path, data, defaultOperation);
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> create(LogicalDatastoreType store, YangInstanceIdentifier path,
                                                           NormalizedNode<?, ?> data,
                                                           Optional<ModifyAction> defaultOperation) {
        return proxyNetconfService.create(store, path, data, defaultOperation);
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> delete(LogicalDatastoreType store, YangInstanceIdentifier path) {
        return proxyNetconfService.delete(store, path);
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> remove(LogicalDatastoreType store, YangInstanceIdentifier path) {
        return proxyNetconfService.remove(store, path);
    }

    @Override
    public ListenableFuture<? extends CommitInfo> commit(
        List<ListenableFuture<? extends DOMRpcResult>> resultsFutures) {
        return proxyNetconfService.commit(resultsFutures);
    }

    @Override
    public @NonNull Object getDeviceId() {
        return id;
    }
}
