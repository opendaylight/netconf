/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams.listeners;

import static com.google.common.base.Verify.verifyNotNull;
import static java.util.Objects.requireNonNull;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.tree.api.DataTreeCandidate;
import org.opendaylight.yangtools.yang.data.tree.api.DataTreeCandidateNode;
import org.opendaylight.yangtools.yang.data.tree.api.ModificationType;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextTree;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack.Inference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class AbstractWebsocketSerializer<T extends Exception> {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractWebsocketSerializer.class);

    private final EffectiveModelContext context;
    protected boolean emptyDataChangedEvent = true;

    AbstractWebsocketSerializer(final EffectiveModelContext context) {
        this.context = requireNonNull(context);
    }

    public final void serialize(final DataTreeCandidate candidate, final boolean leafNodesOnly, final boolean skipData,
                                final boolean changedLeafNodesOnly)
            throws T {
        if (leafNodesOnly || changedLeafNodesOnly) {
            final Deque<PathArgument> path = new ArrayDeque<>();
            path.addAll(candidate.getRootPath().getPathArguments());
            serializeLeafNodesOnly(path, candidate.getRootNode(), skipData, changedLeafNodesOnly);
        } else {
            serializeData(candidate.getRootPath().getPathArguments(), candidate.getRootNode(), skipData);
        }
    }

    final void serializeLeafNodesOnly(final Deque<PathArgument> path, final DataTreeCandidateNode candidate,
            final boolean skipData, final boolean changedLeafNodesOnly) throws T {
        NormalizedNode node = null;
        switch (candidate.getModificationType()) {
            case UNMODIFIED ->
                // no reason to do anything with an unmodified node
                LOG.debug("DataTreeCandidate for a notification is unmodified, not serializing leaves. Candidate: {}",
                        candidate);
            case SUBTREE_MODIFIED, APPEARED -> node = candidate.getDataAfter().get();
            case WRITE -> node = changedLeafNodesOnly && isNotUpdate(candidate) ? null : candidate.getDataAfter().get();
            case DELETE, DISAPPEARED -> node = candidate.getDataBefore().get();
            default ->
                LOG.error("DataTreeCandidate modification has unknown type: {}", candidate.getModificationType());
        }

        if (node == null) {
            return;
        }

        if (node instanceof LeafNode<?> || node instanceof LeafSetNode) {
            serializeData(path, candidate, skipData);
            return;
        }

        for (DataTreeCandidateNode childNode : candidate.getChildNodes()) {
            path.add(childNode.getIdentifier());
            serializeLeafNodesOnly(path, childNode, skipData, changedLeafNodesOnly);
            path.removeLast();
        }
    }

    private void serializeData(final Collection<PathArgument> dataPath, final DataTreeCandidateNode candidate,
            final boolean skipData) throws T {
        var stack = SchemaInferenceStack.of(context);
        var current = DataSchemaContextTree.from(context).getRoot();
        for (var arg : dataPath) {
            final var next = verifyNotNull(current.enterChild(stack, arg),
                "Failed to resolve %s: cannot find %s in %s", dataPath, arg, current);
            current = next;
        }

        // Exit to parent if needed
        if (!stack.isEmpty()) {
            stack.exit();
        }

        serializeData(stack.toInference(), dataPath, candidate, skipData);
    }

    abstract void serializeData(Inference parent, Collection<PathArgument> dataPath, DataTreeCandidateNode candidate,
        boolean skipData) throws T;

    private static boolean isNotUpdate(DataTreeCandidateNode node) {
        return node.getDataBefore().isPresent() && node.getDataAfter().isPresent()
                && node.getDataBefore().get().body().equals(node.getDataAfter().get().body());
    }

    abstract void serializePath(Collection<PathArgument> pathArguments) throws T;

    abstract void serializeOperation(DataTreeCandidateNode candidate) throws T;

    static final String convertPath(final Collection<PathArgument> path) {
        final StringBuilder pathBuilder = new StringBuilder();

        for (PathArgument pathArgument : path) {
            if (pathArgument instanceof YangInstanceIdentifier.AugmentationIdentifier) {
                continue;
            }
            pathBuilder.append('/');
            pathBuilder.append(pathArgument.getNodeType().getNamespace().toString().replace(':', '-'));
            pathBuilder.append(':');
            pathBuilder.append(pathArgument.getNodeType().getLocalName());

            if (pathArgument instanceof YangInstanceIdentifier.NodeIdentifierWithPredicates) {
                pathBuilder.append("[");
                final Set<Map.Entry<QName, Object>> keys =
                        ((YangInstanceIdentifier.NodeIdentifierWithPredicates) pathArgument).entrySet();
                for (Map.Entry<QName, Object> key : keys) {
                    pathBuilder.append(key.getKey().getNamespace().toString().replace(':', '-'));
                    pathBuilder.append(':');
                    pathBuilder.append(key.getKey().getLocalName());
                    pathBuilder.append("='");
                    pathBuilder.append(key.getValue().toString());
                    pathBuilder.append('\'');
                }
                pathBuilder.append(']');
            }
        }

        return pathBuilder.toString();
    }

    static final String modificationTypeToOperation(final DataTreeCandidateNode candidate,
            final ModificationType modificationType) {
        switch (modificationType) {
            case UNMODIFIED:
                // shouldn't ever happen since the root of a modification is only triggered by some event
                LOG.warn("DataTreeCandidate for a notification is unmodified. Candidate: {}", candidate);
                return "none";
            case SUBTREE_MODIFIED:
            case WRITE:
            case APPEARED:
                if (candidate.getDataBefore().isPresent()) {
                    return "updated";
                } else {
                    return "created";
                }
            case DELETE:
            case DISAPPEARED:
                return "deleted";
            default:
                LOG.error("DataTreeCandidate modification has unknown type: {}", candidate.getModificationType());
                throw new IllegalStateException("Unknown modification type");
        }
    }

    public boolean isEmptyDataChangedEvent() {
        return emptyDataChangedEvent;
    }
}
