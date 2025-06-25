/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.spi;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.databind.DatabindPath;
import org.opendaylight.netconf.databind.RequestException;
import org.opendaylight.restconf.api.query.FieldsParam;
import org.opendaylight.restconf.server.api.DataGetParams;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.restconf.server.spi.ServerDataOperations;
import org.opendaylight.yangtools.yang.common.Empty;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContext;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.LeafListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;

/**
 * Class suitable for use with {@link ServerDataOperations#getData(ServerRequest, DatabindPath.Data, DataGetParams)}
 * to parse provided {@link DataGetParams#fields()} into list of {@link YangInstanceIdentifier} child nodes.
 */
@VisibleForTesting
@NonNullByDefault
public final class FieldsParamParser {
    private final EffectiveModelContext modelContext;
    private final DataSchemaContext startNode;

    private FieldsParamParser(final EffectiveModelContext context, final DataSchemaContext startNode) {
        this.modelContext = requireNonNull(context);
        this.startNode = requireNonNull(startNode);
    }

    /**
     * Translate a {@link FieldsParam} to a list of child node paths saved in lists.
     *
     * <p>Fields parser that stores a set of all the leaf {@link LinkedPathElement}s specified in {@link FieldsParam}.
     * Using {@link LinkedPathElement} it is possible to create a chain of path arguments and build complete paths
     * since this element contains identifiers of intermediary mixin nodes and also linked to its parent
     * {@link LinkedPathElement}.
     *
     * <p>Example: field 'a(b/c;d/e)' ('e' is place under choice node 'x') is parsed into following levels:
     * <pre>
     *   - './a' +- 'a/b' - 'b/c'
     *           |
     *           +- 'a/d' - 'd/x/e'
     * </pre>
     *
     *
     * @param context {@link EffectiveModelContext}.
     * @param startNode {@link DataSchemaContext}.
     * @param fields value of the {@code fields} query parameter.
     * @return {@link List} of {@link YangInstanceIdentifier} that are relative to the last
     *          {@link YangInstanceIdentifier.PathArgument} of provided {@code identifier}.
     * @throws RequestException when an error occurs.
     */
    public static List<YangInstanceIdentifier> fieldsParamsToPaths(final EffectiveModelContext context,
            final DataSchemaContext startNode, final @Nullable FieldsParam fields) throws RequestException {
        final var fieldsParamParser = new FieldsParamParser(context, startNode);
        return fieldsParamParser.toPaths(fields);
    }

    private List<YangInstanceIdentifier> toPaths(final @Nullable FieldsParam fields) throws RequestException {
        final var parsed = new HashSet<LinkedPathElement>();
        if (fields == null) {
            return List.of();
        }
        processSelectors(parsed, startNode.dataSchemaNode().getQName().getModule(),
            new LinkedPathElement(null, List.of(), startNode), fields.nodeSelectors());
        return parsed.stream().map(FieldsParamParser::buildPath).toList();
    }

    private void processSelectors(final Set<LinkedPathElement> parsed, final QNameModule startNamespace,
            final LinkedPathElement startPathElement, final List<FieldsParam.NodeSelector> selectors)
            throws RequestException {
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
                    namespace = modelContext.findModules(module).iterator().next().getQNameModule();
                }

