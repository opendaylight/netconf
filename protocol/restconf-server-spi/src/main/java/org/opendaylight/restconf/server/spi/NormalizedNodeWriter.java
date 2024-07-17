/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.Beta;
import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.api.query.DepthParam;
import org.opendaylight.restconf.api.query.FieldsParam;
import org.opendaylight.restconf.api.query.FieldsParam.NodeSelector;
import org.opendaylight.restconf.server.api.ServerException;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.data.api.schema.AnydataNode;
import org.opendaylight.yangtools.yang.data.api.schema.AnyxmlNode;
import org.opendaylight.yangtools.yang.data.api.schema.ChoiceNode;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.UnkeyedListEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.UnkeyedListNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContext;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContext.PathMixin;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

/**
 * This is an experimental iterator over a {@link NormalizedNode}. This is essentially
 * the opposite of a {@link javax.xml.stream.XMLStreamReader} -- unlike instantiating an iterator over
 * the backing data, this encapsulates a {@link NormalizedNodeStreamWriter} and allows
 * us to write multiple nodes.
 */
@NonNullByDefault
public abstract class NormalizedNodeWriter implements Flushable, Closeable {
    protected final NormalizedNodeStreamWriter writer;

    NormalizedNodeWriter(final NormalizedNodeStreamWriter writer) {
        this.writer = requireNonNull(writer);
    }

    /**
     * Create a new writer backed by a {@link NormalizedNodeStreamWriter}.
     *
     * @param writer Back-end writer
     * @param maxDepth Maximal depth to write
     * @return A new instance.
     */
    public static final NormalizedNodeWriter forStreamWriter(final NormalizedNodeStreamWriter writer,
            final @Nullable DepthParam maxDepth) {
        return forStreamWriter(writer, true,  maxDepth, null);
    }

    /**
     * Create a new writer backed by a {@link NormalizedNodeStreamWriter}.
     *
     * @param writer Back-end writer
     * @param maxDepth Maximal depth to write
     * @param fields Selected child nodes to write
     * @return A new instance.
     */
    public static final NormalizedNodeWriter forStreamWriter(final NormalizedNodeStreamWriter writer,
            final @Nullable DepthParam maxDepth, final @Nullable List<Set<QName>> fields) {
        return forStreamWriter(writer, true,  maxDepth, fields);
    }

    /**
     * Create a new writer backed by a {@link NormalizedNodeStreamWriter}. Unlike the simple
     * {@link #forStreamWriter(NormalizedNodeStreamWriter, DepthParam, List)} method, this allows the caller to
     * switch off RFC6020 XML compliance, providing better throughput. The reason is that the XML mapping rules in
     * RFC6020 require the encoding to emit leaf nodes which participate in a list's key first and in the order in which
     * they are defined in the key. For JSON, this requirement is completely relaxed and leaves can be ordered in any
     * way we see fit. The former requires a bit of work: first a lookup for each key and then for each emitted node we
     * need to check whether it was already emitted.
     *
     * @param writer Back-end writer
     * @param orderKeyLeaves whether the returned instance should be RFC6020 XML compliant.
     * @param depth Maximal depth to write
     * @param fields Selected child nodes to write
     * @return A new instance.
     */
    public static final NormalizedNodeWriter forStreamWriter(final NormalizedNodeStreamWriter writer,
            final boolean orderKeyLeaves, final @Nullable DepthParam depth, final @Nullable List<Set<QName>> fields) {
        return new DefaultNormalizedNodeWriter(writer, !orderKeyLeaves, depth, fields);
    }

    /**
     * Translate a {@link FieldsParam} to a complete list of child nodes organized into levels, suitable for use with
     * {@link NormalizedNodeWriter}.
     *
     * <p>
     * Fields parser that stores set of {@link QName}s in each level. Because of this fact, from the output it is only
     * possible to assume on what depth the selected element is placed. Identifiers of intermediary mixin nodes are also
     * flatten to the same level as identifiers of data nodes.<br>
     * Example: field 'a(/b/c);d/e' ('e' is place under choice node 'x') is parsed into following levels:<br>
     * <pre>
     * level 0: ['a', 'd']
     * level 1: ['b', 'x', 'e']
     * level 2: ['c']
     * </pre>
     *
     * @param modelContext EffectiveModelContext
     * @param startNode {@link DataSchemaContext} of the API request path
     * @param input input value of fields parameter
     * @return {@link List} of levels; each level contains set of {@link QName}
     */
    @Beta
    public static List<Set<QName>> translateFieldsParam(final EffectiveModelContext modelContext,
            final DataSchemaContext startNode, final FieldsParam input) throws ServerException {
        final var parsed = new ArrayList<Set<QName>>();
        processSelectors(parsed, modelContext, startNode.dataSchemaNode().getQName().getModule(), startNode,
            input.nodeSelectors(), 0);
        return parsed;
    }

