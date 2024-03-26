/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import static com.google.common.base.Verify.verify;
import static com.google.common.base.Verify.verifyNotNull;
import static java.util.Objects.requireNonNull;

import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.api.ApiPath.ApiIdentifier;
import org.opendaylight.restconf.api.ApiPath.ListInstance;
import org.opendaylight.restconf.api.ApiPath.Step;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.Insert.PointNormalizer;
import org.opendaylight.restconf.server.api.DatabindContext;
import org.opendaylight.restconf.server.spi.ApiPathNormalizer.Path.Action;
import org.opendaylight.restconf.server.spi.ApiPathNormalizer.Path.Data;
import org.opendaylight.restconf.server.spi.ApiPathNormalizer.Path.Rpc;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeWithValue;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONCodec;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContext;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContext.Composite;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContext.PathMixin;
import org.opendaylight.yangtools.yang.model.api.ActionNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveStatementInference;
import org.opendaylight.yangtools.yang.model.api.LeafListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.TypedDataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.stmt.ActionEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.InputEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.OutputEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.RpcEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaTreeEffectiveStatement;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack.Inference;

/**
 * Utility for normalizing {@link ApiPath}s. An {@link ApiPath} can represent a number of different constructs, as
 * denoted to in the {@link Path} interface hierarchy.
 *
 * <p>
 * This process is governed by
 * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-3.5.3">RFC8040, section 3.5.3</a>. The URI provides the
 * equivalent of NETCONF XML filter encoding, with data values being escaped RFC7891 strings.
 */
public final class ApiPathNormalizer implements PointNormalizer {
    /**
     * A normalized {@link ApiPath}. This can be either
     * <ul>
     *   <li>a {@link Data} pointing to a datastore resource, or</li>
     *   <li>an {@link Rpc} pointing to a YANG {@code rpc} statement, or</li>
     *   <li>an {@link Action} pointing to an instantiation of a YANG {@code action} statement</li>
     * </ul>
     */
    @NonNullByDefault
    public sealed interface Path {
        /**
         * Returns the {@link EffectiveStatementInference} made by this path.
         *
         * @return the {@link EffectiveStatementInference} made by this path
         */
        Inference inference();

        /**
         * A {@link Path} denoting an invocation of a YANG {@code action}.
         *
         * @param inference the {@link EffectiveStatementInference} made by this path
         * @param instance the {@link YangInstanceIdentifier} of the instance being referenced, guaranteed to be
         *        non-empty
         * @param action the {@code action}
         */
        record Action(Inference inference, YangInstanceIdentifier instance, ActionEffectiveStatement action)
                implements OperationPath, InstanceReference {
            public Action {
                requireNonNull(inference);
                requireNonNull(action);
                if (instance.isEmpty()) {
                    throw new IllegalArgumentException("action must be instantiated on a data resource");
                }
            }

            @Override
            public InputEffectiveStatement inputStatement() {
                return action.input();
            }

            @Override
            public OutputEffectiveStatement outputStatement() {
                return action.output();
            }
        }

        /**
         * A {@link Path} denoting a datastore instance.
         *
         * @param inference the {@link EffectiveStatementInference} made by this path
         * @param instance the {@link YangInstanceIdentifier} of the instance being referenced,
         *                 {@link YangInstanceIdentifier#empty()} denotes the datastore
         * @param schema the {@link DataSchemaContext} of the datastore instance
         */
        // FIXME: split into 'Datastore' and 'Data' with non-empty instance, so we can bind to correct
        //        instance-identifier semantics, which does not allow YangInstanceIdentifier.empty()
        record Data(Inference inference, YangInstanceIdentifier instance, DataSchemaContext schema)
                implements InstanceReference {
            public Data {
                requireNonNull(inference);
                requireNonNull(instance);
                requireNonNull(schema);
            }
        }

        /**
         * A {@link Path} denoting an invocation of a YANG {@code rpc}.
         *
         * @param inference the {@link EffectiveStatementInference} made by this path
         * @param rpc the {@code rpc}
         */
        record Rpc(Inference inference, RpcEffectiveStatement rpc) implements OperationPath {
            public Rpc {
                requireNonNull(inference);
                requireNonNull(rpc);
            }

            @Override
            public InputEffectiveStatement inputStatement() {
                return rpc.input();
            }

            @Override
            public OutputEffectiveStatement outputStatement() {
                return rpc.output();
            }
        }
    }

