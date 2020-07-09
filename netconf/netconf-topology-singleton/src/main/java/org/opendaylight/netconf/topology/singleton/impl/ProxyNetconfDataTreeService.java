package org.opendaylight.netconf.topology.singleton.impl;

import akka.actor.ActorRef;
import akka.pattern.AskTimeoutException;
import akka.util.Timeout;
import com.google.common.util.concurrent.ListenableFuture;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import java.util.Optional;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.netconf.api.ModifyAction;
import org.opendaylight.netconf.api.NetconfDataTreeService;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologyUtils;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.concurrent.ExecutionContext;

public class ProxyNetconfDataTreeService implements NetconfDataTreeService {
    private static final Logger LOG = LoggerFactory.getLogger(ProxyNetconfDataTreeService.class);

    private final Timeout askTimeout;
    private final RemoteDeviceId id;
    private final ActorRef masterNode;
    private final ExecutionContext executionContext;

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
    public List<ListenableFuture<? extends DOMRpcResult>> lock() {
        return null;
    }

    @Override
    public void unlock() {

    }

    @Override
    public void discardChanges() {

    }

    @Override
    public ListenableFuture<Optional<NormalizedNode<?, ?>>> get(YangInstanceIdentifier path) {
        return null;
    }

    @Override
    public ListenableFuture<Optional<NormalizedNode<?, ?>>> getConfig(YangInstanceIdentifier path) {
        return null;
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> merge(LogicalDatastoreType store, YangInstanceIdentifier path, NormalizedNode<?, ?> data, Optional<ModifyAction> defaultOperation) {
        return null;
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> replace(LogicalDatastoreType store, YangInstanceIdentifier path, NormalizedNode<?, ?> data, Optional<ModifyAction> defaultOperation) {
        return null;
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> create(LogicalDatastoreType store, YangInstanceIdentifier path, NormalizedNode<?, ?> data, Optional<ModifyAction> defaultOperation) {
        return null;
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> delete(LogicalDatastoreType store, YangInstanceIdentifier path) {
        return null;
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> remove(LogicalDatastoreType store, YangInstanceIdentifier path) {
        return null;
    }

    @Override
    public ListenableFuture<? extends CommitInfo> commit(List<ListenableFuture<? extends DOMRpcResult>> resultsFutures) {
        return null;
    }

    @Override
    public @NonNull Object getDeviceId() {
        return id;
    }

    @SuppressFBWarnings(value = "UPM_UNCALLED_PRIVATE_METHOD",
        justification = "https://github.com/spotbugs/spotbugs/issues/811")
    private Throwable processFailure(final Throwable failure) {
        return failure instanceof AskTimeoutException
            ? NetconfTopologyUtils.createMasterIsDownException(id, (Exception) failure) : failure;
    }
}
