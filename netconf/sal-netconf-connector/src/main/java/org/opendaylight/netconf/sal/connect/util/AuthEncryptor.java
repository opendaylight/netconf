/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.util;

import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *  Utility to encrypt netconf username and password.
 */
public class AuthEncryptor {
    private static final Logger LOG = LoggerFactory.getLogger(AuthEncryptor.class);

    public static boolean encryptIfNeeded(final NodeId nodeId, final NetconfNode netconfNode,
                                 AAAEncryptionService encryptionService,
                                 final String topologyId, final DataBroker dataBroker) {
        final org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node
                .credentials.credentials.LoginPassword creds =
                (org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node
                        .credentials.credentials.LoginPassword) netconfNode.getCredentials();
        final String decryptedPassword = encryptionService.decrypt(creds.getPassword());
        if (decryptedPassword != null && decryptedPassword.equals(creds.getPassword())) {
            LOG.info("Encrypting the provided credentials");
            final String username = encryptionService.encrypt(creds.getUsername());
            final String password = encryptionService.encrypt(creds.getPassword());
            final org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node
                    .credentials.credentials.LoginPasswordBuilder passwordBuilder =
                    new org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114
                            .netconf.node.credentials.credentials.LoginPasswordBuilder();
            passwordBuilder.setUsername(username);
            passwordBuilder.setPassword(password);
            final NetconfNodeBuilder nnb = new NetconfNodeBuilder();
            nnb.setCredentials(passwordBuilder.build());

            final WriteTransaction writeTransaction = dataBroker.newWriteOnlyTransaction();
            final InstanceIdentifier<NetworkTopology> networkTopologyId =
                    InstanceIdentifier.builder(NetworkTopology.class).build();
            final InstanceIdentifier<NetconfNode> niid = networkTopologyId.child(Topology.class,
                    new TopologyKey(new TopologyId(topologyId))).child(Node.class,
                    new NodeKey(nodeId)).augmentation(NetconfNode.class);
            writeTransaction.merge(LogicalDatastoreType.CONFIGURATION, niid, nnb.build());
            final CheckedFuture<Void, TransactionCommitFailedException> future = writeTransaction.submit();
            Futures.addCallback(future, new FutureCallback<Void>() {

                @Override
                public void onSuccess(Void result) {
                    LOG.info("Encrypted netconf username/password successfully");
                }

                @Override
                public void onFailure(Throwable exception) {
                    LOG.error("Unable to encrypt netconf username/password." + exception.getMessage());
                }
            });
            return true;
        }
        return false;
    }
}