    /**
     * An intermediate trait of {@link Path}s which are referencing a YANG data resource. This can be either
     * a {@link Data}, or an {@link Action}}.
     */
    @NonNullByDefault
    public sealed interface InstanceReference extends Path {
        /**
         * Returns the {@link YangInstanceIdentifier} of the instance being referenced.
         *
         * @return the {@link YangInstanceIdentifier} of the instance being referenced,
         *         {@link YangInstanceIdentifier#empty()} denotes the datastora
         */
        YangInstanceIdentifier instance();
    }

    /**
     * An intermediate trait of {@link Path}s which are referencing a YANG operation. This can be either
     * an {@link Action} on an {@link Rpc}.
     */
    @NonNullByDefault
    public sealed interface OperationPath extends Path {
        /**
         * Returns the {@code input} statement of this operation.
         *
         * @return the {@code input} statement of this operation
         */
        InputEffectiveStatement inputStatement();

        /**
         * Returns the {@code output} statement of this operation.
         *
         * @return the {@code output} statement of this operation
         */
        OutputEffectiveStatement outputStatement();
    }

    private final @NonNull DatabindContext databind;

    public ApiPathNormalizer(final DatabindContext databind) {
        this.databind = requireNonNull(databind);
    }

    public @NonNull Path normalizePath(final ApiPath apiPath) {
        final var it = apiPath.steps().iterator();
        if (!it.hasNext()) {
            return new Data(Inference.ofDataTreePath(databind.modelContext()), YangInstanceIdentifier.of(),
                databind.schemaTree().getRoot());
        }

        // First step is somewhat special:
        // - it has to contain a module qualifier
        // - it has to consider RPCs, for which we need SchemaContext
        //
        // We therefore peel that first iteration here and not worry about those details in further iterations
        var step = it.next();
        final var firstModule = step.module();
        if (firstModule == null) {
            throw new RestconfDocumentedException(
                "First member must use namespace-qualified form, '" + step.identifier() + "' does not",
                ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE);
        }

        var namespace = resolveNamespace(firstModule);
        var qname = step.identifier().bindTo(namespace);

        // We go through more modern APIs here to get this special out of the way quickly
        final var modelContext = databind.modelContext();
        final var optRpc = modelContext.findModuleStatement(namespace).orElseThrow()
            .findSchemaTreeNode(RpcEffectiveStatement.class, qname);
        if (optRpc.isPresent()) {
            final var rpc = optRpc.orElseThrow();

            // We have found an RPC match,
            if (it.hasNext()) {
                throw new RestconfDocumentedException("First step in the path resolves to RPC '" + qname + "' and "
                    + "therefore it must be the only step present", ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE);
            }
            if (step instanceof ListInstance) {
                throw new RestconfDocumentedException("First step in the path resolves to RPC '" + qname + "' and "
                    + "therefore it must not contain key values", ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE);
            }

            final var stack = SchemaInferenceStack.of(modelContext);
            final var stmt = stack.enterSchemaTree(rpc.argument());
            verify(rpc.equals(stmt), "Expecting %s, inferred %s", rpc, stmt);
            return new Rpc(stack.toInference(), rpc);
        }

        final var stack = SchemaInferenceStack.of(modelContext);
        final var path = new ArrayList<PathArgument>();
        DataSchemaContext parentNode = databind.schemaTree().getRoot();
        while (true) {
            final var parentSchema = parentNode.dataSchemaNode();
            if (parentSchema instanceof ActionNodeContainer actionParent) {
                final var optAction = actionParent.findAction(qname);
                if (optAction.isPresent()) {
                    final var action = optAction.orElseThrow();

                    if (it.hasNext()) {
                        throw new RestconfDocumentedException("Request path resolves to action '" + qname + "' and "
                            + "therefore it must not continue past it", ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE);
                    }
                    if (step instanceof ListInstance) {
                        throw new RestconfDocumentedException("Request path resolves to action '" + qname + "' and "
                            + "therefore it must not contain key values",
                            ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE);
                    }

                    final var stmt = stack.enterSchemaTree(qname);
                    final var actionStmt = action.asEffectiveStatement();
                    verify(actionStmt.equals(stmt), "Expecting %s, inferred %s", actionStmt, stmt);

                    return new Action(stack.toInference(), YangInstanceIdentifier.of(path), actionStmt);
                }
            }

            // Resolve the child step with respect to data schema tree
            final var found = parentNode instanceof DataSchemaContext.Composite composite
                ? composite.enterChild(stack, qname) : null;
            if (found == null) {
                throw new RestconfDocumentedException("Schema for '" + qname + "' not found",
                    ErrorType.PROTOCOL, ErrorTag.DATA_MISSING);
            }

            // Now add all mixins encountered to the path
            var childNode = found;
            while (childNode instanceof PathMixin currentMixin) {
                path.add(currentMixin.mixinPathStep());
                childNode = verifyNotNull(currentMixin.enterChild(stack, qname),
                    "Mixin %s is missing child for %s while resolving %s", childNode, qname, found);
            }

            final PathArgument pathArg;
            if (step instanceof ListInstance listStep) {
                final var values = listStep.keyValues();
                final var schema = childNode.dataSchemaNode();
                if (schema instanceof ListSchemaNode listSchema) {
                    pathArg = prepareNodeWithPredicates(stack, qname, listSchema, values);
                } else if (schema instanceof LeafListSchemaNode leafListSchema) {
                    if (values.size() != 1) {
                        throw new RestconfDocumentedException("Entry '" + qname + "' requires one value predicate.",
                            ErrorType.PROTOCOL, ErrorTag.BAD_ATTRIBUTE);
                    }
                    pathArg = new NodeWithValue<>(qname, parserJsonValue(stack, leafListSchema, values.get(0)));
                } else {
                    throw new RestconfDocumentedException(
                        "Entry '" + qname + "' does not take a key or value predicate.",
                        ErrorType.PROTOCOL, ErrorTag.MISSING_ATTRIBUTE);
                }
            } else {
                if (childNode.dataSchemaNode() instanceof ListSchemaNode list && !list.getKeyDefinition().isEmpty()) {
                    throw new RestconfDocumentedException(
                        "Entry '" + qname + "' requires key or value predicate to be present.",
                        ErrorType.PROTOCOL, ErrorTag.MISSING_ATTRIBUTE);
                }
                pathArg = childNode.getPathStep();
            }

            path.add(pathArg);

            if (!it.hasNext()) {
                return new Data(stack.toInference(), YangInstanceIdentifier.of(path), childNode);
            }

            parentNode = childNode;
            step = it.next();
            final var module = step.module();
            if (module != null) {
                namespace = resolveNamespace(module);
            }

            qname = step.identifier().bindTo(namespace);
        }
    }

