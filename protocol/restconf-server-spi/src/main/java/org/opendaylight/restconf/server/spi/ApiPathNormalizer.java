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
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.databind.DatabindContext;
import org.opendaylight.netconf.databind.DatabindPath;
import org.opendaylight.netconf.databind.DatabindPath.Action;
import org.opendaylight.netconf.databind.DatabindPath.Data;
import org.opendaylight.netconf.databind.DatabindPath.InstanceRequest;
import org.opendaylight.netconf.databind.DatabindPath.Rpc;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.api.ApiPath.ListInstance;
import org.opendaylight.restconf.api.ApiPath.Step;
import org.opendaylight.restconf.server.api.ServerException;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeWithValue;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContext;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContext.PathMixin;
import org.opendaylight.yangtools.yang.model.api.ActionNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.TypedDataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.stmt.RpcEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaTreeEffectiveStatement;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;

/**
 * Utility for normalizing {@link ApiPath}s. An {@link ApiPath} can represent a number of different constructs, as
 * denoted to in the {@link DatabindPath} interface hierarchy.
 *
 * <p>This process is governed by
 * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-3.5.3">RFC8040, section 3.5.3</a>. The URI provides the
 * equivalent of NETCONF XML filter encoding, with data values being escaped RFC7891 strings.
 */
public final class ApiPathNormalizer implements Insert.PointNormalizer {
    private final @NonNull DatabindContext databind;

    public ApiPathNormalizer(final DatabindContext databind) {
        this.databind = requireNonNull(databind);
    }

    public DatabindContext databind() {
        return databind;
    }

    public @NonNull DatabindPath normalizePath(final ApiPath apiPath) throws ServerException {
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
            throw new ServerException(ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE,
                "First member must use namespace-qualified form, '%s' does not", firstStep.identifier());
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
                throw new ServerException(ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE,
                    "First step in the path resolves to RPC '%s' and therefore it must be the only step present",
                    qname);
            }
            if (step instanceof ListInstance) {
                throw new ServerException(ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE,
                    "First step in the path resolves to RPC '%s' and therefore it must not contain key values", qname);
            }

