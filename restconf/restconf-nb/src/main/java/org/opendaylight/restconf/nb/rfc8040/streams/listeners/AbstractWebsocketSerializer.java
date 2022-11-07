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
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.AugmentationIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetNode;
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

    AbstractWebsocketSerializer(final EffectiveModelContext context) {
        this.context = requireNonNull(context);
    }

    public final boolean serialize(final DataTreeCandidate candidate, final boolean leafNodesOnly,
            final boolean skipData, final boolean changedLeafNodesOnly) throws T {
        if (leafNodesOnly || changedLeafNodesOnly) {
            final var path = new ArrayDeque<PathArgument>();
            path.addAll(candidate.getRootPath().getPathArguments());
            return serializeLeafNodesOnly(path, candidate.getRootNode(), skipData, changedLeafNodesOnly);
        }

        serializeData(candidate.getRootPath().getPathArguments(), candidate.getRootNode(), skipData);
        return true;
    }

    final boolean serializeLeafNodesOnly(final Deque<PathArgument> path, final DataTreeCandidateNode candidate,
            final boolean skipData, final boolean changedLeafNodesOnly) throws T {
        final var node = switch (candidate.getModificationType()) {
            case SUBTREE_MODIFIED, APPEARED -> candidate.getDataAfter().orElseThrow();
            case DELETE, DISAPPEARED -> candidate.getDataBefore().orElseThrow();
            case WRITE -> changedLeafNodesOnly && isNotUpdate(candidate) ? null
                : candidate.getDataAfter().orElseThrow();
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

        boolean ret = false;
        for (var childNode : candidate.getChildNodes()) {
            path.add(childNode.getIdentifier());
            ret |= serializeLeafNodesOnly(path, childNode, skipData, changedLeafNodesOnly);
            path.removeLast();
        }
        return ret;
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

    private static boolean isNotUpdate(final DataTreeCandidateNode node) {
        final var before = node.getDataBefore();
        final var after = node.getDataAfter();

        return before.isPresent() && after.isPresent()
            && before.orElseThrow().body().equals(after.orElseThrow().body());
    }

    abstract void serializePath(Collection<PathArgument> pathArguments) throws T;

    abstract void serializeOperation(DataTreeCandidateNode candidate) throws T;

    static final String convertPath(final Collection<PathArgument> path) {
        final StringBuilder pathBuilder = new StringBuilder();

        for (var pathArgument : path) {
            if (pathArgument instanceof AugmentationIdentifier) {
                continue;
            }
            pathBuilder.append('/');
            pathBuilder.append(pathArgument.getNodeType().getNamespace().toString().replace(':', '-'));
            pathBuilder.append(':');
            pathBuilder.append(pathArgument.getNodeType().getLocalName());

            if (pathArgument instanceof NodeIdentifierWithPredicates nip) {
                pathBuilder.append("[");
                for (var key : nip.entrySet()) {
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
        return switch (modificationType) {
            case APPEARED, SUBTREE_MODIFIED, WRITE -> candidate.getDataBefore().isPresent() ? "updated" : "created";
            case DELETE, DISAPPEARED -> "deleted";
            case UNMODIFIED -> {
                // shouldn't ever happen since the root of a modification is only triggered by some event
                LOG.warn("DataTreeCandidate for a notification is unmodified. Candidate: {}", candidate);
                yield "none";
            }
        };
    }
}
