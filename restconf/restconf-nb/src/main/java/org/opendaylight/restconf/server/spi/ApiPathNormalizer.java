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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.api.ApiPath.ApiIdentifier;
import org.opendaylight.restconf.api.ApiPath.ListInstance;
import org.opendaylight.restconf.api.ApiPath.Step;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.Insert.PointNormalizer;
import org.opendaylight.restconf.server.api.DatabindContext;
import org.opendaylight.restconf.server.api.DatabindPath;
import org.opendaylight.restconf.server.api.DatabindPath.Action;
import org.opendaylight.restconf.server.api.DatabindPath.Data;
import org.opendaylight.restconf.server.api.DatabindPath.InstanceReference;
import org.opendaylight.restconf.server.api.DatabindPath.Rpc;
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
import org.opendaylight.yangtools.yang.model.api.LeafListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.TypedDataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.stmt.RpcEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaTreeEffectiveStatement;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;

/**
 * Utility for normalizing {@link ApiPath}s. An {@link ApiPath} can represent a number of different constructs, as
 * denoted to in the {@link DatabindPath} interface hierarchy.
 *
 * <p>
 * This process is governed by
 * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-3.5.3">RFC8040, section 3.5.3</a>. The URI provides the
 * equivalent of NETCONF XML filter encoding, with data values being escaped RFC7891 strings.
 */
public final class ApiPathNormalizer implements PointNormalizer {
    private final @NonNull DatabindContext databind;

    public ApiPathNormalizer(final DatabindContext databind) {
        this.databind = requireNonNull(databind);
    }

    public @NonNull DatabindPath normalizePath(final ApiPath apiPath) {
        final var it = apiPath.steps().iterator();
        if (!it.hasNext()) {
            return new Data(databind);
        }

        // First step is somewhat special:
        // - it has to contain a module qualifier
        // - it has to consider RPCs, for which we need SchemaContext
        //
        // We therefore peel that first iteration here and not worry about those details in further iterations
        final var firstStep = it.next();
        final var firstModule = firstStep.module();
        if (firstModule == null) {
            throw new RestconfDocumentedException(
                "First member must use namespace-qualified form, '" + firstStep.identifier() + "' does not",
                ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE);
        }

        var namespace = resolveNamespace(firstModule);
        var step = firstStep;
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
            return new Rpc(databind, stack.toInference(), rpc);
        }

