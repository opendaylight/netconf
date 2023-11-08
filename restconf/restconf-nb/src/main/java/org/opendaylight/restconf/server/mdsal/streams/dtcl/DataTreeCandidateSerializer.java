/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.mdsal.streams.dtcl;

import static com.google.common.base.Verify.verifyNotNull;
import static java.util.Objects.requireNonNull;

import com.google.common.base.VerifyException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.nb.rfc8040.streams.TextParameters;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.remote.rev140114.data.changed.notification.DataChangeEvent;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.remote.rev140114.data.changed.notification.DataChangeEvent.Operation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.remote.rev140114.data.changed.notification.data.change.event.Data;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.ChoiceNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.data.tree.api.DataTreeCandidate;
import org.opendaylight.yangtools.yang.data.tree.api.DataTreeCandidateNode;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContext;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextTree;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack.Inference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class DataTreeCandidateSerializer<T extends Exception> {
    private static final Logger LOG = LoggerFactory.getLogger(DataTreeCandidateSerializer.class);
    static final @NonNull QName PATH_QNAME = QName.create(DataChangeEvent.QNAME, "path").intern();
    static final @NonNull NodeIdentifier PATH_NID = NodeIdentifier.create(PATH_QNAME);
    static final @NonNull QName OPERATION_QNAME = QName.create(DataChangeEvent.QNAME, "operation").intern();
    static final @NonNull NodeIdentifier OPERATION_NID = NodeIdentifier.create(OPERATION_QNAME);
    static final @NonNull String DATA_NAME = Data.QNAME.getLocalName();

    private final EffectiveModelContext context;

    DataTreeCandidateSerializer(final EffectiveModelContext context) {
        this.context = requireNonNull(context);
    }

    final boolean serialize(final DataTreeCandidate candidate, final TextParameters params) throws T {
        final var skipData = params.skipData();
        final var changedLeafNodesOnly = params.changedLeafNodesOnly();
        if (changedLeafNodesOnly || params.leafNodesOnly()) {
            return serializeLeafNodesOnly(mutableRootPath(candidate), candidate.getRootNode(), skipData,
                changedLeafNodesOnly);
        }
        if (params.childNodesOnly()) {
            return serializeChildNodesOnly(mutableRootPath(candidate), candidate.getRootNode(), skipData);
        }

        serializeData(candidate.getRootPath().getPathArguments(), candidate.getRootNode(), skipData);
        return true;
    }

    private static Deque<PathArgument> mutableRootPath(final DataTreeCandidate candidate) {
        final var ret = new ArrayDeque<PathArgument>();
        ret.addAll(candidate.getRootPath().getPathArguments());
        return ret;
    }

    final boolean serializeLeafNodesOnly(final Deque<PathArgument> path, final DataTreeCandidateNode candidate,
            final boolean skipData, final boolean changedLeafNodesOnly) throws T {
        final var node = switch (candidate.modificationType()) {
            case SUBTREE_MODIFIED, APPEARED -> candidate.getDataAfter();
            case DELETE, DISAPPEARED -> candidate.getDataBefore();
            case WRITE -> changedLeafNodesOnly && isNotUpdate(candidate) ? null : candidate.getDataAfter();
            case UNMODIFIED -> {
                // no reason to do anything with an unmodified node
                LOG.debug("DataTreeCandidate for a notification is unmodified, not serializing leaves. Candidate: {}",
                        candidate);
                yield null;
            }
        };

        if (node == null) {
            return false;
        }
        if (node instanceof LeafNode || node instanceof LeafSetNode) {
            serializeData(path, candidate, skipData);
            return true;
        }

        // Retain a modicum of sanity here: children may come from different namespaces. Report children from the same
        // namespace first, holding others back. Once that is done, sort the remaining children by their PathArgument
        // and report them in that order.
        final var myNamespace = node.name().getNodeType().getModule();
        final var heldBack = new ArrayList<DataTreeCandidateNode>();
        boolean ret = false;
        for (var childNode : candidate.childNodes()) {
            final var childName = childNode.name();
            if (myNamespace.equals(childName.getNodeType().getModule())) {
                ret |= serializeChild(path, childNode, skipData, changedLeafNodesOnly);
            } else {
                heldBack.add(childNode);
            }
        }
        if (!heldBack.isEmpty()) {
            // This is not exactly nice, as we really should be using schema definition order, but we do not have it
            // available here, so we fall back to the next best thing.
            heldBack.sort(Comparator.comparing(DataTreeCandidateNode::name));
            for (var childNode : heldBack) {
                ret |= serializeChild(path, childNode, skipData, changedLeafNodesOnly);
            }
        }
        return ret;
    }

    private boolean serializeChild(final Deque<PathArgument> path, final DataTreeCandidateNode childNode,
            final boolean skipData, final boolean changedLeafNodesOnly) throws T {
        final boolean ret;
        path.add(childNode.name());
        ret = serializeLeafNodesOnly(path, childNode, skipData, changedLeafNodesOnly);
        path.removeLast();
        return ret;
    }

    private boolean serializeChildNodesOnly(final Deque<PathArgument> path, final DataTreeCandidateNode current,
            final boolean skipData) throws T {
        return switch (current.modificationType()) {
            case APPEARED, WRITE -> serializeChildNodesOnlyTerminal(path, current, skipData, current.getDataAfter());
            case DISAPPEARED, DELETE -> serializeChildNodesOnlyTerminal(path, current, skipData,
                current.getDataBefore());
            case SUBTREE_MODIFIED -> serializeChildNodesOnlyChildren(path, current, skipData);
            case UNMODIFIED -> false;
        };
    }

    private boolean serializeChildNodesOnlyTerminal(final Deque<PathArgument> path, final DataTreeCandidateNode current,
            final boolean skipData, final NormalizedNode data) throws T {
        if (data instanceof ChoiceNode || data instanceof LeafSetNode || data instanceof MapNode) {
            return serializeChildNodesOnlyChildren(path, current, skipData);
        }

        final var updated = !isNotUpdate(current);
        if (updated) {
            serializeData(path, current, skipData);
        }
        return updated;
    }

    private boolean serializeChildNodesOnlyChildren(final Deque<PathArgument> path, final DataTreeCandidateNode current,
            final boolean skipData) throws T {
        var updated = false;
        for (var child : current.childNodes()) {
            path.add(child.name());
            updated |= serializeChildNodesOnly(path, child, skipData);
            path.removeLast();
        }
        return updated;
    }

    private void serializeData(final Collection<PathArgument> dataPath, final DataTreeCandidateNode candidate,
            final boolean skipData) throws T {
        var stack = SchemaInferenceStack.of(context);
        DataSchemaContext current = DataSchemaContextTree.from(context).getRoot();
        for (var arg : dataPath) {
            final var next = current instanceof DataSchemaContext.Composite composite ? composite.enterChild(stack, arg)
                : null;
            current = verifyNotNull(next,  "Failed to resolve %s: cannot find %s in %s", dataPath, arg, current);
        }

        // Exit to parent if needed
        if (!stack.isEmpty()) {
            stack.exit();
        }

        serializeData(stack.toInference(), dataPath, candidate, skipData);
    }

    abstract void serializeData(Inference parent, Collection<PathArgument> dataPath, DataTreeCandidateNode candidate,
        boolean skipData) throws T;

    private static boolean isNotUpdate(final DataTreeCandidateNode node) {
        final var before = node.dataBefore();
        final var after = node.dataAfter();

        return before != null && after != null && before.body().equals(after.body());
    }

    static final @Nullable NormalizedNode getDataAfter(final DataTreeCandidateNode candidate) {
        final var data = candidate.dataAfter();
        if (data instanceof MapEntryNode mapEntry) {
            return ImmutableNodes.mapNodeBuilder(data.name().getNodeType()).withChild(mapEntry).build();
        }
        return data;
    }

    static final @NonNull String modificationTypeToOperation(final DataTreeCandidateNode candidate) {
        final var operation = switch (candidate.modificationType()) {
            case APPEARED, SUBTREE_MODIFIED, WRITE -> candidate.dataBefore() != null ? Operation.Updated
                : Operation.Created;
            case DELETE, DISAPPEARED -> Operation.Deleted;
            case UNMODIFIED -> {
                throw new VerifyException("DataTreeCandidate for a notification is unmodified. Candidate: "
                    + candidate);
            }
        };
        return operation.getName();
    }
}
