/*
 * Copyright (c) 2019 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.mdsal.connector.ops;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.ClassToInstanceMap;
import com.google.common.collect.ImmutableClassToInstanceMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import javax.xml.transform.dom.DOMSource;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.mdsal.connector.ops.DataTreeChangeTracker.DataTreeChange;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.EditConfigInput;
import org.opendaylight.yangtools.rfc7952.data.api.NormalizedMetadataStreamWriter;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.AugmentationIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeWithValue;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriterExtension;
import org.opendaylight.yangtools.yang.data.impl.schema.NormalizedNodeMetadataResult;

final class SplittingNormalizedNodeMetadataStreamWriter implements NormalizedNodeStreamWriter,
        NormalizedMetadataStreamWriter {
    private static final class SplitState {
        private final ComponentNormalizedNodeStreamWriter writer;
        private final ModifyAction action;
        private final int depth;

        SplitState(final ComponentNormalizedNodeStreamWriter writer, final ModifyAction action, final int depth) {
            this.writer = requireNonNull(writer);
            this.action = requireNonNull(action);
            this.depth = depth;
        }
    }

    private static final QName OPERATION_ATTRIBUTE = QName.create(EditConfigInput.QNAME.getNamespace(),
        XmlNetconfConstants.OPERATION_ATTR_KEY);

    private final List<DataTreeChange> dataTreeChanges = new ArrayList<>();
    private final Deque<PathArgument> currentPath = new ArrayDeque<>();
    private final Deque<SplitState> writers = new ArrayDeque<>();
    private final ModifyAction defaultAction;

    private ComponentNormalizedNodeStreamWriter currentWriter;
    private ModifyAction currentAction;
    private int currentDepth;

    SplittingNormalizedNodeMetadataStreamWriter(final ModifyAction defaultAction,
            final NormalizedNodeMetadataResult result) {
        this.defaultAction = requireNonNull(defaultAction);
        currentWriter = new ComponentNormalizedNodeStreamWriter(result);
        currentAction = defaultAction;
    }

    @Override
    public ClassToInstanceMap<NormalizedNodeStreamWriterExtension> getExtensions() {
        return ImmutableClassToInstanceMap.of(NormalizedMetadataStreamWriter.class, this);
    }

    @Override
    public void metadata(final ImmutableMap<QName, Object> metadata) throws IOException {
        final Object operation = metadata.get(OPERATION_ATTRIBUTE);
        final ImmutableMap<QName, Object> pushMeta;
        if (operation != null) {
            checkState(operation instanceof String, "Unexpected operation attribute value %s", operation);
            final ModifyAction newAction = ModifyAction.fromXmlValue((String) operation);

            pushMeta = ImmutableMap.copyOf(Maps.filterKeys(metadata, key -> !OPERATION_ATTRIBUTE.equals(key)));
            if (currentAction != newAction) {
                final ComponentNormalizedNodeStreamWriter newWriter = currentWriter.split();
                writers.push(new SplitState(currentWriter, currentAction, currentDepth));
                currentWriter = newWriter;
                currentAction = newAction;
                currentDepth = 1;
            }
        } else {
            pushMeta = metadata;
        }

        currentWriter.metadata(pushMeta);
    }

    @Override
    public void startLeafNode(final NodeIdentifier name) throws IOException {
        currentWriter.startLeafNode(name);
        pushPath(name);
    }

    @Override
    public void startLeafSet(final NodeIdentifier name, final int childSizeHint) throws IOException {
        currentWriter.startLeafSet(name, childSizeHint);
        pushPath(name);
    }

    @Override
    public void startOrderedLeafSet(final NodeIdentifier name, final int childSizeHint) throws IOException {
        currentWriter.startOrderedLeafSet(name, childSizeHint);
        pushPath(name);
    }

    @Override
    public void startLeafSetEntryNode(final NodeWithValue<?> name) throws IOException {
        currentWriter.startLeafSetEntryNode(name);
        pushPath(name);
    }

    @Override
    public void startContainerNode(final NodeIdentifier name, final int childSizeHint) throws IOException {
        currentWriter.startContainerNode(name, childSizeHint);
        pushPath(name);
    }

    @Override
    public void startUnkeyedList(final NodeIdentifier name, final int childSizeHint) throws IOException {
        currentWriter.startUnkeyedList(name, childSizeHint);
        pushPath(name);
    }

    @Override
    public void startUnkeyedListItem(final NodeIdentifier name, final int childSizeHint) throws IOException {
        currentWriter.startUnkeyedListItem(name, childSizeHint);
        pushPath(name);
    }

    @Override
    public void startMapNode(final NodeIdentifier name, final int childSizeHint) throws IOException {
        currentWriter.startMapNode(name, childSizeHint);
        pushPath(name);
    }

    @Override
    public void startMapEntryNode(final NodeIdentifierWithPredicates identifier, final int childSizeHint)
            throws IOException {
        currentWriter.startMapEntryNode(identifier, childSizeHint);
        pushPath(identifier);
    }

    @Override
    public void startOrderedMapNode(final NodeIdentifier name, final int childSizeHint) throws IOException {
        currentWriter.startOrderedMapNode(name, childSizeHint);
        pushPath(name);
    }

    @Override
    public void startChoiceNode(final NodeIdentifier name, final int childSizeHint) throws IOException {
        currentWriter.startChoiceNode(name, childSizeHint);
        pushPath(name);
    }

    @Override
    public void startAugmentationNode(final AugmentationIdentifier identifier) throws IOException {
        currentWriter.startAugmentationNode(identifier);
        pushPath(identifier);
    }

    @Override
    public void startAnyxmlNode(final NodeIdentifier name) throws IOException {
        currentWriter.startAnyxmlNode(name);
        pushPath(name);
    }

    @Override
    public void startYangModeledAnyXmlNode(final NodeIdentifier name, final int childSizeHint) throws IOException {
        currentWriter.startYangModeledAnyXmlNode(name, childSizeHint);
        pushPath(name);
    }

    @Override
    public void endNode() throws IOException {
        popPath();
        currentWriter.endNode();

        if (currentDepth == 0) {
            final SplitState prev = writers.peek();
            if (prev == null) {
                // All done
                return;
            }

            // FIXME: acquire the result, add in to transaction
            dataTreeChanges.add(new DataTreeChange(currentWriter.build(), currentAction(), currentPath));

            currentWriter = prev.writer;
            currentAction = prev.action;
            currentDepth = prev.depth;
        }
    }

    @Override
    public void domSourceValue(final DOMSource value) throws IOException {
        currentWriter.domSourceValue(value);
    }

    @Override
    public void scalarValue(@NonNull final Object value) throws IOException {
        currentWriter.scalarValue(value);
    }

    @Override
    public void close() throws IOException {
        // TODO Auto-generated method stub
    }

    @Override
    public void flush() throws IOException {
        currentWriter.flush();
    }

    private ModifyAction currentAction() {
        return currentAction == null ? defaultAction : currentAction;
    }

    private PathArgument popPath() {
        currentDepth--;
        return currentPath.pop();
    }

    private void pushPath(final PathArgument pathArgument) {
        currentPath.push(pathArgument);
        currentDepth++;
    }
}
