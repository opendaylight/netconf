/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.utils.parser;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.FieldsParam;
import org.opendaylight.restconf.nb.rfc8040.FieldsParam.NodeSelector;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

/**
 * Utilities used for parsing of fields query parameter content.
 *
 * @param <T> type of identifier
 */
public abstract class AbstractFieldsTranslator<T> {
    AbstractFieldsTranslator() {
        // Hidden on purpose
    }

    /**
     * Parse fields parameter and return complete list of child nodes organized into levels.
     *
     * @param identifier identifier context created from request URI
     * @param input input value of fields parameter
     * @return {@link List} of levels; each level contains {@link Set} of identifiers of type {@link T}
     */
    protected final @NonNull List<Set<T>> parseFields(final @NonNull InstanceIdentifierContext<?> identifier,
                                                      final @NonNull FieldsParam input) {
        final DataSchemaContextNode<?> startNode = DataSchemaContextNode.fromDataSchemaNode(
                (DataSchemaNode) identifier.getSchemaNode());

        if (startNode == null) {
            throw new RestconfDocumentedException(
                    "Start node missing in " + input, ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
        }

        final List<Set<T>> parsed = new ArrayList<>();
        processSelectors(parsed, identifier.getSchemaContext(), identifier.getSchemaNode().getQName().getModule(),
            startNode, input.nodeSelectors(), 0);
        return parsed;
    }

    /**
     * Add parsed child of current node to result for current level.
     *
     * @param currentNode current node
     * @param childQName parsed identifier of child node
     * @param level current nodes level
     * @return {@link DataSchemaContextNode}
     */
    protected abstract @NonNull DataSchemaContextNode<?> addChildToResult(@NonNull DataSchemaContextNode<?> currentNode,
            @NonNull QName childQName, @NonNull Set<T> level);

    private void processSelectors(final List<Set<T>> parsed, final EffectiveModelContext context,
            final QNameModule startNamespace, final DataSchemaContextNode<?> startNode,
            final List<NodeSelector> selectors, final int index) {
        Set<T> startLevel;
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
    private Set<T> prepareQNameLevel(final List<Set<T>> parsedIdentifiers, final Set<T> currentLevel) {
        final Optional<Set<T>> existingLevel = parsedIdentifiers.stream()
                .filter(qNameSet -> qNameSet.equals(currentLevel))
                .findAny();
        if (existingLevel.isPresent()) {
            final int index = parsedIdentifiers.indexOf(existingLevel.get());
            if (index == parsedIdentifiers.size() - 1) {
                final Set<T> nextLevel = new HashSet<>();
                parsedIdentifiers.add(nextLevel);
                return nextLevel;
            }

            return parsedIdentifiers.get(index + 1);
        }

        final Set<T> nextLevel = new HashSet<>();
        parsedIdentifiers.add(nextLevel);
        return nextLevel;
    }
}