    public @NonNull Data normalizeDataPath(final ApiPath apiPath) {
        final var path = normalizePath(apiPath);
        if (path instanceof Data dataPath) {
            return dataPath;
        }
        throw new RestconfDocumentedException("Point '" + apiPath + "' resolves to non-data " + path,
            ErrorType.PROTOCOL, ErrorTag.DATA_MISSING);
    }

    @Override
    public PathArgument normalizePoint(final ApiPath value) {
        final var path = normalizePath(value);
        if (path instanceof Data dataPath) {
            final var lastArg = dataPath.instance().getLastPathArgument();
            if (lastArg != null) {
                return lastArg;
            }
            throw new IllegalArgumentException("Point '" + value + "' resolves to an empty path");
        }
        throw new IllegalArgumentException("Point '" + value + "' resolves to non-data " + path);
    }

    public Path.@NonNull Rpc normalizeRpcPath(final ApiPath apiPath) {
        final var steps = apiPath.steps();
        return switch (steps.size()) {
            case 0 -> throw new RestconfDocumentedException("RPC name must be present", ErrorType.PROTOCOL,
                ErrorTag.DATA_MISSING);
            case 1 -> normalizeRpcPath(steps.get(0));
            default -> throw new RestconfDocumentedException(apiPath + " does not refer to an RPC", ErrorType.PROTOCOL,
                ErrorTag.DATA_MISSING);
        };
    }

