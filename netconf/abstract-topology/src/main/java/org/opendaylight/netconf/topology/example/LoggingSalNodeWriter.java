/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.example;

import java.util.ArrayList;
import java.util.Arrays;
import javax.annotation.Nonnull;
import org.opendaylight.netconf.topology.util.NodeWriter;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingSalNodeWriter implements NodeWriter{

    private static final Logger LOG = LoggerFactory.getLogger(LoggingSalNodeWriter.class);

    private final ArrayList<NodeWriter> delegates;

    public LoggingSalNodeWriter(final NodeWriter... delegates) {
        this.delegates = new ArrayList<>(Arrays.asList(delegates));
    }

    @Override
    public void init(@Nonnull NodeId id, @Nonnull Node operationalDataNode) {
        LOG.warn("Init received");
        LOG.warn("NodeId: {}", id.getValue());
        LOG.warn("Node: {}", operationalDataNode);
        for (final NodeWriter delegate : delegates) {
            delegate.init(id, operationalDataNode);
        }
    }

    @Override
    public void update(@Nonnull NodeId id, @Nonnull Node operationalDataNode) {
        LOG.warn("Update received");
        LOG.warn("NodeId: {}", id.getValue());
        LOG.warn("Node: {}", operationalDataNode);
        for (final NodeWriter delegate : delegates) {
            delegate.update(id, operationalDataNode);
        }
    }

    @Override
    public void delete(@Nonnull NodeId id) {
        LOG.warn("Delete received");
        LOG.warn("NodeId: {}", id.getValue());
        for (final NodeWriter delegate : delegates) {
            delegate.delete(id);
        }
    }
}
