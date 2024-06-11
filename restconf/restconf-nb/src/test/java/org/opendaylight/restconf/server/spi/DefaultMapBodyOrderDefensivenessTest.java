/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;

import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.yangtools.concepts.PrettyTree;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;

// Note: tests for conditions requiring a MapEntryNode which cannot be produced via ImmutableNodes' methods.
@ExtendWith(MockitoExtension.class)
class DefaultMapBodyOrderDefensivenessTest extends MapBodyOrderTest {
    @Mock
    private MapEntryNode entry;

    @BeforeEach
    void beforeEach() {
        doReturn(BAR_BAZ_NID).when(entry).name();
    }

    @Test
    void mismatchedSizeThrows() {
        doReturn(1).when(entry).size();

        final var ex = assertThrows(IOException.class, () -> DefaultMapBodyOrder.INSTANCE.orderBody(entry));
        assertEquals("(foo)foo[{(foo)bar=bar, (foo)baz=baz}] requires 2 items, have only 1", ex.getMessage());
    }

    @Test
    void noKeysWithMoreOthersThrows() {
        doReturn(3).when(entry).size();

        final var ex = assertThrows(IOException.class, () -> DefaultMapBodyOrder.INSTANCE.orderBody(entry));
        assertEquals("Missing leaf nodes for [(foo)bar, (foo)baz] in entry", ex.getMessage());
    }

    @Test
    void noKeysWithOthersThrows() {
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
        final var barId = new NodeIdentifier(BAR);
        doReturn(2).when(entry).size();

        doReturn(ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(barId)
            .build()).when(entry).childByArg(barId);

        final var ex = assertThrows(IOException.class, () -> DefaultMapBodyOrder.INSTANCE.orderBody(entry));
        assertEquals("Child containerNode (foo)bar = {} is not a leaf", ex.getMessage());
    }
}
