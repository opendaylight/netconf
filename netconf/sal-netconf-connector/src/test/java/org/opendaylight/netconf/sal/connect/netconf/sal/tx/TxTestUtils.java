/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.connect.netconf.sal.tx;

import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;

final class TxTestUtils {

    private static final QName Q_NAME_1 = QName.create("test:namespace", "2013-07-22", "c");
    private static final QName Q_NAME_2 = QName.create(Q_NAME_1, "a");

    private TxTestUtils() {

    }

    static YangInstanceIdentifier getContainerId() {
        return YangInstanceIdentifier.builder()
                .node(Q_NAME_1)
                .build();
    }

    static YangInstanceIdentifier getLeafId() {
        return YangInstanceIdentifier.builder()
                .node(Q_NAME_1)
                .node(Q_NAME_2)
                .build();
    }

    static ContainerNode getContainerNode() {
        return Builders.containerBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(Q_NAME_1))
                .build();
    }

    static LeafNode<String> getLeafNode() {
        return Builders.<String>leafBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(Q_NAME_2))
                .withValue("data")
                .build();
    }

}
