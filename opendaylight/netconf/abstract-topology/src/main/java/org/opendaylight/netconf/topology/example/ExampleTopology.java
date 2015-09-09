package org.opendaylight.netconf.topology.example;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import javax.annotation.Nonnull;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.netconf.topology.ConnectionAggregator;
import org.opendaylight.netconf.topology.NodeAdministratorCallback;
import org.opendaylight.netconf.topology.Peer;
import org.opendaylight.netconf.topology.util.BaseTopologyAdmin;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeFields;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;

public class ExampleTopology implements NodeAdministratorCallback<NetconfNode, ExampleTopology.CustomMessage> {

    private final BaseTopologyAdmin<NetconfNode, CustomMessage> netconfNodeBaseTopologyAdmin;
    private Peer.PeerContext<CustomMessage> peerCtx;

    public ExampleTopology(final DataBroker dataBroker) {
        netconfNodeBaseTopologyAdmin = new BaseTopologyAdmin<>(dataBroker, "topology-netconf", this,
            new ConnectionAggregator.SingleConnectionAggregator<NetconfNode>());
    }

    @Nonnull @Override public NetconfNode getInitialState(@Nonnull final NodeId nodeId,
        @Nonnull final NetconfNode configNode) {
        return new NetconfNodeBuilder().setConnectionStatus(NetconfNodeFields.ConnectionStatus.Connecting).build();
    }

    @Nonnull @Override public NetconfNode getFailedState(@Nonnull final NodeId nodeId,
        @Nonnull final NetconfNode configNode) {
        return new NetconfNodeBuilder().setConnectionStatus(NetconfNodeFields.ConnectionStatus.UnableToConnect).build();
    }

    @Nonnull @Override public ListenableFuture<NetconfNode> connect(@Nonnull final NodeId nodeId,
        @Nonnull final NetconfNode configNode) {
        // connect magic
        return Futures.immediateFuture(new NetconfNodeBuilder().setConnectionStatus(NetconfNodeFields.ConnectionStatus.Connected).build());
    }

    @Nonnull @Override public ListenableFuture<NetconfNode> update(@Nonnull final NodeId nodeId,
        @Nonnull final NetconfNode configNode) {
        // update magic
        return Futures.immediateFuture(new NetconfNodeBuilder().setConnectionStatus(NetconfNodeFields.ConnectionStatus.Connected).build());
    }

    @Nonnull @Override public ListenableFuture<Void> delete(@Nonnull final NodeId nodeId) {
        // Disconnect
        return Futures.immediateFuture(null);
    }

    @Override public void setPeerContext(final Peer.PeerContext<CustomMessage> peerContext) {
        this.peerCtx = peerContext;
        peerContext.notifyPeers(new CustomMessage());
    }

    @Override public void handle(final CustomMessage msg) {
        // notifications from peers
    }

    public static final class CustomMessage {}
}
