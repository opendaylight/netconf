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
import java.util.Base64;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev240223.credentials.Credentials;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev240223.credentials.credentials.KeyAuthBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev240223.credentials.credentials.LoginPwBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev240223.credentials.credentials.LoginPwUnencryptedBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev240223.credentials.credentials.key.auth.KeyBasedBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev240223.credentials.credentials.login.pw.LoginPasswordBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev240223.credentials.credentials.login.pw.unencrypted.LoginPasswordUnencryptedBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.optional.rev221225.NetconfNodeAugmentedOptionalBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev240223.CreateDeviceInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev240223.CreateDeviceOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev240223.CreateDeviceOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev240223.DeleteDeviceInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev240223.DeleteDeviceOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev240223.DeleteDeviceOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev240223.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev240223.NetconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev240223.NetconfNodeTopologyService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev240223.rpc.credentials.RpcCredentials;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev240223.rpc.credentials.rpc.credentials.KeyAuth;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev240223.rpc.credentials.rpc.credentials.LoginPw;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev240223.rpc.credentials.rpc.credentials.LoginPwUnencrypted;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
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
        final var netconfNode = encryptPassword(input);
        final var nodeId = new NodeId(input.getNodeId());
        final var nodeBuilder = new NodeBuilder()
            .setNodeId(nodeId)
            .addAugmentation(netconfNode);
        if (input.getIgnoreMissingSchemaSources() != null) {
            final var netconfNodeOptionalBuilder = new NetconfNodeAugmentedOptionalBuilder(input);
            nodeBuilder.addAugmentation(netconfNodeOptionalBuilder.build());
        }
        final SettableFuture<RpcResult<CreateDeviceOutput>> futureResult = SettableFuture.create();
        writeToConfigDS(nodeBuilder.build(), nodeId, futureResult);
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

        return builder.setCredentials(translate(input.getRpcCredentials()))
            .build();
    }

    private Credentials translate(final RpcCredentials credentialsRpc) {
        if (credentialsRpc instanceof LoginPw loginPw) {
            final var loginPassword = loginPw.getLoginPassword();

            return new LoginPwBuilder()
                .setLoginPassword(new LoginPasswordBuilder()
                    .setUsername(loginPassword.getUsername())
                    .setPassword(Base64.getDecoder().decode(encryptionService.encrypt(loginPassword.getPassword())))
                    .build())
                .build();
        } else if (credentialsRpc instanceof LoginPwUnencrypted loginPwUnencrypted) {
            final var loginPassword = loginPwUnencrypted.getLoginPasswordUnencrypted();
            return new LoginPwUnencryptedBuilder()
                .setLoginPasswordUnencrypted(new LoginPasswordUnencryptedBuilder()
                    .setUsername(loginPassword.getUsername())
                    .setPassword(loginPassword.getPassword())
                    .build())
                .build();
        } else if (credentialsRpc instanceof KeyAuth keyAuth) {
            final var loginPassword = keyAuth.getKeyBased();
            return new KeyAuthBuilder()
                .setKeyBased(new KeyBasedBuilder()
                    .setUsername(loginPassword.getUsername())
                    .setKeyId(loginPassword.getKeyId())
                    .build())
                .build();
        } else {
            throw new IllegalArgumentException("Unsupported credential type: " + credentialsRpc.getClass());
        }
    }

    private void writeToConfigDS(final Node node, final NodeId nodeId,
            final SettableFuture<RpcResult<CreateDeviceOutput>> futureResult) {

        final WriteTransaction writeTransaction = dataBroker.newWriteOnlyTransaction();
        final InstanceIdentifier<Node> niid = topologyPath.child(Node.class, new NodeKey(nodeId));
        writeTransaction.mergeParentStructureMerge(LogicalDatastoreType.CONFIGURATION, niid, node);
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
