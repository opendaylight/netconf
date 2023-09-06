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
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Collection;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.DataTreeChangeListener;
import org.opendaylight.mdsal.binding.api.DataTreeIdentifier;
import org.opendaylight.mdsal.binding.api.DataTreeModification;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.credentials.Credentials;
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
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A listener that reacts to changes in the data tree, specifically related to password changes.
 * If a password is changed and encryption is required, it will update the datastore with the encrypted password.
 *
 * <p>The class requires access to both a {@link DataBroker} for data tree interactions and an
 * {@link AAAEncryptionService} for encryption services.</p>
 **/
@Component(immediate = true)
public class EncryptedPasswordChangeListener implements DataTreeChangeListener<Node>, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(EncryptedPasswordChangeListener.class);

    private final @NonNull DataBroker dataBroker;
    private final @NonNull AAAEncryptionService encryptionService;
    private final @NonNull InstanceIdentifier<Topology> topologyPath;

    private  Registration req;

    /**
     * Constructor for the EncryptedPasswordChangeListener.
     *
     * @param dataBroker        The data broker service for accessing the data tree.
     * @param encryptionService The service for encryption utilities.
     */
    @SuppressFBWarnings(value = "MC_OVERRIDABLE_METHOD_CALL_IN_CONSTRUCTOR",
        justification = "DTCL registration of 'this'")
    @Inject
    @Activate
    public EncryptedPasswordChangeListener(@Reference final DataBroker dataBroker,
            @Reference final AAAEncryptionService encryptionService) {
        this.dataBroker = requireNonNull(dataBroker);
        this.encryptionService = requireNonNull(encryptionService);
        topologyPath = InstanceIdentifier.builder(NetworkTopology.class)
            .child(Topology.class, new TopologyKey(new TopologyId(NetconfNodeUtils.DEFAULT_TOPOLOGY_NAME)))
            .build();
        req = dataBroker.registerDataTreeChangeListener(DataTreeIdentifier.create(LogicalDatastoreType.CONFIGURATION,
            topologyPath.child(Node.class)), this);
    }

    @Override
    public void onDataTreeChanged(@NonNull final Collection<DataTreeModification<Node>> changes) {
        for (final var change : changes) {
            final var rootNode = change.getRootNode();
            final var netconfNodeChange = rootNode.getModifiedAugmentation(NetconfNode.class);
            if (netconfNodeChange == null) {
                continue;
            }

            final var passwordBefore = getPassword(netconfNodeChange.getDataBefore());
            final var passwordAfter = getPassword(netconfNodeChange.getDataAfter());

            if (isEncryptionRequired(passwordBefore, passwordAfter)) {
                final var credentialsAfter = netconfNodeChange.getDataAfter().getCredentials();
                final var credentials = handleEncryption(credentialsAfter);
                updateDatastoreWithEncryptedPassword(rootNode.getDataAfter(), credentials);
            }
        }
    }

    @PreDestroy
    @Deactivate
    @Override
    public void close() throws Exception {
        if (req != null) {
            req.close();
            req = null;
        }
    }

    private void updateDatastoreWithEncryptedPassword(final Node node, final Credentials credentials) {
        final WriteTransaction writeTransaction = dataBroker.newWriteOnlyTransaction();
        final InstanceIdentifier<NetconfNode> niid = topologyPath.child(Node.class, new NodeKey(node.getNodeId()))
            .augmentation(NetconfNode.class);

        // Create a new NetconfNode with the encrypted password and other unchanged details
        final NetconfNode updatedNode = new NetconfNodeBuilder().setCredentials(credentials).build();

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
        }, MoreExecutors.directExecutor());
    }

    private Credentials handleEncryption(final Credentials credentials) {
        if (credentials instanceof LoginPw loginPassword) {
            return new LoginPwBuilder()
                .setLoginPassword(new LoginPasswordBuilder()
                    .setUsername(loginPassword.getLoginPassword().getUsername())
                    .setPassword(encryptionService.encrypt(loginPassword.getLoginPassword().getPassword()))
                    .build())
                .build();
        }

        // nothing else needs to be encrypted
        return credentials;
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    private boolean isEncryptionRequired(final String passwordBefore, final String passwordAfter) {
        if (passwordAfter == null || passwordAfter.equals(passwordBefore)) {
            return false;
        }
        try {
            final String decryptedPasswordAfter = encryptionService.decrypt(passwordAfter);
            return passwordAfter.equals(decryptedPasswordAfter);
        } catch (RuntimeException e) {
            return true;
        }
    }

    private static String getPassword(final NetconfNode node) {
        if (node == null) {
            return null;
        }
        final Credentials credentials = node.getCredentials();
        if (credentials instanceof LoginPw loginPassword) {
            return loginPassword.getLoginPassword().getPassword();
        }
        return null;
    }
}
