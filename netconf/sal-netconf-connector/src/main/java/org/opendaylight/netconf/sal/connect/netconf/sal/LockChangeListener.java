/*
 * Copyright (c) 2019 Lumina Networks, Inc. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.sal;

import java.util.Collection;
import org.opendaylight.mdsal.binding.api.DataObjectModification;
import org.opendaylight.mdsal.binding.api.DataTreeChangeListener;
import org.opendaylight.mdsal.binding.api.DataTreeModification;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.optional.rev190614.netconf.node.fields.optional.topology.node.DatastoreLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LockChangeListener implements DataTreeChangeListener<DatastoreLock> {

    private static final Logger LOG = LoggerFactory.getLogger(LockChangeListener.class);

    private final NetconfDeviceDataBroker netconfDeviceDataBroker;
    private final NetconfDataTreeServiceImpl netconfDataTreeService;

    public LockChangeListener(final DOMDataBroker netconfDeviceDataBrokder,
                       final NetconfDataTreeService netconfDataTreeService) {
        this.netconfDeviceDataBroker = (NetconfDeviceDataBroker)netconfDeviceDataBrokder;
        this.netconfDataTreeService = (NetconfDataTreeServiceImpl) netconfDataTreeService;
    }

    @Override
    public void onDataTreeChanged(final Collection<DataTreeModification<DatastoreLock>> changes) {
        for (final DataTreeModification<DatastoreLock> change : changes) {
            final DataObjectModification<DatastoreLock> rootNode = change.getRootNode();
            switch (rootNode.getModificationType()) {
                case SUBTREE_MODIFIED:
                case WRITE:
                    if (!rootNode.getDataAfter().getDatastoreLockAllowed()) {
                        LOG.warn("With blocking the lock/unlock operations, the user is coming to "
                                 + "operate in a manner which is not supported. Concurrent access to "
                                 + "the data store may interfere with data consistency.");
                    }
                    netconfDeviceDataBroker.setLockAllowed(rootNode.getDataAfter().getDatastoreLockAllowed());
                    netconfDataTreeService.setLockAllowed(rootNode.getDataAfter().getDatastoreLockAllowed());
                    break;
                case DELETE:
                    netconfDeviceDataBroker.setLockAllowed(true);
                    netconfDataTreeService.setLockAllowed(true);
                    break;
                default:
                    LOG.debug("Unsupported modification type: {}.", rootNode.getModificationType());
            }
        }
    }
}
