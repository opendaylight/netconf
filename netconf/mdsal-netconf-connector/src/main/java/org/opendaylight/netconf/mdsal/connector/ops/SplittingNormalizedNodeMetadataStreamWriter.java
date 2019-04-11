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
import org.opendaylight.yangtools.yang.data.codec.xml.XmlParserStream;
import org.opendaylight.yangtools.yang.data.impl.schema.NormalizedNodeMetadataResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class SplittingNormalizedNodeMetadataStreamWriter implements NormalizedNodeStreamWriter,
        NormalizedMetadataStreamWriter {
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
    private final ModifyAction defaultAction;
    // Backing writer
    private final ComponentNormalizedNodeStreamWriter writer;

    // Current action, populated to default action on entry
    private ModifyAction currentAction;

    // Tracks the number of delete operations in actions
    private int deleteDepth;

    SplittingNormalizedNodeMetadataStreamWriter(final ModifyAction defaultAction) {
        this.defaultAction = requireNonNull(defaultAction);
        writer = new ComponentNormalizedNodeStreamWriter(result);
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
        final Object operation = metadata.get(OPERATION_ATTRIBUTE);
        if (operation != null) {
            checkState(operation instanceof String, "Unexpected operation attribute value %s", operation);
            final ModifyAction newAction = ModifyAction.fromXmlValue((String) operation);
            currentAction = newAction;
        }

        writer.metadata(filterMeta(metadata));
    }

    private static ImmutableMap<QName, Object> filterMeta(ImmutableMap<QName, Object> metadata) {
        // FIXME: also remove prefixed attributes?
        return ImmutableMap.copyOf(Maps.filterKeys(metadata,
            key -> !XmlParserStream.LEGACY_ATTRIBUTE_NAMESPACE.equals(key.getModule())));
    }

    @Override
    public void startLeafNode(final NodeIdentifier name) throws IOException {
        writer.startLeafNode(name);
        pushPath(name);
    }

    @Override
    public void startLeafSet(final NodeIdentifier name, final int childSizeHint) throws IOException {
        writer.startLeafSet(name, childSizeHint);
        pushPath(name);
    }

    @Override
    public void startOrderedLeafSet(final NodeIdentifier name, final int childSizeHint) throws IOException {
        writer.startOrderedLeafSet(name, childSizeHint);
        pushPath(name);
    }

    @Override
    public void startLeafSetEntryNode(final NodeWithValue<?> name) throws IOException {
        writer.startLeafSetEntryNode(name);
        pushPath(name);
    }

    @Override
    public void startContainerNode(final NodeIdentifier name, final int childSizeHint) throws IOException {
        writer.startContainerNode(name, childSizeHint);
        pushPath(name);
    }

    @Override
    public void startUnkeyedList(final NodeIdentifier name, final int childSizeHint) throws IOException {
        writer.startUnkeyedList(name, childSizeHint);
        pushPath(name);
    }

    @Override
    public void startUnkeyedListItem(final NodeIdentifier name, final int childSizeHint) throws IOException {
        writer.startUnkeyedListItem(name, childSizeHint);
        pushPath(name);
    }

    @Override
    public void startMapNode(final NodeIdentifier name, final int childSizeHint) throws IOException {
        writer.startMapNode(name, childSizeHint);
        pushPath(name);
    }

    @Override
    public void startMapEntryNode(final NodeIdentifierWithPredicates identifier, final int childSizeHint)
            throws IOException {
        writer.startMapEntryNode(identifier, childSizeHint);
        pushPath(identifier);
    }

    @Override
    public void startOrderedMapNode(final NodeIdentifier name, final int childSizeHint) throws IOException {
        writer.startOrderedMapNode(name, childSizeHint);
        pushPath(name);
    }

    @Override
    public void startChoiceNode(final NodeIdentifier name, final int childSizeHint) throws IOException {
        writer.startChoiceNode(name, childSizeHint);
        pushPath(name);
    }

    @Override
    public void startAugmentationNode(final AugmentationIdentifier identifier) throws IOException {
        writer.startAugmentationNode(identifier);
        pushPath(identifier);
    }

    @Override
    public void startAnyxmlNode(final NodeIdentifier name) throws IOException {
        writer.startAnyxmlNode(name);
        pushPath(name);
    }

    @Override
    public void startYangModeledAnyXmlNode(final NodeIdentifier name, final int childSizeHint) throws IOException {
        writer.startYangModeledAnyXmlNode(name, childSizeHint);
        pushPath(name);
    }

    @Override
    public void endNode() throws IOException {
        final ModifyAction prevAction = actions.peek();
        if (prevAction != null) {
            // We only split out a builder if we a changing action relative to parent and we are not inside
            // a remove/delete operation
            if (prevAction != currentAction && deleteDepth == 0) {
                dataTreeChanges.add(new DataTreeChange(writer.build(), currentAction, currentPath));
            } else {
                writer.endNode();
            }
            popPath();
        } else {
            // All done, special-cased
            LOG.debug("All done ... writer {}", writer);
            writer.endNode();
            dataTreeChanges.add(new DataTreeChange(result.getResult(), currentAction, currentPath));
        }
    }

    @Override
    public void domSourceValue(final DOMSource value) throws IOException {
        writer.domSourceValue(value);
    }

    @Override
    public void scalarValue(@NonNull final Object value) throws IOException {
        writer.scalarValue(value);
    }

    @Override
    public void close() throws IOException {
        checkState(currentPath.isEmpty(), "Cannot close with %s", currentPath);
        writer.close();
    }

    @Override
    public void flush() throws IOException {
        writer.flush();
    }

    private boolean atRemoval() {
        return currentAction == ModifyAction.DELETE || currentAction == ModifyAction.REMOVE;
    }

    private void popPath() {
        currentPath.pop();
        currentAction = actions.pop();
        if (atRemoval()) {
            checkState(deleteDepth > 0);
            deleteDepth--;
        }
    }

    private void pushPath(final PathArgument pathArgument) {
        if (currentAction != null) {
            // Nested element: inherit previous action and track number of REMOVE/DELETE operations in the stack
            if (atRemoval()) {
                deleteDepth++;
            }
            actions.push(currentAction);
        } else {
            // Top-level element: set the default action
            currentAction = defaultAction;
        }
        currentPath.push(pathArgument);
    }
}
