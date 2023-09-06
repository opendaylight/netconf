/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.spi;

import static java.util.Objects.requireNonNull;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.DataObjectModification;
import org.opendaylight.mdsal.binding.api.DataTreeChangeListener;
import org.opendaylight.mdsal.binding.api.DataTreeIdentifier;
import org.opendaylight.mdsal.binding.api.DataTreeModification;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.credentials.Credentials;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev221225.CreateDeviceInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev221225.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
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

    @Activate
    public EncryptedPasswordChangeListener(final DataBroker dataBroker, final AAAEncryptionService encryptionService) {
        this.dataBroker = requireNonNull(dataBroker);
        this.encryptionService = requireNonNull(encryptionService);
        req = dataBroker.registerDataTreeChangeListener(DataTreeIdentifier.create(LogicalDatastoreType.CONFIGURATION,
            NetconfNodeUtils.DEFAULT_TOPOLOGY_IID.child(Node.class)), this);
    }

    @Override
    public void onDataTreeChanged(@NonNull final Collection<DataTreeModification<Node>> changes) {
        for (final var change : changes) {
            DataObjectModification<Node> rootNode = change.getRootNode();

            Node before = change.getRootNode().getDataBefore();
            final var passwordBefore = getPassword(before);
            Node after = change.getRootNode().getDataAfter();
            final var passwordAfter = getPassword(after);
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

    private static String getPassword(final Node node) {
        final var netconfNodeAugmentation = node.augmentation(NetconfNode.class);
        final var nodeCredentials = netconfNodeAugmentation.getCredentials().toString();
        final var matcher = Pattern.compile("password=([^,]+)").matcher(nodeCredentials);
        return matcher.group(1);
    }
}
