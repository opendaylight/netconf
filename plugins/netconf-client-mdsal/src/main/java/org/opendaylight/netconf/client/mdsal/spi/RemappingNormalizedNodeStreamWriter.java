/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.spi;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.util.function.Function;
import javax.xml.transform.dom.DOMSource;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeWithValue;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;

final class RemappingNormalizedNodeStreamWriter implements NormalizedNodeStreamWriter {
    private final NormalizedNodeStreamWriter delegate;
    private final Function<QName, QName> mapper;

    RemappingNormalizedNodeStreamWriter(final NormalizedNodeStreamWriter delegate,
            final Function<QName, QName> mapper) {
        this.delegate = requireNonNull(delegate);
        this.mapper = requireNonNull(mapper);
    }

    @Override
    public void startContainerNode(final NodeIdentifier name, final int childCount) throws IOException {
        delegate.startContainerNode(mapId(name), childCount);
    }

    @Override
    public void startChoiceNode(final NodeIdentifier name, final int childCount) throws IOException {
        delegate.startChoiceNode(mapId(name), childCount);
    }

    @Override
    public boolean startAnydataNode(final NodeIdentifier name, final Class<?> objectModel) throws IOException {
        return delegate.startAnydataNode(mapId(name), objectModel);
    }

    @Override
    public void startMapNode(final NodeIdentifier name, final int childCount) throws IOException {
        delegate.startMapNode(mapId(name), childCount);
    }

    @Override
    public void startOrderedMapNode(final NodeIdentifier name, final int childCount) throws IOException {
        delegate.startOrderedMapNode(mapId(name), childCount);
    }

    @Override
    public void startMapEntryNode(final NodeIdentifierWithPredicates name, final int childCount) throws IOException {
        delegate.startMapEntryNode(mapPredicates(name), childCount);
    }

    @Override
    public void startUnkeyedList(final NodeIdentifier name, final int childCount) throws IOException {
        delegate.startUnkeyedList(mapId(name), childCount);
    }

    @Override
    public void startUnkeyedListItem(final NodeIdentifier name, final int childCount) throws IOException {
        delegate.startUnkeyedListItem(mapId(name), childCount);
    }

    @Override
    public void startLeafSet(final NodeIdentifier name, final int childCount) throws IOException {
        delegate.startLeafSet(mapId(name), childCount);
    }

    @Override
    public void startOrderedLeafSet(final NodeIdentifier name, final int childCount) throws IOException {
        delegate.startOrderedLeafSet(mapId(name), childCount);
    }

    @Override
    public void startLeafSetEntryNode(final YangInstanceIdentifier.NodeWithValue<?> name) throws IOException {
        delegate.startLeafSetEntryNode(mapNodeWithValue(name));
    }

    @Override
    public void startLeafNode(final NodeIdentifier name) throws IOException {
        delegate.startLeafNode(mapId(name));
    }

    @Override
    public void scalarValue(final Object value) throws IOException {
        delegate.scalarValue(mapScalarValue(value, mapper));
    }

    @Override
    public boolean startAnyxmlNode(final NodeIdentifier name, final Class<?> objectModel) throws IOException {
        return delegate.startAnyxmlNode(mapId(name), objectModel);
    }

    @Override
    public void domSourceValue(final DOMSource value) throws IOException {
        delegate.domSourceValue(value);
    }

    @Override
    public void endNode() throws IOException {
        delegate.endNode();
    }

    @Override
    public void nextDataSchemaNode(final DataSchemaNode schema) {
        delegate.nextDataSchemaNode(schema);
    }

    @Override
    public void flush() throws IOException {
        delegate.flush();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    private NodeIdentifier mapId(final NodeIdentifier id) {
        final var mapped = mapper.apply(id.getNodeType());
        return mapped.equals(id.getNodeType()) ? id : NodeIdentifier.create(mapped);
    }

    private NodeIdentifierWithPredicates mapPredicates(final NodeIdentifierWithPredicates id) {
        final var mappedType = mapper.apply(id.getNodeType());
        final var kb = ImmutableMap.<QName, Object>builder();
        id.asMap().forEach((k, v) -> kb.put(mapper.apply(k), mapScalarValue(v, mapper)));
        return NodeIdentifierWithPredicates.of(mappedType, kb.build());
    }

    private NodeWithValue<?> mapNodeWithValue(final NodeWithValue<?> nodeWithValue) {
        final var mappedQ = mapper.apply(nodeWithValue.getNodeType());
        final var mappedV = mapScalarValue(nodeWithValue.getValue(), mapper);
        if (mappedQ.equals(nodeWithValue.getNodeType()) && mappedV == nodeWithValue.getValue()) {
            return nodeWithValue;
        }
        return new NodeWithValue<>(mappedQ, mappedV);
    }

    private static Object mapScalarValue(final Object value, final Function<QName, QName> mapper) {
        if (value instanceof QName qName) {
            return mapper.apply(qName);
        }
        if (value instanceof YangInstanceIdentifier instanceIdentifier) {
            return transformYII(instanceIdentifier, mapper);
        }
        return value;
    }

    private static YangInstanceIdentifier transformYII(final YangInstanceIdentifier yangInstanceIdentifier,
        final Function<QName, QName> mapper) {
        final var builder = YangInstanceIdentifier.builder();
        for (var arg : yangInstanceIdentifier.getPathArguments()) {
            switch (arg) {
                case NodeIdentifierWithPredicates nip -> {
                    final var mappedType = mapper.apply(nip.getNodeType());
                    final var kb = ImmutableMap.<QName, Object>builder();
                    nip.asMap().forEach((k, v) -> kb.put(mapper.apply(k), mapScalarValue(v, mapper)));
                    builder.nodeWithKey(mappedType, kb.build());
                }
                case NodeIdentifier nodeIdentifier -> builder.node(mapper.apply(nodeIdentifier.getNodeType()));
                case YangInstanceIdentifier.NodeWithValue<?> nv -> {
                    final var mappedQ = mapper.apply(nv.getNodeType());
                    final var mappedV = nv.getValue() instanceof QName q ? mapper.apply(q) : nv.getValue();
                    builder.node(new YangInstanceIdentifier.NodeWithValue<>(mappedQ, mappedV));
                }
                case null, default -> builder.node(arg);
            }
        }
        return builder.build();
    }
}