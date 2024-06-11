/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import com.google.common.collect.ImmutableMap;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;

@NonNullByDefault
abstract class MapBodyOrderTest {
    static final QName FOO = QName.create("foo", "foo");
    static final QName BAR = QName.create("foo", "bar");
    static final QName BAZ = QName.create("foo", "baz");
    static final LeafNode<String> FOO_LEAF = ImmutableNodes.leafNode(FOO, "foo");
    static final LeafNode<String> BAR_LEAF = ImmutableNodes.leafNode(BAR, "bar");
    static final LeafNode<String> BAZ_LEAF = ImmutableNodes.leafNode(BAZ, "baz");
    static final NodeIdentifierWithPredicates BAR_BAZ_NID =
        // Note: ImmutableMap to ensure a fixed iteration order
        NodeIdentifierWithPredicates.of(FOO, ImmutableMap.of(BAR, "bar", BAZ, "baz"));
}
