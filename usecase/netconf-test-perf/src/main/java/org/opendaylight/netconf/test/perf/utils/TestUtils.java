/*
 * Copyright (c) 2021 Pantheon Technologies, s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.test.perf.utils;

import java.util.Optional;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;

public final class TestUtils {

    private static final QName NODE_QNAME = QName.create(Node.QNAME, "node-id").intern();

    private TestUtils() {
    }

    public static Optional<String> getNodeId(final YangInstanceIdentifier path) {
        if (path.getLastPathArgument() instanceof NodeIdentifierWithPredicates) {
            final NodeIdentifierWithPredicates nodeIId = ((NodeIdentifierWithPredicates) path.getLastPathArgument());
            return Optional.ofNullable(nodeIId.getValue(NODE_QNAME, String.class));
        } else {
            return Optional.empty();
        }
    }
}
