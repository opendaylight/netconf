/*
 * Copyright (c) 2020 Pantheon Technologies, s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.test;

import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.aries.blueprint.annotation.service.Reference;
import org.opendaylight.mdsal.dom.api.DOMMountPointListener;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class MountedDeviceListener implements DOMMountPointListener {

    private static final Logger LOG = LoggerFactory.getLogger(MountedDeviceListener.class);
    private static final String TEST_NODE_PREFIX = "qa-";
    private static final QName NODE_QNAME = QName.create(Node.QNAME, "node-id").intern();
    private final DOMMountPointService mountPointService;

    @Inject
    public MountedDeviceListener(final @Reference DOMMountPointService mountPointService) {
        this.mountPointService = mountPointService;
        mountPointService.registerProvisionListener(this);
    }

    @Override
    public void onMountPointCreated(final YangInstanceIdentifier path) {
        final Optional<String> optNodeId = getNodeId(path);
        if (optNodeId.isPresent() && optNodeId.get().startsWith(TEST_NODE_PREFIX)) {
            LOG.error("Test node mounted: {}", optNodeId.get());
            // do dirty things
        }
    }

    @Override
    public void onMountPointRemoved(final YangInstanceIdentifier path) {
        // do nothing?
    }

    private Optional<String> getNodeId(final YangInstanceIdentifier path) {
        if (path.getLastPathArgument() instanceof NodeIdentifierWithPredicates) {
            final NodeIdentifierWithPredicates nodeIId = ((NodeIdentifierWithPredicates) path.getLastPathArgument());
            return Optional.ofNullable(nodeIId.getValue(NODE_QNAME, String.class));
        } else {
            return Optional.empty();
        }

    }

}
