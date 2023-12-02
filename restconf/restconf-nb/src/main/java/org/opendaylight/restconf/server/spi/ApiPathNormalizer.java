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

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.api.ApiPath.ListInstance;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.Insert.PointNormalizer;
import org.opendaylight.restconf.server.api.DatabindContext;
import org.opendaylight.restconf.server.spi.ApiPathNormalizer.OperationPath.Rpc;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeWithValue;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.impl.codec.TypeDefinitionAwareCodec;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContext;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContext.PathMixin;
import org.opendaylight.yangtools.yang.model.api.ActionNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.LeafListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.TypeDefinition;
import org.opendaylight.yangtools.yang.model.api.stmt.ActionEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.IdentityEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.InputEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.RpcEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaTreeEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.type.IdentityrefTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.InstanceIdentifierTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.LeafrefTypeDefinition;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack.Inference;

/**
 * Deserializer for {@link String} to {@link YangInstanceIdentifier} for restconf.
 */
public final class ApiPathNormalizer implements PointNormalizer {
    @NonNullByDefault
    public sealed interface Path {

        Inference inference();
    }

    @NonNullByDefault
    public sealed interface InstanceReference extends Path {

        YangInstanceIdentifier instance();
    }

    @NonNullByDefault
    public record DataPath(Inference inference, YangInstanceIdentifier instance, DataSchemaContext schema)
            implements InstanceReference {
        public DataPath {
            requireNonNull(inference);
            requireNonNull(instance);
            requireNonNull(schema);
        }
    }

    @NonNullByDefault
    public sealed interface OperationPath extends Path {

        InputEffectiveStatement inputStatement();

        record Action(Inference inference, YangInstanceIdentifier instance, ActionEffectiveStatement action)
                implements OperationPath, InstanceReference {
            public Action {
                requireNonNull(inference);
                requireNonNull(action);
                requireNonNull(instance);
            }

            @Override
            public InputEffectiveStatement inputStatement() {
                return action.input();
            }
        }

        record Rpc(Inference inference, RpcEffectiveStatement rpc) implements OperationPath {
            public Rpc {
                requireNonNull(inference);
                requireNonNull(rpc);
            }

            @Override
            public InputEffectiveStatement inputStatement() {
                return rpc.input();
            }
        }
    }

    private final @NonNull ApiPathInstanceIdentifierCodec instanceIdentifierCodec;
    private final @NonNull EffectiveModelContext modelContext;
    private final @NonNull DatabindContext databind;

    public ApiPathNormalizer(final DatabindContext databind) {
        this.databind = requireNonNull(databind);
        modelContext = databind.modelContext();
        instanceIdentifierCodec = new ApiPathInstanceIdentifierCodec(databind);
    }

