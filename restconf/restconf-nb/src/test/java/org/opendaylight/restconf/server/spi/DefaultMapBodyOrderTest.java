/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import static org.junit.jupiter.api.Assertions.assertIterableEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;

class DefaultMapBodyOrderTest extends MapBodyOrderTest {
    @Test
    void needReorderTwoKeys() throws Exception {
        assertIterableEquals(List.of(BAR_LEAF, BAZ_LEAF),
            DefaultMapBodyOrder.INSTANCE.orderBody(ImmutableNodes.newMapEntryBuilder()
                .withNodeIdentifier(BAR_BAZ_NID)
                .withChild(BAZ_LEAF)
                .withChild(BAR_LEAF)
                .build()));
    }

    @Test
    void needReorderTwoKeysAndOther() throws Exception {
        assertIterableEquals(List.of(BAR_LEAF, BAZ_LEAF, FOO_LEAF),
            DefaultMapBodyOrder.INSTANCE.orderBody(ImmutableNodes.newMapEntryBuilder()
                .withNodeIdentifier(BAR_BAZ_NID)
                .withChild(FOO_LEAF)
                .withChild(BAZ_LEAF)
                .withChild(BAR_LEAF)
                .build()));
    }

    @Test
    void needReorderOneKeyAndOther() throws Exception {
        assertIterableEquals(List.of(BAR_LEAF, FOO_LEAF),
            DefaultMapBodyOrder.INSTANCE.orderBody(ImmutableNodes.newMapEntryBuilder()
                .withNodeIdentifier(NodeIdentifierWithPredicates.of(FOO, BAR, "bar"))
                .withChild(FOO_LEAF)
                .withChild(BAR_LEAF)
                .build()));
    }

    @Test
    void noReorderOneKey() throws Exception {
        assertIterableEquals(List.of(BAR_LEAF),
            DefaultMapBodyOrder.INSTANCE.orderBody(ImmutableNodes.newMapEntryBuilder()
                .withNodeIdentifier(NodeIdentifierWithPredicates.of(FOO, BAR, "bar"))
                .withChild(BAR_LEAF)
                .build()));
    }
}
