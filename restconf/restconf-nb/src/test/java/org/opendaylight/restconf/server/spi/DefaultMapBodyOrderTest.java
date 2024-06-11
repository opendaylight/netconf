/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;

import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.yangtools.concepts.PrettyTree;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;

@ExtendWith(MockitoExtension.class)
class DefaultMapBodyOrderTest {
    private static final QName FOO = QName.create("foo", "foo");
    private static final QName BAR = QName.create("foo", "bar");
    private static final QName BAZ = QName.create("foo", "baz");
    private static final LeafNode<String> FOO_LEAF = ImmutableNodes.leafNode(FOO, "foo");
    private static final LeafNode<String> BAR_LEAF = ImmutableNodes.leafNode(BAR, "bar");
    private static final LeafNode<String> BAZ_LEAF = ImmutableNodes.leafNode(BAZ, "baz");
    private static final NodeIdentifierWithPredicates BAR_BAZ_NID =
        // Note: ImmutableMap to ensure a fixed iteration order
        NodeIdentifierWithPredicates.of(FOO, ImmutableMap.of(BAR, "bar", BAZ, "baz"));

    @Mock(answer = Answers.CALLS_REAL_METHODS)
    private MapEntryNode entry;

    @Test
    void needReorderTwoKeys() throws Exception {
        doReturn(BAR_BAZ_NID).when(entry).name();
        doReturn(List.of(BAZ_LEAF, BAR_LEAF)).when(entry).body();
        doReturn(BAR_LEAF).when(entry).childByArg(BAR_LEAF.name());
        doReturn(BAZ_LEAF).when(entry).childByArg(BAZ_LEAF.name());

        assertIterableEquals(List.of(BAR_LEAF, BAZ_LEAF), DefaultMapBodyOrder.INSTANCE.orderBody(entry));
    }

    @Test
    void needReorderTwoKeysAndOther() throws Exception {
        doReturn(BAR_BAZ_NID).when(entry).name();
        doReturn(List.of(FOO_LEAF, BAZ_LEAF, BAR_LEAF)).when(entry).body();

        assertIterableEquals(List.of(BAR_LEAF, BAZ_LEAF, FOO_LEAF), DefaultMapBodyOrder.INSTANCE.orderBody(entry));
    }

    @Test
    void needReorderOneKeyAndOther() throws Exception {
        doReturn(NodeIdentifierWithPredicates.of(FOO, BAR, "bar")).when(entry).name();
        doReturn(List.of(FOO_LEAF, BAR_LEAF)).when(entry).body();

        assertIterableEquals(List.of(BAR_LEAF, FOO_LEAF), DefaultMapBodyOrder.INSTANCE.orderBody(entry));
    }

    @Test
    void noReorderOneKey() throws Exception {
        doReturn(NodeIdentifierWithPredicates.of(FOO, BAR, "bar")).when(entry).name();
        doReturn(List.of(BAR_LEAF)).when(entry).body();
        doReturn(BAR_LEAF).when(entry).childByArg(BAR_LEAF.name());

        assertIterableEquals(List.of(BAR_LEAF), DefaultMapBodyOrder.INSTANCE.orderBody(entry));
    }

    @Test
    void mismatchedSizeThrows() {
        doReturn(BAR_BAZ_NID).when(entry).name();
        doReturn(1).when(entry).size();

        final var ex = assertThrows(IOException.class, () -> DefaultMapBodyOrder.INSTANCE.orderBody(entry));
        assertEquals("(foo)foo[{(foo)bar=bar, (foo)baz=baz}] requires 2 items, have only 1", ex.getMessage());
    }

    @Test
    void noKeysWithMoreOthersThrows() {
        doReturn(BAR_BAZ_NID).when(entry).name();
        doReturn(3).when(entry).size();
        doReturn(new PrettyTree() {
            @Override
            public void appendTo(final StringBuilder sb, final int depth) {
                sb.append("prettyTree");
            }
        }).when(entry).prettyTree();

        final var ex = assertThrows(IOException.class, () -> DefaultMapBodyOrder.INSTANCE.orderBody(entry));
        assertEquals("Missing leaf nodes for [(foo)bar, (foo)baz] in prettyTree", ex.getMessage());
    }

    @Test
    void noKeysWithOthersThrows() {
        doReturn(BAR_BAZ_NID).when(entry).name();
        doReturn(2).when(entry).size();
        doReturn(new PrettyTree() {
            @Override
            public void appendTo(final StringBuilder sb, final int depth) {
                sb.append("prettyTree");
            }
        }).when(entry).prettyTree();

        final var ex = assertThrows(IOException.class, () -> DefaultMapBodyOrder.INSTANCE.orderBody(entry));
        assertEquals("No leaf for (foo)bar in prettyTree", ex.getMessage());
    }

    @Test
    void nonKeyChildThrows() {
        doReturn(BAR_BAZ_NID).when(entry).name();
        final var barId = new NodeIdentifier(BAR);
        doReturn(2).when(entry).size();

        doReturn(ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(barId)
            .build()).when(entry).childByArg(barId);

        final var ex = assertThrows(IOException.class, () -> DefaultMapBodyOrder.INSTANCE.orderBody(entry));
        assertEquals("Child containerNode (foo)bar = {} is not a leaf", ex.getMessage());
    }

    @Test
    void iterationDoesNotReorderEvenIfNeeded() {
        final var body = List.of(FOO_LEAF);
        doReturn(body).when(entry).body();

        assertSame(body, IterationMapBodyOrder.INSTANCE.orderBody(entry));
    }
}
