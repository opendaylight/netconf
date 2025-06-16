package org.opendaylight.netconf.client.mdsal.spi;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.List;
import javax.xml.transform.dom.DOMSource;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.netconf.api.EffectiveOperation;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.impl.NetconfBaseOps;
import org.opendaylight.netconf.client.mdsal.impl.NetconfRpcFutureCallback;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.AnyxmlNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

public class Running extends AbstractDataStore {
    private final List<AnyxmlNode<DOMSource>> nodes = new ArrayList<>();

    Running(NetconfBaseOps netconfOps, final RemoteDeviceId id, boolean rollbackSupport,
            boolean lockDatastore) {
        super(netconfOps, id, rollbackSupport, lockDatastore);
    }

    ListenableFuture<? extends DOMRpcResult> lock() {
        if (lockDatastore && !lock.getAndSet(true)) {
            return netconfOps.lockRunning(new NetconfRpcFutureCallback("Lock running", id));
        }
        return RPC_SUCCESS;
    }

    @Override
    ListenableFuture<? extends DOMRpcResult> unlock() {
        if (lockDatastore && lock.getAndSet(false)) {
            return netconfOps.unlockRunning(new NetconfRpcFutureCallback("Unlock running", id));
        }
        return RPC_SUCCESS;
    }

    @Override
    public void cancel() {
        readListCache.clear();
        nodes.clear();
        executeWithLogging(this::unlock);
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> commit() {
        final var editConfigStructure = netconfOps.createEditConfigStructure(nodes);
        nodes.clear();
        final var callback = new NetconfRpcFutureCallback("Edit running", id);
        return addIntoFutureChain(lock(), () -> netconfOps.editConfigRunning(callback, editConfigStructure,
            rollbackSupport), true);
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> editConfig(final AnyxmlNode<DOMSource> node) {
        nodes.add(node);
        return RPC_SUCCESS;
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> editConfig(final EffectiveOperation operation,
            final NormalizedNode child, final YangInstanceIdentifier path) {
        nodes.add(netconfOps.createNode(child, operation, path));
        return RPC_SUCCESS;
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> editConfig(EffectiveOperation operation, YangInstanceIdentifier path) {
        nodes.add(netconfOps.createNode(operation, path));
        return RPC_SUCCESS;
    }
}
