/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.spi;

import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.client.conf.NetconfClientConfigurationBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev221225.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;

/**
 * Factory for creating {@link NetconfClientConfigurationBuilder}s.
 */
public interface NetconfClientConfigurationBuilderFactory {
    /**
     * Create a new {@link NetconfClientConfigurationBuilder} initialized based on configuration {@link NetconfNode}.
     *
     * @param nodeId A topology node identifier
     * @param node A {@link NetconfNode}
     * @return An initialized {@link NetconfClientConfigurationBuilder}
     */
    @NonNull NetconfClientConfigurationBuilder createClientConfigurationBuilder(@NonNull NodeId nodeId,
        @NonNull NetconfNode node);
}
