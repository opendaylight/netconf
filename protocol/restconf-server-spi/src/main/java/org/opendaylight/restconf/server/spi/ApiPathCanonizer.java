/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import static java.util.Objects.requireNonNull;

import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableList;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.databind.DatabindContext;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.api.ApiPath.ApiIdentifier;
import org.opendaylight.restconf.api.ApiPath.ListInstance;
import org.opendaylight.restconf.api.ApiPath.Step;
import org.opendaylight.restconf.server.api.ServerException;
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
import org.opendaylight.yangtools.yang.model.api.LeafListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.TypedDataSchemaNode;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;

/**
 * Utility class for turning {@link YangInstanceIdentifier}s into {@link ApiPath}s via
 * {@link #dataToApiPath(YangInstanceIdentifier)}.
 */
public final class ApiPathCanonizer {
    private final @NonNull DatabindContext databind;

    public ApiPathCanonizer(final DatabindContext databind) {
        this.databind = requireNonNull(databind);
    }

    /**
     * Return the canonical {@link ApiPath} for specified {@link YangInstanceIdentifier}.
     *
     * @param path {@link YangInstanceIdentifier} to canonicalize
     * @return An {@link ApiPath}
     * @throws ServerException if an error occurs
     */
    public @NonNull ApiPath dataToApiPath(final YangInstanceIdentifier path) throws ServerException {
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
                throw new ServerException(ErrorType.APPLICATION, ErrorTag.UNKNOWN_ELEMENT,
                    "Invalid input '%s': schema for argument '%s' (after '%s') not found", path, arg,
                    ApiPath.of(builder.build()));
            }

            context = childContext;

            // only output PathArguments which are not inherent to YangInstanceIdentifier structure
            if (!(childContext instanceof PathMixin)) {
                builder.add(argToStep(arg, parentModule, stack, context));
            }
        } while (it.hasNext());

        return new ApiPath(builder.build());
    }

    private @NonNull Step argToStep(final PathArgument arg, final QNameModule prevNamespace,
            final SchemaInferenceStack stack, final DataSchemaContext context) throws ServerException {
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
                throw new ServerException(ErrorType.APPLICATION, ErrorTag.INVALID_VALUE,
                    "Argument '%s' does not map to a leaf-list, but %s", arg, schema);
            }
            return ListInstance.of(module, identifier, valueToString(stack, leafList, withValue.getValue()));
        }

        // The only remaining case is NodeIdentifierWrithPredicates, verify that instead of an explicit cast
        if (!(arg instanceof NodeIdentifierWithPredicates withPredicates)) {
            throw new VerifyException("Unhandled " + arg);
        }
        // A NodeIdentifierWithPredicates adresses a MapEntryNode and maps to a ListInstance with one or more values:
        // 1) schema has to be a ListSchemaNode
        if (!(schema instanceof ListSchemaNode list)) {
            throw new ServerException(ErrorType.APPLICATION, ErrorTag.INVALID_VALUE,
                "Argument '%s' does not map to a list, but %s", arg, schema);
        }
        // 2) the key definition must be non-empty
        final var keyDef = list.getKeyDefinition();
        final var size = keyDef.size();
        if (size == 0) {
            throw new ServerException(ErrorType.APPLICATION, ErrorTag.INVALID_VALUE,
                "Argument '%s' maps a list without any keys %s", arg, schema);
        }
        // 3) the number of predicates has to match the number of keys
        if (size != withPredicates.size()) {
            throw new ServerException(ErrorType.APPLICATION, ErrorTag.INVALID_VALUE,
                "Argument '%s' does not match required keys %s", arg, keyDef);
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
                throw new ServerException(ErrorType.APPLICATION, ErrorTag.INVALID_VALUE,
                    "Argument '%s' is missing predicate for %s", arg, key);
            }

            final var tmpStack = stack.copy();
            final var keyContext = composite.enterChild(tmpStack, key);
            if (keyContext == null) {
                throw new VerifyException("Failed to find key " + key + " in " + composite);
            }
            if (!(keyContext.dataSchemaNode() instanceof LeafSchemaNode leaf)) {
                throw new VerifyException("Key " + key + " maps to non-leaf context " + keyContext);
            }
            builder.add(valueToString(tmpStack, leaf, value));
        }
        return ListInstance.of(module, identifier, builder.build());
    }

    private <T> @NonNull String valueToString(final SchemaInferenceStack stack, final TypedDataSchemaNode schema,
            final T value) {
        @SuppressWarnings("unchecked")
        final var codec = (JSONCodec<T>) databind.jsonCodecs().codecFor(schema, stack);
        return codec.unparseValue(value).rawString();
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