        return normalizeSteps(SchemaInferenceStack.of(modelContext), databind.schemaTree().getRoot(), List.of(),
            namespace, firstStep, it);
    }

    private @NonNull DatabindPath normalizeSteps(final SchemaInferenceStack stack,
            final @NonNull DataSchemaContext rootNode, final @NonNull List<PathArgument> pathPrefix,
            final @NonNull QNameModule firstNamespace, final @NonNull Step firstStep,
            final Iterator<@NonNull Step> it) {
        var parentNode = rootNode;
        var namespace = firstNamespace;
        var step = firstStep;
        var qname = step.identifier().bindTo(namespace);

        final var path = new ArrayList<PathArgument>();
        path.addAll(pathPrefix);

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

                    return new Action(databind, stack.toInference(), YangInstanceIdentifier.of(path), actionStmt);
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
                return new Data(databind, stack.toInference(), YangInstanceIdentifier.of(path), childNode);
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

    public static @NonNull Data normalizeSubResource(final Data resource, final ApiPath subResource) {
        // If subResource is empty just return the resource
        final var steps = subResource.steps();
        if (steps.isEmpty()) {
            return requireNonNull(resource);
        }

        final var normalizer = new ApiPathNormalizer(resource.databind());
        final var urlPath = resource.instance();
        if (urlPath.isEmpty()) {
            // URL indicates the datastore resource, let's just normalize targetPath
            return normalizer.normalizeDataPath(subResource);
        }

        // Defer to normalizePath(), faking things a bit. Then check the result.
        final var it = steps.iterator();
        final var path = normalizer.normalizeSteps(resource.inference().toSchemaInferenceStack(), resource.schema(),
            urlPath.getPathArguments(), urlPath.getLastPathArgument().getNodeType().getModule(), it.next(), it);
        if (path instanceof Data dataPath) {
            return dataPath;
        }
        throw new RestconfDocumentedException("Sub-resource '" + subResource + "' resolves to non-data " + path,
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

    public @NonNull Rpc normalizeRpcPath(final ApiPath apiPath) {
        final var steps = apiPath.steps();
        return switch (steps.size()) {
            case 0 -> throw new RestconfDocumentedException("RPC name must be present", ErrorType.PROTOCOL,
                ErrorTag.DATA_MISSING);
            case 1 -> normalizeRpcPath(steps.get(0));
            default -> throw new RestconfDocumentedException(apiPath + " does not refer to an RPC", ErrorType.PROTOCOL,
                ErrorTag.DATA_MISSING);
        };
    }

    public @NonNull Rpc normalizeRpcPath(final ApiPath.Step step) {
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
            return new Rpc(databind, stack.toInference(), rpc);
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
        DataSchemaContext context = databind.schemaTree().getRoot();
        QNameModule parentModule = null;
        do {
            final var arg = it.next();

            // get module of the parent
            if (!(context instanceof PathMixin)) {
                parentModule = context.dataSchemaNode().getQName().getModule();
            }

            final var childContext = context instanceof Composite composite ? composite.enterChild(stack, arg) : null;
            if (childContext == null) {
                throw new RestconfDocumentedException(
                    "Invalid input '%s': schema for argument '%s' (after '%s') not found".formatted(path, arg,
                        ApiPath.of(builder.build())), ErrorType.APPLICATION, ErrorTag.UNKNOWN_ELEMENT);
            }

            context = childContext;
            if (childContext instanceof PathMixin) {
                // This PathArgument is a mixed-in YangInstanceIdentifier, do not emit anything and continue
                continue;
            }

            builder.add(canonicalize(arg, parentModule, stack, context));
        } while (it.hasNext());

        return new ApiPath(builder.build());
    }

    private @NonNull Step canonicalize(final PathArgument arg, final QNameModule prevNamespace,
            final SchemaInferenceStack stack, final DataSchemaContext context) {
        // append namespace before every node which is defined in other module than its parent
        // condition is satisfied also for the first path argument
        final var nodeType = arg.getNodeType();
        final var module = nodeType.getModule().equals(prevNamespace) ? null : resolvePrefix(nodeType);
        final var identifier = nodeType.unbind();

        // NodeIdentifier maps to an ApiIdentifier
        if (arg instanceof NodeIdentifier) {
            return new ApiIdentifier(module, identifier);
        }

        // NodeWithValue addresses a LeafSetEntryNode and maps to a ListInstance with a single value
        final var schema = context.dataSchemaNode();
        if (arg instanceof NodeWithValue<?> withValue) {
            if (!(schema instanceof LeafListSchemaNode leafList)) {
                throw new RestconfDocumentedException(
                    "Argument '%s' does not map to a leaf-list, but %s".formatted(arg, schema),
                    ErrorType.APPLICATION, ErrorTag.INVALID_VALUE);
            }
            return ListInstance.of(module, identifier, encodeValue(stack, leafList, withValue.getValue()));
        }

        // The only remaining case is NodeIdentifierWrithPredicates, verify that instead of an explicit cast
        if (!(arg instanceof NodeIdentifierWithPredicates withPredicates)) {
            throw new VerifyException("Unhandled " + arg);
        }
        // A NodeIdentifierWithPredicates adresses a MapEntryNode and maps to a ListInstance with one or more values:
        // 1) schema has to be a ListSchemaNode
        if (!(schema instanceof ListSchemaNode list)) {
            throw new RestconfDocumentedException(
                "Argument '%s' does not map to a list, but %s".formatted(arg, schema),
                ErrorType.APPLICATION, ErrorTag.INVALID_VALUE);
        }
        // 2) the key definition must be non-empty
        final var keyDef = list.getKeyDefinition();
        final var size = keyDef.size();
        if (size == 0) {
            throw new RestconfDocumentedException(
                "Argument '%s' maps a list without any keys %s".formatted(arg, schema),
                ErrorType.APPLICATION, ErrorTag.INVALID_VALUE);
        }
        // 3) the number of predicates has to match the number of keys
        if (size != withPredicates.size()) {
            throw new RestconfDocumentedException(
                "Argument '%s' does not match required keys %s".formatted(arg, keyDef),
                ErrorType.APPLICATION, ErrorTag.INVALID_VALUE);
        }

        // ListSchemaNode implies the context is a composite, verify that instead of an unexplained cast when we look
        // up the schema for individual keys
        if (!(context instanceof Composite composite)) {
            throw new VerifyException("Unexpected non-composite " + context + " with " + list);
        }

        final var builder = ImmutableList.<String>builderWithExpectedSize(size);
        for (var key : keyDef) {
            final var value = withPredicates.getValue(key);
            if (value == null) {
                throw new RestconfDocumentedException("Argument '%s' is missing predicate for %s".formatted(arg, key),
                    ErrorType.APPLICATION, ErrorTag.INVALID_VALUE);
            }

            final var tmpStack = stack.copy();
            final var keyContext = composite.enterChild(tmpStack, key);
            if (keyContext == null) {
                throw new VerifyException("Failed to find key " + key + " in " + composite);
            }
            if (!(keyContext.dataSchemaNode() instanceof LeafSchemaNode leaf)) {
                throw new VerifyException("Key " + key + " maps to non-leaf context " + keyContext);
            }
            builder.add(encodeValue(tmpStack, leaf, value));
        }
        return ListInstance.of(module, identifier, builder.build());
    }

    private String encodeValue(final SchemaInferenceStack stack, final TypedDataSchemaNode schema, final Object value) {
        @SuppressWarnings("unchecked")
        final var codec = (JSONCodec<Object>) databind.jsonCodecs().codecFor(schema, stack);
        try (var jsonWriter = new HackJsonWriter()) {
            codec.writeValue(jsonWriter, value);
            return jsonWriter.acquireCaptured().rawString();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize '" + value + "'", e);
        }
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