                // add parsed path element linked to its parent
                pathElement = addChildPathElement(pathElement, step.identifier().bindTo(namespace));
            } while (it.hasNext());

            final var subs = selector.subSelectors();
            if (!subs.isEmpty()) {
                processSelectors(parsed, namespace, pathElement, subs);
            } else {
                parsed.add(pathElement);
            }
        }
    }

    private static LinkedPathElement addChildPathElement(final LinkedPathElement currentElement,
            final QName childQName) throws RequestException {
        final var collectedMixinNodes = new ArrayList<YangInstanceIdentifier.PathArgument>();

        final var currentNode = currentElement.targetNode;
        var actualContextNode = childByQName(currentNode, childQName);
        if (actualContextNode == null) {
            actualContextNode = resolveMixinNode(currentNode, currentNode.getPathStep().getNodeType());
            actualContextNode = childByQName(actualContextNode, childQName);
        }

        while (actualContextNode instanceof DataSchemaContext.PathMixin) {
            final var actualDataSchemaNode = actualContextNode.dataSchemaNode();
            if (actualDataSchemaNode instanceof ListSchemaNode listSchema && listSchema.getKeyDefinition().isEmpty()) {
                // we need just a single node identifier from list in the path IFF it is an unkeyed list, otherwise
                // we need both (which is the default case)
                actualContextNode = childByQName(actualContextNode, childQName);
            } else if (actualDataSchemaNode instanceof LeafListSchemaNode) {
                // NodeWithValue is unusable - stop parsing
                break;
            } else {
                collectedMixinNodes.add(actualContextNode.getPathStep());
                actualContextNode = childByQName(actualContextNode, childQName);
            }
        }

        if (actualContextNode == null) {
            throw new RequestException(ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE, "Child %s node missing in %s",
                childQName.getLocalName(), currentNode.getPathStep().getNodeType().getLocalName());
        }

        return new LinkedPathElement(currentElement, collectedMixinNodes, actualContextNode);
    }

    private static @Nullable DataSchemaContext childByQName(final @Nullable DataSchemaContext parent,
            final QName qname) {
        return parent instanceof DataSchemaContext.Composite composite ? composite.childByQName(qname) : null;
    }

    private static YangInstanceIdentifier buildPath(final LinkedPathElement lastPathElement) {
        var pathElement = lastPathElement;
        final var path = new LinkedList<YangInstanceIdentifier.PathArgument>();
        do {
            path.addFirst(contextPathArgument(pathElement.targetNode));
            path.addAll(0, pathElement.mixinNodesToTarget);
            pathElement = pathElement.parentPathElement;
        } while (pathElement != null && pathElement.parentPathElement != null);

        return YangInstanceIdentifier.of(path);
    }

    private static YangInstanceIdentifier.PathArgument contextPathArgument(final DataSchemaContext context) {
        final var arg = context.pathStep();
        if (arg != null) {
            return arg;
        }

        final var schema = context.dataSchemaNode();
        if (schema instanceof ListSchemaNode listSchema && !listSchema.getKeyDefinition().isEmpty()) {
            return YangInstanceIdentifier.NodeIdentifierWithPredicates.of(listSchema.getQName());
        }
        if (schema instanceof LeafListSchemaNode leafListSchema) {
            return new YangInstanceIdentifier.NodeWithValue<>(leafListSchema.getQName(), Empty.value());
        }
        throw new UnsupportedOperationException("Unsupported schema " + schema);
    }

    private static @Nullable DataSchemaContext resolveMixinNode(final DataSchemaContext node,
            final QName qualifiedName) {
        DataSchemaContext currentNode = node;
        while (currentNode != null && currentNode instanceof DataSchemaContext.PathMixin currentMixin) {
            currentNode = currentMixin.childByQName(qualifiedName);
        }
        return currentNode;
    }


    /**
     * {@link DataSchemaContext} of data element grouped with identifiers of leading mixin nodes and previous path
     * element.
     * <ul>
     *  <li>identifiers of mixin nodes on the path to the target node - required for construction of full valid
     *    DOM paths.</li>
     *  <li>{@link LinkedPathElement} of the previous non-mixin node - required to successfully create a chain
     *    of {@link YangInstanceIdentifier.PathArgument}s.</li>
     * </ul>
     *
     * @param parentPathElement     parent path element
     * @param mixinNodesToTarget    identifiers of mixin nodes on the path to the target node
     * @param targetNode            target non-mixin node
     */
    private record LinkedPathElement(
            @Nullable LinkedPathElement parentPathElement,
            List<YangInstanceIdentifier.PathArgument> mixinNodesToTarget,
            DataSchemaContext targetNode) {
        LinkedPathElement {
            requireNonNull(mixinNodesToTarget);
            requireNonNull(targetNode);
        }
    }
}
