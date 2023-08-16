/*
 * Copyright (c) 2017 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.spi;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.credentials.Credentials;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.credentials.credentials.LoginPw;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.credentials.credentials.LoginPwBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.credentials.credentials.login.pw.LoginPasswordBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev221225.CreateDeviceInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev221225.CreateDeviceOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev221225.CreateDeviceOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev221225.DeleteDeviceInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev221225.DeleteDeviceOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev221225.DeleteDeviceOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev221225.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev221225.NetconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev221225.NetconfNodeTopologyService;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Deprecated
public class NetconfTopologyRPCProvider implements NetconfNodeTopologyService {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfTopologyRPCProvider.class);

    private final @NonNull InstanceIdentifier<Topology> topologyPath;
    private final @NonNull AAAEncryptionService encryptionService;
    private final @NonNull DataBroker dataBroker;

    public NetconfTopologyRPCProvider(final DataBroker dataBroker, final AAAEncryptionService encryptionService,
                                      final String topologyId) {
        this.dataBroker = requireNonNull(dataBroker);
        this.encryptionService = requireNonNull(encryptionService);
        topologyPath = InstanceIdentifier.builder(NetworkTopology.class)
                .child(Topology.class, new TopologyKey(new TopologyId(topologyId)))
                .build();
    }

    protected final @NonNull InstanceIdentifier<Topology> topologyPath() {
        return topologyPath;
    }

    @Override
    public final ListenableFuture<RpcResult<CreateDeviceOutput>> createDevice(final CreateDeviceInput input) {
        final NetconfNode node = encryptPassword(input);
        final SettableFuture<RpcResult<CreateDeviceOutput>> futureResult = SettableFuture.create();
        final NodeId nodeId = new NodeId(input.getNodeId());
        writeToConfigDS(node, nodeId, futureResult);
        return futureResult;
    }

    @Override
    public final ListenableFuture<RpcResult<DeleteDeviceOutput>> deleteDevice(final DeleteDeviceInput input) {
        final NodeId nodeId = new NodeId(input.getNodeId());

        final InstanceIdentifier<Node> niid = topologyPath.child(Node.class, new NodeKey(nodeId));

        final WriteTransaction wtx = dataBroker.newWriteOnlyTransaction();
        wtx.delete(LogicalDatastoreType.CONFIGURATION, niid);

        final SettableFuture<RpcResult<DeleteDeviceOutput>> rpcFuture = SettableFuture.create();

        wtx.commit().addCallback(new FutureCallback<CommitInfo>() {

            @Override
            public void onSuccess(final CommitInfo result) {
                LOG.info("delete-device RPC: Removed netconf node successfully.");
                rpcFuture.set(RpcResultBuilder.success(new DeleteDeviceOutputBuilder().build()).build());
            }

            @Override
            public void onFailure(final Throwable exception) {
                LOG.error("delete-device RPC: Unable to remove netconf node.", exception);
                rpcFuture.setException(exception);
            }
        }, MoreExecutors.directExecutor());

        return rpcFuture;
    }

    @VisibleForTesting
    NetconfNode encryptPassword(final CreateDeviceInput input) {
        final NetconfNodeBuilder builder = new NetconfNodeBuilder();
        builder.fieldsFrom(input);

        return builder.setCredentials(handleEncryption(input.getCredentials()))
            .build();
    }

    private Credentials handleEncryption(final Credentials credentials) {
        if (credentials instanceof LoginPw loginPw) {
            final var loginPassword = loginPw.getLoginPassword();

            return new LoginPwBuilder()
                .setLoginPassword(new LoginPasswordBuilder()
                    .setUsername(loginPassword.getUsername())
                    .setPassword(encryptionService.encrypt(loginPassword.getPassword()))
                    .build())
                .build();
        }

        // nothing else needs to be encrypted
        return credentials;
    }

    private void writeToConfigDS(final NetconfNode node, final NodeId nodeId,
            final SettableFuture<RpcResult<CreateDeviceOutput>> futureResult) {

        final WriteTransaction writeTransaction = dataBroker.newWriteOnlyTransaction();
        final InstanceIdentifier<NetconfNode> niid = topologyPath.child(Node.class,
                new NodeKey(nodeId)).augmentation(NetconfNode.class);
        /**
         * Now we get:
         * Caused by: org.opendaylight.yangtools.yang.data.tree.api.SchemaValidationFailedException: Child /(urn:TBD:params:xml:ns:yang:network-topology?revision=2013-10-21)network-topology/topology/topology[{(urn:TBD:params:xml:ns:yang:network-topology?revision=2013-10-21)topology-id=topology-netconf}]/node/node[{(urn:TBD:params:xml:ns:yang:network-topology?revision=2013-10-21)node-id=netconf-test-device}]/(urn:opendaylight:netconf-node-topology?revision=2022-12-25)clustered-connection-status is not present in schema tree.
         *         at org.opendaylight.yangtools.yang.data.tree.impl.InMemoryDataTreeModification.resolveModificationFor(InMemoryDataTreeModification.java:183) ~[bundleFile:?]
         *         at org.opendaylight.yangtools.yang.data.tree.impl.InMemoryDataTreeModification.delete(InMemoryDataTreeModification.java:110) ~[bundleFile:?]
         *         at org.opendaylight.controller.cluster.databroker.actors.dds.LocalReadWriteProxyTransaction.doDelete(LocalReadWriteProxyTransaction.java:156) ~[bundleFile:?]
         *         at org.opendaylight.controller.cluster.databroker.actors.dds.AbstractProxyTransaction.delete(AbstractProxyTransaction.java:296) ~[bundleFile:?]
         *         at org.opendaylight.controller.cluster.databroker.actors.dds.ClientTransaction.delete(ClientTransaction.java:79) ~[bundleFile:?]
         *         at org.opendaylight.controller.cluster.databroker.ClientBackedWriteTransaction.delete(ClientBackedWriteTransaction.java:40) ~[bundleFile:?]
         *         at org.opendaylight.controller.cluster.databroker.AbstractDOMBrokerWriteTransaction.delete(AbstractDOMBrokerWriteTransaction.java:93) ~[bundleFile:?]
         *         at org.opendaylight.mdsal.binding.dom.adapter.BindingDOMWriteTransactionAdapter.put(BindingDOMWriteTransactionAdapter.java:55) ~[bundleFile:?]
         *         at org.opendaylight.mdsal.binding.dom.adapter.BindingDOMWriteTransactionAdapter.mergeParentStructurePut(BindingDOMWriteTransactionAdapter.java:72) ~[bundleFile:?]
         *         at org.opendaylight.netconf.topology.spi.NetconfTopologyRPCProvider.writeToConfigDS(NetconfTopologyRPCProvider.java:139) ~[bundleFile:?]
         *         at org.opendaylight.netconf.topology.spi.NetconfTopologyRPCProvider.createDevice(NetconfTopologyRPCProvider.java:75) ~[bundleFile:?]
         */
        writeTransaction.mergeParentStructurePut(LogicalDatastoreType.CONFIGURATION, niid, node);
        writeTransaction.commit().addCallback(new FutureCallback<CommitInfo>() {

            @Override
            public void onSuccess(final CommitInfo result) {
                LOG.info("add-netconf-node RPC: Added netconf node successfully.");
                futureResult.set(RpcResultBuilder.success(new CreateDeviceOutputBuilder().build()).build());
            }

            @Override
            public void onFailure(final Throwable exception) {
                LOG.error("add-netconf-node RPC: Unable to add netconf node.", exception);
                futureResult.setException(exception);
            }
        }, MoreExecutors.directExecutor());
    }
}
