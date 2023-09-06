/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.spi;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.Collection;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.DataObjectModification;
import org.opendaylight.mdsal.binding.api.DataTreeChangeListener;
import org.opendaylight.mdsal.binding.api.DataTreeIdentifier;
import org.opendaylight.mdsal.binding.api.DataTreeModification;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.credentials.Credentials;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.credentials.credentials.LoginPassword;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.credentials.credentials.LoginPw;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.credentials.credentials.LoginPwBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.credentials.credentials.login.pw.LoginPasswordBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev221225.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev221225.NetconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(immediate = true)
public class EncryptedPasswordChangeListener implements DataTreeChangeListener<Node>, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(EncryptedPasswordChangeListener.class);

    private final @NonNull DataBroker dataBroker;
    private final @NonNull AAAEncryptionService encryptionService;
    private ListenerRegistration<EncryptedPasswordChangeListener> req;
    private final @NonNull InstanceIdentifier<Topology> topologyPath;

    @Activate
    public EncryptedPasswordChangeListener(final DataBroker dataBroker, final AAAEncryptionService encryptionService,
            final String topologyId) {
        this.dataBroker = requireNonNull(dataBroker);
        this.encryptionService = requireNonNull(encryptionService);
        topologyPath = InstanceIdentifier.builder(NetworkTopology.class)
            .child(Topology.class, new TopologyKey(new TopologyId(topologyId)))
            .build();
        req = dataBroker.registerDataTreeChangeListener(DataTreeIdentifier.create(LogicalDatastoreType.CONFIGURATION,
            topologyPath.child(Node.class)), this);
    }

    @Override
    public void onDataTreeChanged(@NonNull final Collection<DataTreeModification<Node>> changes) {
        for (final var change : changes) {
            final DataObjectModification<Node> rootNode = change.getRootNode();

            String passwordBefore = null;
            String passwordAfter = null;

            final Credentials credentialsBefore = rootNode.getModifiedAugmentation(NetconfNode.class).getDataBefore()
                .getCredentials();
            if (credentialsBefore instanceof LoginPassword loginPassword) {
                passwordBefore = loginPassword.getPassword();
            }

            final NetconfNode nodeDataAfter = rootNode.getModifiedAugmentation(NetconfNode.class).getDataAfter();
            final Credentials credentialsAfter = nodeDataAfter.getCredentials();
            if (credentialsAfter instanceof LoginPassword loginPassword) {
                passwordAfter = loginPassword.getPassword();
            }
            if (passwordBefore != null && passwordAfter != null && !passwordBefore.equals(passwordAfter)) {
                try {
                    final String decryptedPassword = encryptionService.decrypt(passwordAfter);
                    if (!decryptedPassword.equals(passwordBefore)) {
                        updateDatastoreWithEncryptedPassword(rootNode.getDataAfter(),
                            handleEncryption(credentialsAfter));
                    }
                } catch (Exception e) {
                    updateDatastoreWithEncryptedPassword(rootNode.getDataAfter(), handleEncryption(credentialsAfter));
                }
            }

        }
    }

    @Deactivate
    @Override
    public void close() throws Exception {
        if (req != null) {
            req.close();
            req = null;
        }
    }

    private void updateDatastoreWithEncryptedPassword(final Node node, final Credentials credentials) {
        WriteTransaction writeTransaction = dataBroker.newWriteOnlyTransaction();
        InstanceIdentifier<NetconfNode> niid = topologyPath.child(Node.class, new NodeKey(node.getNodeId()))
            .augmentation(NetconfNode.class);

        // Create a new NetconfNode with the encrypted password and other unchanged details
        NetconfNode updatedNode = new NetconfNodeBuilder().setCredentials(credentials).build();

        // Update the datastore
        writeTransaction.mergeParentStructureMerge(LogicalDatastoreType.CONFIGURATION, niid, updatedNode);
        writeTransaction.commit().addCallback(new FutureCallback<CommitInfo>() {

            @Override
            public void onSuccess(final CommitInfo result) {
                LOG.info("Credentials has been updated successfully.");
            }

            @Override
            public void onFailure(final Throwable exception) {
                LOG.error("Unable to update credentials", exception);
            }
        }, MoreExecutors.directExecutor());;
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
}
