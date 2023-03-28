/*
 * Copyright © 2020 FRINX s.r.o. and others.  All rights reserved.
 * Copyright © 2021 PANTHEON.tech, s.r.o.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.utils.parser;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.restconf.api.query.FieldsParam;
import org.opendaylight.restconf.api.query.FieldsParam.NodeSelector;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.LeafListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;

/**
 * A translator between {@link FieldsParam} and {@link YangInstanceIdentifier}s suitable for use as field identifiers
 * in {@code netconf-dom-api}.
 *
 * <p>
 * Fields parser that stores a set of all the leaf {@link LinkedPathElement}s specified in {@link FieldsParam}.
 * Using {@link LinkedPathElement} it is possible to create a chain of path arguments and build complete paths
 * since this element contains identifiers of intermediary mixin nodes and also linked to its parent
 * {@link LinkedPathElement}.
 *
 * <p>
 * Example: field 'a(b/c;d/e)' ('e' is place under choice node 'x') is parsed into following levels:
 * <pre>
 *   - './a' +- 'a/b' - 'b/c'
 *           |
 *           +- 'a/d' - 'd/x/e'
 * </pre>
 */
public final class NetconfFieldsTranslator {
    private static final NetconfFieldsTranslator INSTANCE = new NetconfFieldsTranslator();

    private NetconfFieldsTranslator() {
        // Hidden on purpose
    }

    /**
     * Translate a {@link FieldsParam} to a list of child node paths saved in lists, suitable for use with
     * {@link NetconfDataTreeService}.
     *
     * @param identifier identifier context created from request URI
     * @param input input value of fields parameter
     * @return {@link List} of {@link YangInstanceIdentifier} that are relative to the last {@link PathArgument}
     *     of provided {@code identifier}
     */
    public static @NonNull List<YangInstanceIdentifier> translate(
            final @NonNull InstanceIdentifierContext identifier, final @NonNull FieldsParam input) {
        final var parsed = INSTANCE.parseFields(identifier, input);
        return parsed.stream().map(NetconfFieldsTranslator::buildPath).toList();
    }

    private @NonNull Set<LinkedPathElement> parseFields(final @NonNull InstanceIdentifierContext identifier,
        final @NonNull FieldsParam input) {
        final var startNode = DataSchemaContextNode.fromDataSchemaNode(
            (DataSchemaNode) identifier.getSchemaNode());

        if (startNode == null) {
            throw new RestconfDocumentedException(
                "Start node missing in " + input, ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
        }

        final var parsed = new HashSet<LinkedPathElement>();
        processSelectors(parsed, identifier.getSchemaContext(), identifier.getSchemaNode().getQName().getModule(),
            new LinkedPathElement(startNode), input.nodeSelectors());

        return parsed;
    }

    private void processSelectors(final Set<LinkedPathElement> parsed, final EffectiveModelContext context,
            final QNameModule startNamespace, final LinkedPathElement startPathElement,
            final List<NodeSelector> selectors) {
        for (var selector : selectors) {
            var pathElement = startPathElement;
            var namespace = startNamespace;

            // Note: path is guaranteed to have at least one step
            final var it = selector.path().iterator();
            do {
                final var step = it.next();
                final var module = step.module();
                if (module != null) {
                    // FIXME: this is not defensive enough, as we can fail to find the module
                    namespace = context.findModules(module).iterator().next().getQNameModule();
                }

                // add parsed path element linked to its parent
                pathElement = addChildPathElement(pathElement, step.identifier().bindTo(namespace));
            } while (it.hasNext());

            final var subs = selector.subSelectors();
            if (!subs.isEmpty()) {
                processSelectors(parsed, context, namespace, pathElement, subs);
            } else {
                parsed.add(pathElement);
            }
        }
    }

    private LinkedPathElement addChildPathElement(final LinkedPathElement currentElement,
        final QName childQName) {
        final List<PathArgument> collectedMixinNodes = new ArrayList<>();

        DataSchemaContextNode<?> currentNode = currentElement.targetNode;
        DataSchemaContextNode<?> actualContextNode = currentNode.getChild(childQName);
        if (actualContextNode == null) {
            actualContextNode = resolveMixinNode(currentNode, currentNode.getIdentifier().getNodeType());
            actualContextNode = actualContextNode.getChild(childQName);
        }

        while (actualContextNode != null && actualContextNode.isMixin()) {
            final var actualDataSchemaNode = actualContextNode.getDataSchemaNode();
            if (actualDataSchemaNode instanceof ListSchemaNode listSchema && listSchema.getKeyDefinition().isEmpty()) {
                // we need just a single node identifier from list in the path IFF it is an unkeyed list, otherwise
                // we need both (which is the default case)
                actualContextNode = actualContextNode.getChild(childQName);
            } else if (actualDataSchemaNode instanceof LeafListSchemaNode) {
                // NodeWithValue is unusable - stop parsing
                break;
            } else {
                collectedMixinNodes.add(actualContextNode.getIdentifier());
                actualContextNode = actualContextNode.getChild(childQName);
            }
        }

        if (actualContextNode == null) {
            throw new RestconfDocumentedException("Child " + childQName.getLocalName() + " node missing in "
                + currentNode.getIdentifier().getNodeType().getLocalName(),
                ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
        }

        return new LinkedPathElement(currentElement, collectedMixinNodes, actualContextNode);
    }

    private static YangInstanceIdentifier buildPath(final LinkedPathElement lastPathElement) {
        LinkedPathElement pathElement = lastPathElement;
        final LinkedList<PathArgument> path = new LinkedList<>();
        do {
            path.addFirst(pathElement.targetNodeIdentifier());
            path.addAll(0, pathElement.mixinNodesToTarget);
            pathElement = pathElement.parentPathElement;
        } while (pathElement.parentPathElement != null);

        return YangInstanceIdentifier.create(path);
    }

    private static DataSchemaContextNode<?> resolveMixinNode(
        final DataSchemaContextNode<?> node, final @NonNull QName qualifiedName) {
        DataSchemaContextNode<?> currentNode = node;
        while (currentNode != null && currentNode.isMixin()) {
            currentNode = currentNode.getChild(qualifiedName);
        }
        return currentNode;
    }

    /**
     * {@link DataSchemaContextNode} of data element grouped with identifiers of leading mixin nodes and previous
     * path element.<br>
     *  - identifiers of mixin nodes on the path to the target node - required for construction of full valid
     *    DOM paths,<br>
     *  - {@link LinkedPathElement} of the previous non-mixin node - required to successfully create a chain
     *    of {@link PathArgument}s
     */
    static final class LinkedPathElement {
        private @Nullable final LinkedPathElement parentPathElement;
        private @NonNull final List<PathArgument> mixinNodesToTarget;
        private @NonNull final DataSchemaContextNode<?> targetNode;

        private LinkedPathElement(final DataSchemaContextNode<?> targetNode) {
            this(null, List.of(), targetNode);
        }

        /**
         * Creation of new {@link LinkedPathElement}.
         *
         * @param parentPathElement     parent path element
         * @param mixinNodesToTarget    identifiers of mixin nodes on the path to the target node
         * @param targetNode            target non-mixin node
         */
        private LinkedPathElement(@Nullable final LinkedPathElement parentPathElement,
                final List<PathArgument> mixinNodesToTarget, final DataSchemaContextNode<?> targetNode) {
            this.parentPathElement = parentPathElement;
            this.mixinNodesToTarget = requireNonNull(mixinNodesToTarget);
            this.targetNode = requireNonNull(targetNode);
        }

        private PathArgument targetNodeIdentifier() {
            return targetNode.getIdentifier();
        }
    }
}
