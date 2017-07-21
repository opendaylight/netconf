/*
 * Copyright (c) 2017 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.AddNetconfNodeInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeTopologyService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.credentials.LoginPassword;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.credentials.LoginPasswordBuilder;
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

import java.util.concurrent.Future;

public class NetconfTopologyRPCProvider implements NetconfNodeTopologyService{
    private final AAAEncryptionService encryptionService;
    private final DataBroker           dataBroker;
    private final String topologyId;
    private static final Logger LOG = LoggerFactory.getLogger(NetconfTopologyRPCProvider.class);
    public NetconfTopologyRPCProvider(final DataBroker dataBroker,
                                      final AAAEncryptionService encryptionService,
                                      final String topologyId){
        this.dataBroker = dataBroker;
        this.encryptionService = Preconditions.checkNotNull(encryptionService);
        this.topologyId = Preconditions.checkNotNull(topologyId);
    }

    @Override
    public Future<RpcResult<Void>> addNetconfNode(AddNetconfNodeInput input) {
        NodeId nodeId = new NodeId(input.getNodeId());
        NetconfNodeBuilder builder = new NetconfNodeBuilder();
        builder.fieldsFrom(input);

        boolean encrypt = input.isEncrypt();
        LoginPassword loginPassword = (LoginPassword)input.getCredentials();
        if(encrypt) {
            String encryptedPassword = encryptionService.encrypt(loginPassword.getPassword());
            LoginPassword newCreds = new LoginPasswordBuilder().setPassword(encryptedPassword)
                    .setUsername(loginPassword.getUsername()).build();
            builder.setCredentials(newCreds);
        }
        
        NetconfNode node = builder.build();
        final SettableFuture<RpcResult<Void>> futureResult = SettableFuture.create();
        writeToConfigDS(node, nodeId, topologyId, futureResult);
        return futureResult;
    }

    private void writeToConfigDS(NetconfNode node, NodeId nodeId, String topologyId,
                                 final SettableFuture<RpcResult<Void>> futureResult){

        WriteTransaction writeTransaction = dataBroker.newWriteOnlyTransaction();
        final InstanceIdentifier<NetworkTopology> networkTopologyId =
                InstanceIdentifier.builder(NetworkTopology.class).build();
        final InstanceIdentifier<NetconfNode> niid = networkTopologyId.child(Topology.class,
                              new TopologyKey(new TopologyId(topologyId))).child(Node.class,
                              new NodeKey(nodeId)).augmentation(NetconfNode.class);
        writeTransaction.merge(LogicalDatastoreType.CONFIGURATION, niid, node, true);
        final CheckedFuture<Void, TransactionCommitFailedException> future = writeTransaction.submit();
        Futures.addCallback(future, new FutureCallback<Void>() {

            @Override
            public void onSuccess(Void result) {
                LOG.info("Encrypted netconf username/password successfully");
                futureResult.set(RpcResultBuilder.<Void>success().build());
            }

            @Override
            public void onFailure(Throwable exception) {
                LOG.error("Unable to encrypt netconf username/password." + exception.getMessage());
                futureResult.setException(exception);
            }
        });
    }

}