            final var stack = SchemaInferenceStack.of(modelContext);
            final var stmt = stack.enterSchemaTree(rpc.argument());
            verify(rpc.equals(stmt), "Expecting %s, inferred %s", rpc, stmt);
            return new Rpc(databind, stack.toInference(), rpc);
        }

        return normalizeSteps(SchemaInferenceStack.of(modelContext), databind.schemaTree().getRoot(), List.of(),
            namespace, firstStep, it);
    }

    @NonNull DatabindPath normalizeSteps(final SchemaInferenceStack stack, final @NonNull DataSchemaContext rootNode,
            final @NonNull List<PathArgument> pathPrefix, final @NonNull QNameModule firstNamespace,
            final @NonNull Step firstStep, final Iterator<@NonNull Step> it) throws ServerException {
        var parentNode = rootNode;
        var namespace = firstNamespace;
        var step = firstStep;
        var qname = step.identifier().bindTo(namespace);

        final var path = new ArrayList<>(pathPrefix);
        while (true) {
            final var parentSchema = parentNode.dataSchemaNode();
            if (parentSchema instanceof ActionNodeContainer actionParent) {
                final var optAction = actionParent.findAction(qname);
                if (optAction.isPresent()) {
                    final var action = optAction.orElseThrow();

                    if (it.hasNext()) {
                        throw new ServerException(ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE,
                            "Request path resolves to action '%s' and therefore it must not continue past it", qname);
                    }
                    if (step instanceof ListInstance) {
                        throw new ServerException(ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE,
                            "Request path resolves to action '%s' and therefore it must not contain key values", qname);
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
                throw new ServerException(ErrorType.PROTOCOL, ErrorTag.DATA_MISSING, "Schema for '%s' not found",
                    qname);
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
                        throw new ServerException(ErrorType.PROTOCOL, ErrorTag.BAD_ATTRIBUTE,
                            "Entry '%s' requires one value predicate.", qname);
                    }
                    pathArg = new NodeWithValue<>(qname, parserJsonValue(stack, leafListSchema, values.get(0)));
                } else {
                    throw new ServerException(ErrorType.PROTOCOL, ErrorTag.MISSING_ATTRIBUTE,
                        "Entry '%s' does not take a key or value predicate.", qname);
                }
            } else {
                if (childNode.dataSchemaNode() instanceof ListSchemaNode list && !list.getKeyDefinition().isEmpty()) {
                    throw new ServerException(ErrorType.PROTOCOL, ErrorTag.MISSING_ATTRIBUTE,
                        "Entry '%s' requires key or value predicate to be present.", qname);
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

    public @NonNull Data normalizeDataPath(final ApiPath apiPath) throws ServerException {
        final var path = normalizePath(apiPath);
        if (path instanceof Data dataPath) {
            return dataPath;
        }
        throw new ServerException(ErrorType.PROTOCOL, ErrorTag.DATA_MISSING, "Point '%s' resolves to non-data %s",
            apiPath, path);
    }

    @Override
    public PathArgument normalizePoint(final ApiPath value) throws ServerException {
        final var path = normalizePath(value);
        if (path instanceof Data dataPath) {
            final var lastArg = dataPath.instance().getLastPathArgument();
            if (lastArg != null) {
                return lastArg;
            }
            throw new ServerException(ErrorType.PROTOCOL,  ErrorTag.DATA_MISSING,
                "Point '%s' resolves to an empty path", value);
        }
        throw new ServerException(ErrorType.PROTOCOL,  ErrorTag.DATA_MISSING, "Point '%s' resolves to non-data %s",
            value, path);
    }

    public @NonNull Rpc normalizeRpcPath(final ApiPath apiPath) throws ServerException {
        final var steps = apiPath.steps();
        return switch (steps.size()) {
            case 0 -> throw new ServerException(ErrorType.PROTOCOL, ErrorTag.DATA_MISSING, "RPC name must be present");
            case 1 -> normalizeRpcPath(steps.get(0));
            default -> throw new ServerException(ErrorType.PROTOCOL, ErrorTag.DATA_MISSING,
                "%s does not refer to an RPC", apiPath);
        };
    }

    public @NonNull Rpc normalizeRpcPath(final ApiPath.Step step) throws ServerException {
        final var firstModule = step.module();
        if (firstModule == null) {
            throw new ServerException(ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE,
                "First member must use namespace-qualified form, '%s' does not", step.identifier());
        }

        final var namespace = resolveNamespace(firstModule);
        final var qname = step.identifier().bindTo(namespace);
        final var stack = SchemaInferenceStack.of(databind.modelContext());
        final SchemaTreeEffectiveStatement<?> stmt;
        try {
            stmt = stack.enterSchemaTree(qname);
        } catch (IllegalArgumentException e) {
            throw new ServerException(ErrorType.PROTOCOL, ErrorTag.DATA_MISSING, qname + " does not refer to an RPC",
                e);
        }
        if (stmt instanceof RpcEffectiveStatement rpc) {
            return new Rpc(databind, stack.toInference(), rpc);
        }
        throw new ServerException(ErrorType.PROTOCOL, ErrorTag.DATA_MISSING, "%s does not refer to an RPC", qname);
    }

    public @NonNull InstanceRequest normalizeDataOrActionPath(final ApiPath apiPath) throws ServerException {
        // FIXME: optimize this
        final var path = normalizePath(apiPath);
        if (path instanceof Data dataPath) {
            return dataPath;
        }
        if (path instanceof Action actionPath) {
            return actionPath;
        }
        throw new ServerException(ErrorType.PROTOCOL, ErrorTag.DATA_MISSING, "Unexpected path %s", path);
    }

    private NodeIdentifierWithPredicates prepareNodeWithPredicates(final SchemaInferenceStack stack, final QName qname,
            final @NonNull ListSchemaNode schema, final List<@NonNull String> keyValues) throws ServerException {
        final var keyDef = schema.getKeyDefinition();
        final var keySize = keyDef.size();
        final var varSize = keyValues.size();
        if (keySize != varSize) {
            throw new ServerException(ErrorType.PROTOCOL,
                keySize > varSize ? ErrorTag.MISSING_ATTRIBUTE : ErrorTag.UNKNOWN_ATTRIBUTE,
                "Schema for %s requires %s key values, %s supplied", qname, keySize, varSize);
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
            final @NonNull String value) throws ServerException {
        if (schemaNode instanceof TypedDataSchemaNode typedSchema) {
            return parserJsonValue(stack, typedSchema, value);
        }
        throw new VerifyException("Unhandled schema " + schemaNode + " decoding '" + value + "'");
    }

    private Object parserJsonValue(final SchemaInferenceStack stack, final TypedDataSchemaNode schemaNode,
            final String value) throws ServerException {
        // As per https://www.rfc-editor.org/rfc/rfc8040#page-29:
        //            The syntax for
        //            "api-identifier" and "key-value" MUST conform to the JSON identifier
        //            encoding rules in Section 4 of [RFC7951]: The RESTCONF root resource
        //            path is required.  Additional sub-resource identifiers are optional.
        //            The characters in a key value string are constrained, and some
        //            characters need to be percent-encoded, as described in Section 3.5.3.
        try {
            return databind.jsonCodecs().codecFor(schemaNode, stack).parseValue(value);
        } catch (IllegalArgumentException e) {
            throw new ServerException(ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE,
                "Invalid value '" + value + "' for " + schemaNode.getQName(), e);
        }
    }

    private @NonNull QNameModule resolveNamespace(final String moduleName) throws ServerException {
        final var it = databind.modelContext().findModuleStatements(moduleName).iterator();
        if (it.hasNext()) {
            return it.next().localQNameModule();
        }
        throw new ServerException(ErrorType.PROTOCOL, ErrorTag.UNKNOWN_ELEMENT,
            "Failed to lookup for module with name '%s'.", moduleName);
    }
}
