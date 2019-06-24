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
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.optional.rev190621.netconf.node.fields.optional.topology.node.DatastoreLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class LockChangeListener implements DataTreeChangeListener<DatastoreLock> {

    private static final Logger LOG = LoggerFactory.getLogger(LockChangeListener.class);

    private final NetconfDeviceDataBroker netconfDeviceDataBroker;

    LockChangeListener(final DOMDataBroker netconfDeviceDataBrokder) {
        this.netconfDeviceDataBroker = (NetconfDeviceDataBroker)netconfDeviceDataBrokder;
    }

    @Override
    public void onDataTreeChanged(final Collection<DataTreeModification<DatastoreLock>> changes) {
        for (final DataTreeModification<DatastoreLock> change : changes) {
            final DataObjectModification<DatastoreLock> rootNode = change.getRootNode();
            switch (rootNode.getModificationType()) {
                case SUBTREE_MODIFIED:
                case WRITE:
                    netconfDeviceDataBroker.setLockAllowed(rootNode.getDataAfter().isDatastoreLockAllowed());
                    break;
                case DELETE:
                    netconfDeviceDataBroker.setLockAllowed(true);
                    break;
                default:
                    LOG.debug("Unsupported modification type: {}.", rootNode.getModificationType());
            }
        }
    }
}
