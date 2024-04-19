/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import static java.util.Objects.requireNonNull;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.api.query.DepthParam;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.schema.AnydataNode;
import org.opendaylight.yangtools.yang.data.api.schema.AnyxmlNode;
import org.opendaylight.yangtools.yang.data.api.schema.ChoiceNode;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.UnkeyedListEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.UnkeyedListNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;

/**
 * This is an experimental iterator over a {@link NormalizedNode}. This is essentially
 * the opposite of a {@link javax.xml.stream.XMLStreamReader} -- unlike instantiating an iterator over
 * the backing data, this encapsulates a {@link NormalizedNodeStreamWriter} and allows
 * us to write multiple nodes.
 */
@NonNullByDefault
public abstract class NormalizedNodeWriter implements Flushable, Closeable {
    protected final NormalizedNodeStreamWriter writer;

    NormalizedNodeWriter(final NormalizedNodeStreamWriter writer) {
        this.writer = requireNonNull(writer);
    }

    /**
     * Create a new writer backed by a {@link NormalizedNodeStreamWriter}.
     *
     * @param writer Back-end writer
     * @param maxDepth Maximal depth to write
     * @return A new instance.
     */
    public static final NormalizedNodeWriter forStreamWriter(final NormalizedNodeStreamWriter writer,
            final @Nullable DepthParam maxDepth) {
        return forStreamWriter(writer, true,  maxDepth, null);
    }

    /**
     * Create a new writer backed by a {@link NormalizedNodeStreamWriter}.
     *
     * @param writer Back-end writer
     * @param maxDepth Maximal depth to write
     * @param fields Selected child nodes to write
     * @return A new instance.
     */
    public static final NormalizedNodeWriter forStreamWriter(final NormalizedNodeStreamWriter writer,
            final @Nullable DepthParam maxDepth, final @Nullable List<Set<QName>> fields) {
        return forStreamWriter(writer, true,  maxDepth, fields);
    }

    /**
     * Create a new writer backed by a {@link NormalizedNodeStreamWriter}. Unlike the simple
     * {@link #forStreamWriter(NormalizedNodeStreamWriter, DepthParam, List)} method, this allows the caller to
     * switch off RFC6020 XML compliance, providing better throughput. The reason is that the XML mapping rules in
     * RFC6020 require the encoding to emit leaf nodes which participate in a list's key first and in the order in which
     * they are defined in the key. For JSON, this requirement is completely relaxed and leaves can be ordered in any
     * way we see fit. The former requires a bit of work: first a lookup for each key and then for each emitted node we
     * need to check whether it was already emitted.
     *
     * @param writer Back-end writer
     * @param orderKeyLeaves whether the returned instance should be RFC6020 XML compliant.
     * @param depth Maximal depth to write
     * @param fields Selected child nodes to write
     * @return A new instance.
     */
    public static final NormalizedNodeWriter forStreamWriter(final NormalizedNodeStreamWriter writer,
            final boolean orderKeyLeaves, final @Nullable DepthParam depth, final @Nullable List<Set<QName>> fields) {
        return new DefaultNormalizedNodeWriter(writer, !orderKeyLeaves, depth, fields);
    }

    @Override
    public final void flush() throws IOException {
        writer.flush();
    }

    @Override
    public final void close() throws IOException {
        writer.flush();
        writer.close();
    }

    /**
     * Iterate over the provided {@link NormalizedNode} and emit write events to the encapsulated
     * {@link NormalizedNodeStreamWriter}.
     *
     * @param node Node
     * @return {@code ParameterAwareNormalizedNodeWriter}
     * @throws IOException when thrown from the backing writer.
     */
    public final NormalizedNodeWriter write(final NormalizedNode node) throws IOException {
        if (node instanceof ContainerNode n) {
            writeContainer(n);
        } else if (node instanceof MapNode n) {
            writeMap(n);
        } else if (node instanceof MapEntryNode n) {
            writeMapEntry(n);
        } else if (node instanceof LeafNode<?> n) {
            writeLeaf(n);
        } else if (node instanceof ChoiceNode n) {
            writeChoice(n);
        } else if (node instanceof UnkeyedListNode n) {
            writeUnkeyedList(n);
        } else if (node instanceof UnkeyedListEntryNode n) {
            writeUnkeyedListEntry(n);
        } else if (node instanceof LeafSetNode<?> n) {
            writeLeafSet(n);
        } else if (node instanceof LeafSetEntryNode<?> n) {
            writeLeafSetEntry(n);
        } else if (node instanceof AnydataNode<?> n) {
            writeAnydata(n);
        } else if (node instanceof AnyxmlNode<?> n) {
            writeAnyxml(n);
        } else {
            throw new IOException("Unhandled contract " + node.contract().getSimpleName());
        }
        return this;
    }

    protected abstract void writeAnydata(AnydataNode<?> node) throws IOException;

    protected abstract void writeAnyxml(AnyxmlNode<?> node) throws IOException;

    protected abstract void writeChoice(ChoiceNode node) throws IOException;

    protected abstract void writeContainer(ContainerNode node) throws IOException;

    protected abstract void writeLeaf(LeafNode<?> node) throws IOException;

    protected abstract void writeLeafSet(LeafSetNode<?> node) throws IOException;

    protected abstract void writeLeafSetEntry(LeafSetEntryNode<?> node) throws IOException;

    protected abstract void writeMap(MapNode node) throws IOException;

    protected abstract void writeMapEntry(MapEntryNode node) throws IOException;

    protected abstract void writeUnkeyedList(UnkeyedListNode node) throws IOException;

    protected abstract void writeUnkeyedListEntry(UnkeyedListEntryNode node) throws IOException;
}