    public Path.@NonNull Rpc normalizeRpcPath(final ApiPath.Step step) {
        final var firstModule = step.module();
        if (firstModule == null) {
            throw new RestconfDocumentedException(
                "First member must use namespace-qualified form, '" + step.identifier() + "' does not",
                ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE);
        }

        final var namespace = resolveNamespace(firstModule);
        final var qname = step.identifier().bindTo(namespace);
        final var stack = SchemaInferenceStack.of(databind.modelContext());
        final SchemaTreeEffectiveStatement<?> stmt;
        try {
            stmt = stack.enterSchemaTree(qname);
        } catch (IllegalArgumentException e) {
            throw new RestconfDocumentedException(qname + " does not refer to an RPC", ErrorType.PROTOCOL,
                ErrorTag.DATA_MISSING, e);
        }
        if (stmt instanceof RpcEffectiveStatement rpc) {
            return new Rpc(stack.toInference(), rpc);
        }
        throw new RestconfDocumentedException(qname + " does not refer to an RPC", ErrorType.PROTOCOL,
            ErrorTag.DATA_MISSING);
    }

    public @NonNull InstanceReference normalizeDataOrActionPath(final ApiPath apiPath) {
        // FIXME: optimize this
        final var path = normalizePath(apiPath);
        if (path instanceof Data dataPath) {
            return dataPath;
        }
        if (path instanceof Action actionPath) {
            return actionPath;
        }
        throw new RestconfDocumentedException("Unexpected path " + path, ErrorType.PROTOCOL, ErrorTag.DATA_MISSING);
    }

    /**
     * Return the canonical {@link ApiPath} for specified {@link YangInstanceIdentifier}.
     *
     * @param path {@link YangInstanceIdentifier} to canonicalize
     * @return An {@link ApiPath}
     */
    public @NonNull ApiPath canonicalize(final YangInstanceIdentifier path) {
        final var it = path.getPathArguments().iterator();
        if (!it.hasNext()) {
            return ApiPath.empty();
        }

        final var stack = SchemaInferenceStack.of(databind.modelContext());
        final var builder = ImmutableList.<Step>builder();
        DataSchemaContext currentContext = databind.schemaTree().getRoot();
        QNameModule parentModule = null;
        do {
            final var arg = it.next();

            // get module of the parent
            if (!(currentContext instanceof PathMixin)) {
                parentModule = currentContext.dataSchemaNode().getQName().getModule();
            }

            final var childContext = currentContext instanceof DataSchemaContext.Composite composite
                ? composite.enterChild(stack, arg) : null;
            if (childContext == null) {
                throw new RestconfDocumentedException(
                    "Invalid input '%s': schema for argument '%s' (after '%s') not found".formatted(path, arg,
                        ApiPath.of(builder.build())), ErrorType.APPLICATION, ErrorTag.UNKNOWN_ELEMENT);
            }

            currentContext = childContext;
            if (childContext instanceof PathMixin) {
                continue;
            }

            // append namespace before every node which is defined in other module than its parent
            // condition is satisfied also for the first path argument
            final var nodeType = arg.getNodeType();
            final var prefix = nodeType.getModule().equals(parentModule) ? null : resolvePrefix(nodeType);

            final Step step;
            if (arg instanceof NodeIdentifier) {
                step = new ApiIdentifier(prefix, nodeType.getLocalName());
            } else if (arg instanceof NodeWithValue<?> withValue) {
                step = canonicalize(stack, currentContext.dataSchemaNode(), prefix, nodeType.getLocalName(),
                    withValue.getValue());
            } else if (arg instanceof NodeIdentifierWithPredicates withPredicates) {
                step = canonicalize(stack, currentContext, prefix, nodeType.getLocalName(), withPredicates.asMap());
            } else {
                throw new VerifyException("Unhandled " + arg);
            }
            builder.add(step);
        } while (it.hasNext());

        return new ApiPath(builder.build());
    }

    private @NonNull ListInstance canonicalize(final SchemaInferenceStack stack, final DataSchemaContext context,
            final String prefix, final String localName, final Map<QName, Object> keyValues) {
        if (!(context instanceof Composite composite) || !(composite.dataSchemaNode() instanceof ListSchemaNode list)) {
            throw new VerifyException("Unexpected context " + context);
        }
        final var keys = list.getKeyDefinition();
        if (keys.isEmpty()) {
            throw new VerifyException("Schema " + list + " does not have any keys");
        }

        final var builder = ImmutableList.<String>builderWithExpectedSize(keys.size());
        for (var key : keys) {
            final var value = verifyNotNull(keyValues.get(key), "Missing value for %s", key);
            final var tmpStack = stack.copy();
            final var keyContext = verifyNotNull(composite.enterChild(tmpStack, key), "Failed to find key %s in %s",
                key, composite);
            if (!(keyContext.dataSchemaNode() instanceof LeafSchemaNode leaf)) {
                throw new VerifyException("Key " + key + " maps to non-leaf context " + keyContext);
            }
            builder.add(encodeValue(tmpStack, leaf, value));
        }
        return ListInstance.of(prefix, localName, builder.build());
    }