    public @NonNull Path normalizePath(final ApiPath apiPath) throws RestconfDocumentedException {
        final var it = apiPath.steps().iterator();
        if (!it.hasNext()) {
            return new DataPath(Inference.ofDataTreePath(modelContext), YangInstanceIdentifier.of(),
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
            return new OperationPath.Rpc(stack.toInference(), rpc);
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

                    return new OperationPath.Action(stack.toInference(), YangInstanceIdentifier.of(path), actionStmt);
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
                pathArg = schema instanceof ListSchemaNode listSchema
                    ? prepareNodeWithPredicates(stack, qname, listSchema, values)
                        : prepareNodeWithValue(stack, qname, schema, values);
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
                return new DataPath(stack.toInference(), YangInstanceIdentifier.of(path), childNode);
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

    public @NonNull DataPath normalizeDataPath(final ApiPath apiPath) throws RestconfDocumentedException {
        final var path = normalizePath(apiPath);
        if (path instanceof DataPath dataPath) {
            return dataPath;
        }
        throw new RestconfDocumentedException("Point '" + apiPath + "' resolves to non-data " + path,
            ErrorType.PROTOCOL, ErrorTag.DATA_MISSING);
    }

    @Override
    public PathArgument normalizePoint(final ApiPath value) throws RestconfDocumentedException {
        final var path = normalizePath(value);
        if (path instanceof DataPath dataPath) {
            final var lastArg = dataPath.instance().getLastPathArgument();
            if (lastArg != null) {
                return lastArg;
            }
            throw new IllegalArgumentException("Point '" + value + "' resolves to an empty path");
        }
        throw new IllegalArgumentException("Point '" + value + "' resolves to non-data " + path);
    }

    public @NonNull Rpc normalizeRpcPath(final ApiPath apiPath) throws RestconfDocumentedException {
        final var steps = apiPath.steps();
        return switch (steps.size()) {
            case 0 -> throw new RestconfDocumentedException("RPC name must be present", ErrorType.PROTOCOL,
                ErrorTag.DATA_MISSING);
            case 1 -> normalizeRpcPath(steps.get(0));
            default -> throw new RestconfDocumentedException(apiPath + " does not refer to an RPC", ErrorType.PROTOCOL,
                ErrorTag.DATA_MISSING);
        };
    }

    public @NonNull Rpc normalizeRpcPath(final ApiPath.Step step) throws RestconfDocumentedException {
        final var firstModule = step.module();
        if (firstModule == null) {
            throw new RestconfDocumentedException(
                "First member must use namespace-qualified form, '" + step.identifier() + "' does not",
                ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE);
        }

        final var namespace = resolveNamespace(firstModule);
        final var qname = step.identifier().bindTo(namespace);
        final var stack = SchemaInferenceStack.of(modelContext);
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

    public @NonNull InstanceReference normalizeDataOrActionPath(final ApiPath apiPath)
            throws RestconfDocumentedException {
        // FIXME: optimize this
        final var path = normalizePath(apiPath);
        if (path instanceof DataPath dataPath) {
            return dataPath;
        }
        if (path instanceof OperationPath.Action actionPath) {
            return actionPath;
        }
        throw new RestconfDocumentedException("Unexpected path " + path, ErrorType.PROTOCOL, ErrorTag.DATA_MISSING);
    }

    private NodeIdentifierWithPredicates prepareNodeWithPredicates(final SchemaInferenceStack stack, final QName qname,
            final @NonNull ListSchemaNode schema, final List<@NonNull String> keyValues)
                throws RestconfDocumentedException {
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
            final @NonNull String value) throws RestconfDocumentedException {

        TypeDefinition<? extends TypeDefinition<?>> typedef;
        if (schemaNode instanceof LeafListSchemaNode leafList) {
            typedef = leafList.getType();
        } else {
            typedef = ((LeafSchemaNode) schemaNode).getType();
        }
        if (typedef instanceof LeafrefTypeDefinition leafref) {
            typedef = stack.resolveLeafref(leafref);
        }

        if (typedef instanceof IdentityrefTypeDefinition) {
            return toIdentityrefQName(value, schemaNode);
        }

        try {
            if (typedef instanceof InstanceIdentifierTypeDefinition) {
                return instanceIdentifierCodec.deserialize(value);
            }

            return verifyNotNull(TypeDefinitionAwareCodec.from(typedef),
                "Unhandled type %s decoding %s", typedef, value).deserialize(value);
        } catch (IllegalArgumentException e) {
            throw new RestconfDocumentedException("Invalid value '" + value + "' for " + schemaNode.getQName(),
                ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE, e);
        }
    }

    private NodeWithValue<?> prepareNodeWithValue(final SchemaInferenceStack stack, final QName qname,
            final DataSchemaNode schema, final List<String> keyValues) throws RestconfDocumentedException {
        // TODO: qname should be always equal to schema.getQName(), right?
        return new NodeWithValue<>(qname, prepareValueByType(stack, schema,
            // FIXME: ahem: we probably want to do something differently here
            keyValues.get(0)));
    }

    private QName toIdentityrefQName(final String value, final DataSchemaNode schemaNode)
            throws RestconfDocumentedException {
        final QNameModule namespace;
        final String localName;
        final int firstColon = value.indexOf(':');
        if (firstColon != -1) {
            namespace = resolveNamespace(value.substring(0, firstColon));
            localName = value.substring(firstColon + 1);
        } else {
            namespace = schemaNode.getQName().getModule();
            localName = value;
        }

        return modelContext.getModuleStatement(namespace)
            .streamEffectiveSubstatements(IdentityEffectiveStatement.class)
            .map(IdentityEffectiveStatement::argument)
            .filter(qname -> localName.equals(qname.getLocalName()))
            .findFirst()
            .orElseThrow(() -> new RestconfDocumentedException(
                "No identity found for '" + localName + "' in namespace " + namespace,
                ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE));
    }

    private @NonNull QNameModule resolveNamespace(final String moduleName) throws RestconfDocumentedException {
        final var it = modelContext.findModuleStatements(moduleName).iterator();
        if (it.hasNext()) {
            return it.next().localQNameModule();
        }
        throw new RestconfDocumentedException("Failed to lookup for module with name '" + moduleName + "'.",
            ErrorType.PROTOCOL, ErrorTag.UNKNOWN_ELEMENT);
    }
}
