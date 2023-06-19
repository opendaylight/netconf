/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 * Copyright (c) 2021 PANTHEON.tech, s.r.o.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.utils.parser;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.api.query.FieldsParam;
import org.opendaylight.restconf.api.query.FieldsParam.NodeSelector;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.ParameterAwareNormalizedNodeWriter;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

/**
 * Fields parser that stores set of {@link QName}s in each level. Because of this fact, from the output
 * it is is only possible to assume on what depth the selected element is placed. Identifiers of intermediary
 * mixin nodes are also flatten to the same level as identifiers of data nodes.<br>
 * Example: field 'a(/b/c);d/e' ('e' is place under choice node 'x') is parsed into following levels:<br>
 * <pre>
 * level 0: ['a', 'd']
 * level 1: ['b', 'x', 'e']
 * level 2: ['c']
 * </pre>
 */
public final class WriterFieldsTranslator {
    private WriterFieldsTranslator() {
        // Hidden on purpose
    }

    /**
     * Translate a {@link FieldsParam} to a complete list of child nodes organized into levels, suitable for use with
     * {@link ParameterAwareNormalizedNodeWriter}.
     *
     * @param identifier identifier context created from request URI
     * @param input input value of fields parameter
     * @return {@link List} of levels; each level contains set of {@link QName}
     */
    public static @NonNull List<Set<QName>> translate(final @NonNull InstanceIdentifierContext identifier,
            final @NonNull FieldsParam input) {
        final DataSchemaContextNode<?> startNode = DataSchemaContextNode.fromDataSchemaNode(
                (DataSchemaNode) identifier.getSchemaNode());
        if (startNode == null) {
            throw new RestconfDocumentedException(
                    "Start node missing in " + input, ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
        }

        final var parsed = new ArrayList<Set<QName>>();
        processSelectors(parsed, identifier.getSchemaContext(), identifier.getSchemaNode().getQName().getModule(),
            startNode, input.nodeSelectors(), 0);
        return parsed;
    }

    private static void processSelectors(final List<Set<QName>> parsed, final EffectiveModelContext context,
            final QNameModule startNamespace, final DataSchemaContextNode<?> startNode,
            final List<NodeSelector> selectors, final int index) {
        final Set<QName> startLevel;
        if (parsed.size() <= index) {
            startLevel = new HashSet<>();
            parsed.add(startLevel);
        } else {
            startLevel = parsed.get(index);
        }
        for (var selector : selectors) {
            var node = startNode;
            var namespace = startNamespace;
            var level = startLevel;
            var levelIndex = index;

            // Note: path is guaranteed to have at least one step
            final var it = selector.path().iterator();
            while (true) {
                // FIXME: The layout of this loop is rather weird, which is due to how prepareQNameLevel() operates. We
                //        need to call it only when we know there is another identifier coming, otherwise we would end
                //        up with empty levels sneaking into the mix.
                //
                //        Dealing with that weirdness requires understanding what the expected end results are and a
                //        larger rewrite of the algorithms involved.
                final var step = it.next();
                final var module = step.module();
                if (module != null) {
                    // FIXME: this is not defensive enough, as we can fail to find the module
                    namespace = context.findModules(module).iterator().next().getQNameModule();
                }

                // add parsed identifier to results for current level
                node = addChildToResult(node, step.identifier().bindTo(namespace), level);
                if (!it.hasNext()) {
                    break;
                }

                // go one level down
                level = prepareQNameLevel(parsed, level);
                levelIndex++;
            }

            final var subs = selector.subSelectors();
            if (!subs.isEmpty()) {
                processSelectors(parsed, context, namespace, node, subs, levelIndex + 1);
            }
        }
    }

    /**
     * Preparation of the identifiers level that is used as storage for parsed identifiers. If the current level exist
     * at the index that doesn't equal to the last index of already parsed identifiers, a new level of identifiers
     * is allocated and pushed to input parsed identifiers.
     *
     * @param parsedIdentifiers Already parsed list of identifiers grouped to multiple levels.
     * @param currentLevel Current level of identifiers (set).
     * @return Existing or new level of identifiers.
     */
    private static Set<QName> prepareQNameLevel(final List<Set<QName>> parsedIdentifiers,
            final Set<QName> currentLevel) {
        final var existingLevel = parsedIdentifiers.stream()
                .filter(qNameSet -> qNameSet.equals(currentLevel))
                .findAny();
        if (existingLevel.isPresent()) {
            final int index = parsedIdentifiers.indexOf(existingLevel.orElseThrow());
            if (index == parsedIdentifiers.size() - 1) {
                final var nextLevel = new HashSet<QName>();
                parsedIdentifiers.add(nextLevel);
                return nextLevel;
            }

            return parsedIdentifiers.get(index + 1);
        }

        final var nextLevel = new HashSet<QName>();
        parsedIdentifiers.add(nextLevel);
        return nextLevel;
    }

    /**
     * Add parsed child of current node to result for current level.
     *
     * @param currentNode current node
     * @param childQName parsed identifier of child node
     * @param level current nodes level
     * @return {@link DataSchemaContextNode}
     */
    private static DataSchemaContextNode<?> addChildToResult(final DataSchemaContextNode<?> currentNode,
            final QName childQName, final Set<QName> level) {
        // resolve parent node
        final DataSchemaContextNode<?> parentNode = resolveMixinNode(
                currentNode, level, currentNode.getIdentifier().getNodeType());
        if (parentNode == null) {
            throw new RestconfDocumentedException(
                    "Not-mixin node missing in " + currentNode.getIdentifier().getNodeType().getLocalName(),
                    ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
        }

        // resolve child node
        final DataSchemaContextNode<?> childNode = resolveMixinNode(
                parentNode.getChild(childQName), level, childQName);
        if (childNode == null) {
            throw new RestconfDocumentedException(
                    "Child " + childQName.getLocalName() + " node missing in "
                            + currentNode.getIdentifier().getNodeType().getLocalName(),
                    ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
        }

        // add final childNode node to level nodes
        level.add(childNode.getIdentifier().getNodeType());
        return childNode;
    }

    /**
     * Resolve mixin node by searching for inner nodes until not mixin node or null is found.
     * All nodes expect of not mixin node are added to current level nodes.
     *
     * @param node          initial mixin or not-mixin node
     * @param level         current nodes level
     * @param qualifiedName qname of initial node
     * @return {@link DataSchemaContextNode}
     */
    private static @Nullable DataSchemaContextNode<?> resolveMixinNode(final @Nullable DataSchemaContextNode<?> node,
            final @NonNull Set<QName> level, final @NonNull QName qualifiedName) {
        DataSchemaContextNode<?> currentNode = node;
        while (currentNode != null && currentNode.isMixin()) {
            level.add(qualifiedName);
            currentNode = currentNode.getChild(qualifiedName);
        }

        return currentNode;
    }
}