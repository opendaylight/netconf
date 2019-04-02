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
import org.opendaylight.netconf.api.ModifyAction;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
            checkState(depth > 0);
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(SplittingNormalizedNodeMetadataStreamWriter.class);
    private static final QName OPERATION_ATTRIBUTE = QName.create(EditConfigInput.QNAME.getNamespace(),
        XmlNetconfConstants.OPERATION_ATTR_KEY);

    // Top-level result node
    private final NormalizedNodeMetadataResult result = new NormalizedNodeMetadataResult();
    // Split-out changes
    private final List<DataTreeChange> dataTreeChanges = new ArrayList<>();
    // Path of the node we are currently in
    private final Deque<PathArgument> currentPath = new ArrayDeque<>();
    // Stack of parent changes.
    private final Deque<ModifyAction> actions = new ArrayDeque<>();
    // Stack of stashed writers which have been split out
    private final Deque<SplitState> writers = new ArrayDeque<>();
    private final ModifyAction defaultAction;

    // Current backing writer
    private ComponentNormalizedNodeStreamWriter currentWriter;
    // Current action, populated to default action on entry
    private ModifyAction currentAction;
    // Tracks the depth of current writer (which is distinct from currentPath.size())
    private int currentDepth;

    // Tracks the number of delete operations in actions
    private int deleteDepth;

    SplittingNormalizedNodeMetadataStreamWriter(final ModifyAction defaultAction) {
        this.defaultAction = requireNonNull(defaultAction);
        currentWriter = new ComponentNormalizedNodeStreamWriter(result);
    }

    List<DataTreeChange> getDataTreeChanges() {
        return dataTreeChanges;
    }

    @Override
    public ClassToInstanceMap<NormalizedNodeStreamWriterExtension> getExtensions() {
        return ImmutableClassToInstanceMap.of(NormalizedMetadataStreamWriter.class, this);
    }

    @Override
    public void metadata(final ImmutableMap<QName, Object> metadata) throws IOException {
        checkState(currentDepth > 0);
        final Object operation = metadata.get(OPERATION_ATTRIBUTE);
        final ImmutableMap<QName, Object> pushMeta;
        if (operation != null) {
            checkState(operation instanceof String, "Unexpected operation attribute value %s", operation);
            final ModifyAction newAction = ModifyAction.fromXmlValue((String) operation);

            pushMeta = ImmutableMap.copyOf(Maps.filterKeys(metadata, key -> !OPERATION_ATTRIBUTE.equals(key)));
            final ModifyAction prevAction = actions.peek();
            if (prevAction != null) {
                // We only split out a builder if we a changing action relative to parent and we are not inside
                // a remove/delete operation
                if (newAction != prevAction && deleteDepth == 0) {
                    final ComponentNormalizedNodeStreamWriter newWriter = currentWriter.split();
                    LOG.debug("Peel writer {} from {} at depth {}", newWriter, currentWriter, currentDepth);
                    writers.push(new SplitState(currentWriter, prevAction, currentDepth - 1));
                    currentWriter = newWriter;
                    currentAction = newAction;
                    currentDepth = 1;
                }
            } else {
                // We do not want to split out the top-level builder
                currentAction = newAction;
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
        checkState(currentDepth > 0);
        if (currentDepth == 1) {
            final SplitState prev = writers.peek();
            LOG.debug("Writer {} end node prev {}", currentWriter, prev);

            final DataTreeChange change;
            if (prev != null) {
                change = new DataTreeChange(currentWriter.build(), currentAction, currentPath);
                popPath();
                currentWriter = prev.writer;
                currentAction = prev.action;
                currentDepth = prev.depth;
                writers.pop();
                LOG.debug("Reinstate writer {} depth {}", currentWriter, currentDepth);
            } else {
                // All done, special-cased
                LOG.debug("All done ... writer {}", currentWriter);
                currentWriter.endNode();
                change = new DataTreeChange(result.getResult(), currentAction, currentPath);
            }

            dataTreeChanges.add(change);
        } else {
            LOG.trace("Writer {} end node", currentWriter);
            currentWriter.endNode();
            popPath();
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
        checkState(writers.isEmpty(), "Cannot close with %s", writers);
        checkState(currentDepth == 0, "Cannot close with depth %s", currentDepth);
        currentWriter.close();
    }

    @Override
    public void flush() throws IOException {
        currentWriter.flush();
    }

    private boolean atRemoval() {
        return currentAction == ModifyAction.DELETE || currentAction == ModifyAction.REMOVE;
    }

    private void popPath() {
        checkState(currentDepth > 0);
        currentDepth--;
        currentPath.pop();
        currentAction = actions.pop();
        if (atRemoval()) {
            checkState(deleteDepth > 0);
            deleteDepth--;
        }
    }

    private void pushPath(final PathArgument pathArgument) {
        checkState(currentDepth >= 0);
        if (currentDepth != 0) {
            if (atRemoval()) {
                deleteDepth++;
            }
            actions.push(currentAction);
        }
        currentPath.push(pathArgument);
        currentAction = defaultAction;
        currentDepth++;
    }
}
