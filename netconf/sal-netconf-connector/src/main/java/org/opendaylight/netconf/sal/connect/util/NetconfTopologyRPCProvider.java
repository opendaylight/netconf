/*
 * Copyright (c) 2017 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.util;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import java.util.concurrent.Future;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.CreateDeviceInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.DeleteDeviceInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeTopologyService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.Credentials;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.credentials.LoginPw;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.credentials.LoginPwBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.credentials.login.pw.LoginPassword;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.credentials.login.pw.LoginPasswordBuilder;
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

public class NetconfTopologyRPCProvider implements NetconfNodeTopologyService {
    private final AAAEncryptionService encryptionService;
    private final DataBroker dataBroker;
    private final String topologyId;
    private static final Logger LOG = LoggerFactory.getLogger(NetconfTopologyRPCProvider.class);

    public NetconfTopologyRPCProvider(final DataBroker dataBroker,
                                      final AAAEncryptionService encryptionService,
                                      final String topologyId) {
        this.dataBroker = dataBroker;
        this.encryptionService = Preconditions.checkNotNull(encryptionService);
        this.topologyId = Preconditions.checkNotNull(topologyId);
    }

    @Override
    public Future<RpcResult<Void>> createDevice(final CreateDeviceInput input) {
        final NetconfNode node = this.encryptPassword(input);
        final SettableFuture<RpcResult<Void>> futureResult = SettableFuture.create();
        final NodeId nodeId = new NodeId(input.getNodeId());
        writeToConfigDS(node, nodeId, topologyId, futureResult);
        return futureResult;
    }

    @VisibleForTesting
    public NetconfNode encryptPassword(final CreateDeviceInput input) {
        final NetconfNodeBuilder builder = new NetconfNodeBuilder();
        builder.fieldsFrom(input);

        final Credentials credentials = handleEncryption(input.getCredentials());
        builder.setCredentials(credentials);

        return builder.build();
    }

    private Credentials handleEncryption(final Credentials credentials) {
        if (credentials instanceof LoginPw) {
            final LoginPassword loginPassword = ((LoginPw) credentials).getLoginPassword();
            final String encryptedPassword =
                    encryptionService.encrypt(loginPassword.getPassword());

            return new LoginPwBuilder().setLoginPassword(new LoginPasswordBuilder()
                    .setPassword(encryptedPassword)
                    .setUsername(loginPassword.getUsername()).build()).build();
        }

        // nothing else needs to be encrypted
        return credentials;
    }

    private void writeToConfigDS(final NetconfNode node, final NodeId nodeId, final String topologyId,
                                 final SettableFuture<RpcResult<Void>> futureResult) {

        final WriteTransaction writeTransaction = dataBroker.newWriteOnlyTransaction();
        final InstanceIdentifier<NetworkTopology> networkTopologyId =
                InstanceIdentifier.builder(NetworkTopology.class).build();
        final InstanceIdentifier<NetconfNode> niid = networkTopologyId.child(Topology.class,
                new TopologyKey(new TopologyId(topologyId))).child(Node.class,
                new NodeKey(nodeId)).augmentation(NetconfNode.class);
        writeTransaction.merge(LogicalDatastoreType.CONFIGURATION, niid, node, true);
        final ListenableFuture<Void> future = writeTransaction.submit();
        Futures.addCallback(future, new FutureCallback<Void>() {

            @Override
            public void onSuccess(final Void result) {
                LOG.info("add-netconf-node RPC: Added netconf node successfully.");
                futureResult.set(RpcResultBuilder.<Void>success().build());
            }

            @Override
            public void onFailure(final Throwable exception) {
                LOG.error("add-netconf-node RPC: Unable to add netconf node.", exception);
                futureResult.setException(exception);
            }
        }, MoreExecutors.directExecutor());
    }


    @Override
    public Future<RpcResult<Void>> deleteDevice(final DeleteDeviceInput input) {
        final NodeId nodeId = new NodeId(input.getNodeId());

        final InstanceIdentifier<NetworkTopology> networkTopologyId =
                InstanceIdentifier.builder(NetworkTopology.class).build();
        final InstanceIdentifier<NetconfNode> niid = networkTopologyId.child(Topology.class,
                new TopologyKey(new TopologyId(topologyId))).child(Node.class,
                new NodeKey(nodeId)).augmentation(NetconfNode.class);

        final WriteTransaction wtx = dataBroker.newWriteOnlyTransaction();
        wtx.delete(LogicalDatastoreType.CONFIGURATION, niid);

        final ListenableFuture<Void> future = wtx.submit();
        final SettableFuture<RpcResult<Void>> rpcFuture = SettableFuture.create();

        Futures.addCallback(future, new FutureCallback<Void>() {

            @Override
            public void onSuccess(final Void result) {
                LOG.info("delete-device RPC: Removed netconf node successfully.");
                rpcFuture.set(RpcResultBuilder.<Void>success().build());
            }

            @Override
            public void onFailure(final Throwable exception) {
                LOG.error("delete-device RPC: Unable to remove netconf node.", exception);
                rpcFuture.setException(exception);
            }
        }, MoreExecutors.directExecutor());

        return rpcFuture;
    }
}
