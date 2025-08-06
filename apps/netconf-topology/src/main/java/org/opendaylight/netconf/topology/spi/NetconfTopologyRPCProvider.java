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
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.RpcProviderService;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev241009.credentials.Credentials;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev241009.credentials.credentials.KeyAuthBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev241009.credentials.credentials.LoginPwBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev241009.credentials.credentials.LoginPwUnencryptedBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev241009.credentials.credentials.key.auth.KeyBasedBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev241009.credentials.credentials.login.pw.LoginPasswordBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev241009.credentials.credentials.login.pw.unencrypted.LoginPasswordUnencryptedBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.optional.rev221225.NetconfNodeAugmentedOptionalBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev240911.CreateDevice;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev240911.CreateDeviceInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev240911.CreateDeviceOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev240911.CreateDeviceOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev240911.DeleteDevice;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev240911.DeleteDeviceInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev240911.DeleteDeviceOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev240911.DeleteDeviceOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev240911.NetconfNodeAugment;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev240911.NetconfNodeAugmentBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev240911.netconf.node.augment.NetconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev240911.rpc.credentials.RpcCredentials;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev240911.rpc.credentials.rpc.credentials.KeyAuth;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev240911.rpc.credentials.rpc.credentials.LoginPw;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev240911.rpc.credentials.rpc.credentials.LoginPwUnencrypted;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.binding.DataObjectIdentifier;
import org.opendaylight.yangtools.binding.DataObjectIdentifier.WithKey;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Deprecated
public final class NetconfTopologyRPCProvider implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfTopologyRPCProvider.class);

    private final @NonNull WithKey<Topology, TopologyKey> topologyPath;
    private final @NonNull AAAEncryptionService encryptionService;
    private final @NonNull DataBroker dataBroker;

    private final Registration reg;

    public NetconfTopologyRPCProvider(final RpcProviderService rpcProviderService, final DataBroker dataBroker,
            final AAAEncryptionService encryptionService, final String topologyId) {
        this.dataBroker = requireNonNull(dataBroker);
        this.encryptionService = requireNonNull(encryptionService);
        topologyPath = DataObjectIdentifier.builder(NetworkTopology.class)
            .child(Topology.class, new TopologyKey(new TopologyId(topologyId)))
            .build();
        reg = rpcProviderService.registerRpcImplementations(
            (CreateDevice) this::createDevice,
            (DeleteDevice) this::deleteDevice);
    }

    @Override
    public void close() {
        reg.close();
    }

    private ListenableFuture<RpcResult<CreateDeviceOutput>> createDevice(final CreateDeviceInput input) {
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

    private ListenableFuture<RpcResult<DeleteDeviceOutput>> deleteDevice(final DeleteDeviceInput input) {
        final var nodeId = new NodeId(input.getNodeId());

        final var wtx = dataBroker.newWriteOnlyTransaction();
        wtx.delete(LogicalDatastoreType.CONFIGURATION,
            topologyPath.toBuilder().child(Node.class, new NodeKey(nodeId)).build());

        final var rpcFuture = SettableFuture.<RpcResult<DeleteDeviceOutput>>create();

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
    NetconfNodeAugment encryptPassword(final CreateDeviceInput input) {
        final NetconfNodeBuilder builder = new NetconfNodeBuilder();
        builder.fieldsFrom(input);
        builder.setCredentials(translate(input.getRpcCredentials()));
        return new NetconfNodeAugmentBuilder()
            .setNetconfNode(builder.build())
            .build();
    }

    private Credentials translate(final RpcCredentials credentialsRpc) {
        return switch (credentialsRpc) {
            case KeyAuth keyAuth -> {
                final var loginPassword = keyAuth.getKeyBased();
                yield new KeyAuthBuilder()
                    .setKeyBased(new KeyBasedBuilder()
                        .setUsername(loginPassword.getUsername())
                        .setKeyId(loginPassword.getKeyId())
                        .build())
                    .build();
            }
            case LoginPw loginPw -> {
                final var loginPassword = loginPw.getLoginPassword();
                final byte[] cipherBytes;
                try {
                    cipherBytes = encryptionService.encrypt(
                        loginPassword.getPassword().getBytes(StandardCharsets.UTF_8));
                } catch (GeneralSecurityException e) {
                    throw new IllegalArgumentException("Failed to encrypt password", e);
                }

                yield new LoginPwBuilder()
                    .setLoginPassword(new LoginPasswordBuilder()
                        .setUsername(loginPassword.getUsername())
                        .setPassword(cipherBytes)
                        .build())
                    .build();
            }
            case LoginPwUnencrypted loginPwUnencrypted -> {
                final var loginPassword = loginPwUnencrypted.getLoginPasswordUnencrypted();
                yield new LoginPwUnencryptedBuilder()
                    .setLoginPasswordUnencrypted(new LoginPasswordUnencryptedBuilder()
                        .setUsername(loginPassword.getUsername())
                        .setPassword(loginPassword.getPassword())
                        .build())
                    .build();
            }
            default -> throw new IllegalArgumentException("Unsupported credential type: " + credentialsRpc.getClass());
        };
    }

    private void writeToConfigDS(final Node node, final NodeId nodeId,
            final SettableFuture<RpcResult<CreateDeviceOutput>> futureResult) {
        final var writeTransaction = dataBroker.newWriteOnlyTransaction();
        writeTransaction.mergeParentStructureMerge(LogicalDatastoreType.CONFIGURATION,
            topologyPath.toBuilder().child(Node.class, new NodeKey(nodeId)).build(), node);
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
