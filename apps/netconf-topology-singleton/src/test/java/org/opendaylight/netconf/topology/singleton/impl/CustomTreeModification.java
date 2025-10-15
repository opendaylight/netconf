/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl;

import static java.util.Objects.requireNonNull;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.mdsal.binding.api.DataObjectModification;
import org.opendaylight.mdsal.binding.api.DataTreeModification;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.binding.DataObjectIdentifier;

@NonNullByDefault
final class CustomTreeModification implements DataTreeModification<Node> {
    private final LogicalDatastoreType datastore;
    private final DataObjectIdentifier<Node> path;
    private final DataObjectModification<Node> rootNode;

    CustomTreeModification(final LogicalDatastoreType datastore, final DataObjectIdentifier<Node> path,
            final DataObjectModification<Node> rootNode) {
        this.datastore = requireNonNull(datastore);
        this.path = requireNonNull(path);
        this.rootNode = requireNonNull(rootNode);
    }

    @Override
    public DataObjectModification<Node> getRootNode() {
        return rootNode;
    }

    @Override
    public LogicalDatastoreType datastore() {
        return datastore;
    }

    @Override
    public DataObjectIdentifier<Node> path() {
        return path;
    }
}