    private @NonNull ListInstance canonicalize(final SchemaInferenceStack stack, final DataSchemaNode schema,
            final String prefix, final String localName, final Object value) {
        if (!(schema instanceof LeafListSchemaNode leafList)) {
            throw new VerifyException("Unexpected schema " + schema);
        }
        return ListInstance.of(prefix, localName, encodeValue(stack, leafList, value));
    }

    private String encodeValue(final SchemaInferenceStack stack, final TypedDataSchemaNode schema, final Object value) {
        @SuppressWarnings("unchecked")
        final var codec = (JSONCodec<Object>) databind.jsonCodecs().codecFor(schema, stack);
        final var writer = new StringWriter();
        final var jsonWriter = new JsonWriter(writer);
        try {
            codec.writeValue(jsonWriter, value);
            jsonWriter.close();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize '" + value + "'", e);
        }
        return writer.toString();
    }

    private NodeIdentifierWithPredicates prepareNodeWithPredicates(final SchemaInferenceStack stack, final QName qname,
            final @NonNull ListSchemaNode schema, final List<@NonNull String> keyValues) {
        final var keyDef = schema.getKeyDefinition();
        final var keySize = keyDef.size();
        final var varSize = keyValues.size();
        if (keySize != varSize) {
            throw new RestconfDocumentedException(
                "Schema for " + qname + " requires " + keySize + " key values, " + varSize + " supplied",
                ErrorType.PROTOCOL, keySize > varSize ? ErrorTag.MISSING_ATTRIBUTE : ErrorTag.UNKNOWN_ATTRIBUTE);
        }

        final var values = ImmutableMap.<QName, Object>builderWithExpectedSize(keySize);
        final var tmp = stack.copy();
        for (int i = 0; i < keySize; ++i) {
            final QName keyName = keyDef.get(i);
            final var child = schema.getDataChildByName(keyName);
            tmp.enterSchemaTree(keyName);
            values.put(keyName, prepareValueByType(tmp, child, keyValues.get(i)));
            tmp.exit();
        }

        return NodeIdentifierWithPredicates.of(qname, values.build());
    }

    private Object prepareValueByType(final SchemaInferenceStack stack, final DataSchemaNode schemaNode,
            final @NonNull String value) {
        if (schemaNode instanceof TypedDataSchemaNode typedSchema) {
            return parserJsonValue(stack, typedSchema, value);
        }
        throw new VerifyException("Unhandled schema " + schemaNode + " decoding '" + value + "'");
    }

    private Object parserJsonValue(final SchemaInferenceStack stack, final TypedDataSchemaNode schemaNode,
            final String value) {
        // As per https://www.rfc-editor.org/rfc/rfc8040#page-29:
        //            The syntax for
        //            "api-identifier" and "key-value" MUST conform to the JSON identifier
        //            encoding rules in Section 4 of [RFC7951]: The RESTCONF root resource
        //            path is required.  Additional sub-resource identifiers are optional.
        //            The characters in a key value string are constrained, and some
        //            characters need to be percent-encoded, as described in Section 3.5.3.
        try {
            return databind.jsonCodecs().codecFor(schemaNode, stack).parseValue(null, value);
        } catch (IllegalArgumentException e) {
            throw new RestconfDocumentedException("Invalid value '" + value + "' for " + schemaNode.getQName(),
                ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE, e);
        }
    }

    private @NonNull QNameModule resolveNamespace(final String moduleName) {
        final var it = databind.modelContext().findModuleStatements(moduleName).iterator();
        if (it.hasNext()) {
            return it.next().localQNameModule();
        }
        throw new RestconfDocumentedException("Failed to lookup for module with name '" + moduleName + "'.",
            ErrorType.PROTOCOL, ErrorTag.UNKNOWN_ELEMENT);
    }

    /**
     * Create prefix of namespace from {@link QName}.
     *
     * @param qname {@link QName}
     * @return {@link String}
     */
    private @NonNull String resolvePrefix(final QName qname) {
        return databind.modelContext().findModuleStatement(qname.getModule()).orElseThrow().argument().getLocalName();
    }
}
