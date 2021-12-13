/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.util;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import javax.xml.stream.XMLStreamWriter;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yangtools.rfc7952.data.api.NormalizedMetadata;
import org.opendaylight.yangtools.rfc7952.data.api.StreamWriterMetadataExtension;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.AugmentationIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.stream.ForwardingNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;

/**
 * This class has the same purpose as {@link EmptyListXmlWriter} and in addition writes metadata.
 */
final class EmptyListXmlMetadataWriter extends ForwardingNormalizedNodeStreamWriter {
    private final Deque<NormalizedMetadata> stack = new ArrayDeque<>();
    private final EmptyListXmlWriter delegate;
    private final StreamWriterMetadataExtension metaWriter;
    private final NormalizedMetadata metadata;

    private int absentDepth = 0;

    EmptyListXmlMetadataWriter(final @NonNull NormalizedNodeStreamWriter writer,
            final @NonNull XMLStreamWriter xmlStreamWriter, final @NonNull StreamWriterMetadataExtension metaWriter,
            final @NonNull NormalizedMetadata metadata) {
        this.delegate = new EmptyListXmlWriter(requireNonNull(writer), requireNonNull(xmlStreamWriter));
        this.metaWriter = requireNonNull(metaWriter);
        this.metadata = requireNonNull(metadata);
    }

    @Override
    protected NormalizedNodeStreamWriter delegate() {
        return delegate.delegate();
    }

    @Override
    public void startLeafNode(final NodeIdentifier name) throws IOException {
        delegate.startLeafNode(name);
        enterMetadataNode(name);
    }

    @Override
    public void startOrderedLeafSet(final NodeIdentifier name, final int childSizeHint) throws IOException {
        delegate.startOrderedLeafSet(name, childSizeHint);
        enterMetadataNode(name);
    }

    @Override
    public void startUnkeyedList(final NodeIdentifier name, final int childSizeHint) throws IOException {
        delegate.startUnkeyedList(name, childSizeHint);
        enterMetadataNode(name);
    }

    @Override
    public void startLeafSet(final NodeIdentifier name, final int childSizeHint) throws IOException {
        delegate.startLeafSet(name, childSizeHint);
        enterMetadataNode(name);
    }

    @Override
    public void startLeafSetEntryNode(final YangInstanceIdentifier.NodeWithValue<?> name) throws IOException {
        delegate.startLeafSetEntryNode(name);
        enterMetadataNode(name);
    }

    @Override
    public void startContainerNode(final NodeIdentifier name, final int childSizeHint) throws IOException {
        delegate.startContainerNode(name, childSizeHint);
        enterMetadataNode(name);
    }

    @Override
    public void startUnkeyedListItem(final NodeIdentifier name, final int childSizeHint) throws IOException {
        delegate.startUnkeyedListItem(name, childSizeHint);
        enterMetadataNode(name);
    }

    @Override
    public void startMapNode(final NodeIdentifier name, final int childSizeHint) throws IOException {
        delegate.startMapNode(name, childSizeHint);
        enterMetadataNode(name);
    }

    @Override
    public void startMapEntryNode(final NodeIdentifierWithPredicates identifier, final int childSizeHint)
            throws IOException {
        delegate.startMapEntryNode(identifier, childSizeHint);
        enterMetadataNode(identifier);
    }

    @Override
    public void startOrderedMapNode(final NodeIdentifier name, final int childSizeHint) throws IOException {
        delegate.startOrderedMapNode(name, childSizeHint);
        enterMetadataNode(name);
    }

    @Override
    public void startChoiceNode(final NodeIdentifier name, final int childSizeHint) throws IOException {
        delegate.startChoiceNode(name, childSizeHint);
        enterMetadataNode(name);
    }

    @Override
    public void startAugmentationNode(final AugmentationIdentifier identifier) throws IOException {
        delegate.startAugmentationNode(identifier);
        enterMetadataNode(identifier);
    }

    @Override
    public boolean startAnyxmlNode(final NodeIdentifier name, final Class<?> objectModel) throws IOException {
        return delegate.startAnyxmlNode(name, objectModel);
    }

    @Override
    public void endNode() throws IOException {
        delegate.endNode();
    }

    private void enterMetadataNode(final YangInstanceIdentifier.PathArgument name) throws IOException {
        if (absentDepth > 0) {
            absentDepth++;
            return;
        }

        final NormalizedMetadata current = stack.peek();
        if (current != null) {
            final NormalizedMetadata child = current.getChildren().get(name);
            if (child != null) {
                enterChild(child);
            } else {
                absentDepth = 1;
            }
        } else {
            // Empty stack: enter first entry
            enterChild(metadata);
        }
    }

    private void enterChild(final NormalizedMetadata child) throws IOException {
        final Map<QName, Object> annotations = child.getAnnotations();
        if (!annotations.isEmpty()) {
            metaWriter.metadata(ImmutableMap.copyOf(annotations));
        }
        stack.push(child);
    }
}