    private static void processSelectors(final List<Set<QName>> parsed, final EffectiveModelContext context,
            final QNameModule startNamespace, final DataSchemaContext startNode, final List<NodeSelector> selectors,
            final int index) throws ServerException {
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
    private static DataSchemaContext addChildToResult(final DataSchemaContext currentNode, final QName childQName,
            final Set<QName> level) throws ServerException {
        // resolve parent node
        final var parentNode = resolveMixinNode(currentNode, level, currentNode.dataSchemaNode().getQName());
        if (parentNode == null) {
            throw new ServerException(ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE,
                    "Not-mixin node missing in %s", currentNode.getPathStep().getNodeType().getLocalName());
        }

        // resolve child node
        final var childNode = resolveMixinNode(childByQName(parentNode, childQName), level, childQName);
        if (childNode == null) {
            throw new ServerException(ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE,
                "Child %s node missing in %s", childQName.getLocalName(),
                currentNode.getPathStep().getNodeType().getLocalName());
        }

        // add final childNode node to level nodes
        level.add(childNode.dataSchemaNode().getQName());
        return childNode;
    }

    private static @Nullable DataSchemaContext childByQName(final DataSchemaContext parent, final QName qname) {
        return parent instanceof DataSchemaContext.Composite composite ? composite.childByQName(qname) : null;
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
    private static @Nullable DataSchemaContext resolveMixinNode(final @Nullable DataSchemaContext node,
            final @NonNull Set<QName> level, final @NonNull QName qualifiedName) {
        DataSchemaContext currentNode = node;
        while (currentNode instanceof PathMixin currentMixin) {
            level.add(qualifiedName);
            currentNode = currentMixin.childByQName(qualifiedName);
        }
        return currentNode;
    }

    @Override
    public final void flush() throws IOException {
        writer.flush();
    }

    @Override
    public final void close() throws IOException {
        writer.flush();
        writer.close();
    }

    /**
     * Iterate over the provided {@link NormalizedNode} and emit write events to the encapsulated
     * {@link NormalizedNodeStreamWriter}.
     *
     * @param node Node
     * @return {@code ParameterAwareNormalizedNodeWriter}
     * @throws IOException when thrown from the backing writer.
     */
    public final NormalizedNodeWriter write(final NormalizedNode node) throws IOException {
        if (node instanceof ContainerNode n) {
            writeContainer(n);
        } else if (node instanceof MapNode n) {
            writeMap(n);
        } else if (node instanceof MapEntryNode n) {
            writeMapEntry(n);
        } else if (node instanceof LeafNode<?> n) {
            writeLeaf(n);
        } else if (node instanceof ChoiceNode n) {
            writeChoice(n);
        } else if (node instanceof UnkeyedListNode n) {
            writeUnkeyedList(n);
        } else if (node instanceof UnkeyedListEntryNode n) {
            writeUnkeyedListEntry(n);
        } else if (node instanceof LeafSetNode<?> n) {
            writeLeafSet(n);
        } else if (node instanceof LeafSetEntryNode<?> n) {
            writeLeafSetEntry(n);
        } else if (node instanceof AnydataNode<?> n) {
            writeAnydata(n);
        } else if (node instanceof AnyxmlNode<?> n) {
            writeAnyxml(n);
        } else {
            throw new IOException("Unhandled contract " + node.contract().getSimpleName());
        }
        return this;
    }

    protected abstract void writeAnydata(AnydataNode<?> node) throws IOException;

    protected abstract void writeAnyxml(AnyxmlNode<?> node) throws IOException;

    protected abstract void writeChoice(ChoiceNode node) throws IOException;

    protected abstract void writeContainer(ContainerNode node) throws IOException;

    protected abstract void writeLeaf(LeafNode<?> node) throws IOException;

    protected abstract void writeLeafSet(LeafSetNode<?> node) throws IOException;

    protected abstract void writeLeafSetEntry(LeafSetEntryNode<?> node) throws IOException;

    protected abstract void writeMap(MapNode node) throws IOException;

    protected abstract void writeMapEntry(MapEntryNode node) throws IOException;

    protected abstract void writeUnkeyedList(UnkeyedListNode node) throws IOException;

    protected abstract void writeUnkeyedListEntry(UnkeyedListEntryNode node) throws IOException;
}
