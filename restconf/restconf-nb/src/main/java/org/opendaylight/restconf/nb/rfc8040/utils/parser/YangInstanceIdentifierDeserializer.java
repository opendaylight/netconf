/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.utils.parser;

import static com.google.common.base.Verify.verify;
import static com.google.common.base.Verify.verifyNotNull;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableMap;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.api.ApiPath.ListInstance;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeWithValue;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.impl.codec.TypeDefinitionAwareCodec;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContext;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContext.PathMixin;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextTree;
import org.opendaylight.yangtools.yang.model.api.ActionNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.LeafListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.api.TypeDefinition;
import org.opendaylight.yangtools.yang.model.api.stmt.IdentityEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.RpcEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.type.IdentityrefTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.InstanceIdentifierTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.LeafrefTypeDefinition;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;

/**
 * Deserializer for {@link String} to {@link YangInstanceIdentifier} for restconf.
 */
public final class YangInstanceIdentifierDeserializer {
    public static final class Result {
        public final @NonNull YangInstanceIdentifier path;
        public final @NonNull SchemaInferenceStack stack;
        public final @NonNull SchemaNode node;

        Result(final EffectiveModelContext context) {
            path = YangInstanceIdentifier.of();
            node = requireNonNull(context);
            stack = SchemaInferenceStack.of(context);
        }

        Result(final EffectiveModelContext context, final QName qname) {
            // Legacy behavior: RPCs do not really have a YangInstanceIdentifier, but the rest of the code expects it
            path = YangInstanceIdentifier.of(qname);
            stack = SchemaInferenceStack.of(context);

            final var stmt = stack.enterSchemaTree(qname);
            verify(stmt instanceof RpcDefinition, "Unexpected statement %s", stmt);
            node = (RpcDefinition) stmt;
        }

        Result(final List<PathArgument> steps, final SchemaInferenceStack stack, final SchemaNode node) {
            path = YangInstanceIdentifier.of(steps);
            this.stack = requireNonNull(stack);
            this.node = requireNonNull(node);
        }
    }

    private final @NonNull EffectiveModelContext schemaContext;
    private final @NonNull ApiPath apiPath;

    private YangInstanceIdentifierDeserializer(final EffectiveModelContext schemaContext, final ApiPath apiPath) {
        this.schemaContext = requireNonNull(schemaContext);
        this.apiPath = requireNonNull(apiPath);
    }

    /**
     * Method to create {@link List} from {@link PathArgument} which are parsing from data by {@link SchemaContext}.
     *
     * @param schemaContext for validate of parsing path arguments
     * @param data path to data, in URL string form
     * @return {@link Iterable} of {@link PathArgument}
     * @throws RestconfDocumentedException the path is not valid
     */
    public static Result create(final EffectiveModelContext schemaContext, final String data) {
        final ApiPath path;
        try {
            path = ApiPath.parse(requireNonNull(data));
        } catch (ParseException e) {
            throw new RestconfDocumentedException("Invalid path '" + data + "' at offset " + e.getErrorOffset(),
                ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE, e);
        }
        return create(schemaContext, path);
    }

    public static Result create(final EffectiveModelContext schemaContext, final ApiPath path) {
        return new YangInstanceIdentifierDeserializer(schemaContext, path).parse();
    }

    // FIXME: NETCONF-818: this method really needs to report an Inference and optionally a YangInstanceIdentifier
    // - we need the inference for discerning the correct context
    // - RPCs do not have a YangInstanceIdentifier
    // - Actions always have a YangInstanceIdentifier, but it points to their parent
    // - we need to discern the cases RPC invocation, Action invocation and data tree access quickly
    //
    // All of this really is an utter mess because we end up calling into this code from various places which,
    // for example, should not allow RPCs to be valid targets
    private Result parse() {
        final var it = apiPath.steps().iterator();
        if (!it.hasNext()) {
            return new Result(schemaContext);
        }

        // First step is somewhat special:
        // - it has to contain a module qualifier
        // - it has to consider RPCs, for which we need SchemaContext
        //
        // We therefore peel that first iteration here and not worry about those details in further iterations
        var step = it.next();
        final var firstModule = RestconfDocumentedException.throwIfNull(step.module(),
            ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE,
            "First member must use namespace-qualified form, '%s' does not", step.identifier());
        var namespace = resolveNamespace(firstModule);
        var qname = step.identifier().bindTo(namespace);

        // We go through more modern APIs here to get this special out of the way quickly
        final var optRpc = schemaContext.findModuleStatement(namespace).orElseThrow()
            .findSchemaTreeNode(RpcEffectiveStatement.class, qname);
        if (optRpc.isPresent()) {
            // We have found an RPC match,
            if (it.hasNext()) {
                throw new RestconfDocumentedException("First step in the path resolves to RPC '" + qname + "' and "
                    + "therefore it must be the only step present", ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE);
            }
            if (step instanceof ListInstance) {
                throw new RestconfDocumentedException("First step in the path resolves to RPC '" + qname + "' and "
                    + "therefore it must not contain key values", ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE);
            }

            return new Result(schemaContext, optRpc.orElseThrow().argument());
        }

        final var stack = SchemaInferenceStack.of(schemaContext);
        final var path = new ArrayList<PathArgument>();
        final SchemaNode node;

        DataSchemaContext parentNode = DataSchemaContextTree.from(schemaContext).getRoot();
        while (true) {
            final var parentSchema = parentNode.dataSchemaNode();
            if (parentSchema instanceof ActionNodeContainer actionParent) {
                final var optAction = actionParent.findAction(qname);
                if (optAction.isPresent()) {
                    if (it.hasNext()) {
                        throw new RestconfDocumentedException("Request path resolves to action '" + qname + "' and "
                            + "therefore it must not continue past it", ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE);
                    }
                    if (step instanceof ListInstance) {
                        throw new RestconfDocumentedException("Request path resolves to action '" + qname + "' and "
                            + "therefore it must not contain key values",
                            ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE);
                    }

                    // Legacy behavior: Action's path should not include its path, but the rest of the code expects it
                    path.add(new NodeIdentifier(qname));
                    stack.enterSchemaTree(qname);
                    node = optAction.orElseThrow();
                    break;
                }
            }

            // Resolve the child step with respect to data schema tree
            final var found = RestconfDocumentedException.throwIfNull(
                parentNode instanceof DataSchemaContext.Composite composite ? composite.enterChild(stack, qname) : null,
                    ErrorType.PROTOCOL, ErrorTag.DATA_MISSING, "Schema for '%s' not found", qname);

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
                RestconfDocumentedException.throwIf(childNode.dataSchemaNode() instanceof ListSchemaNode listChild
                    && !listChild.getKeyDefinition().isEmpty(),
                    ErrorType.PROTOCOL, ErrorTag.MISSING_ATTRIBUTE,
                    "Entry '%s' requires key or value predicate to be present.", qname);
                pathArg = childNode.getPathStep();
            }

            path.add(pathArg);

            if (!it.hasNext()) {
                node = childNode.dataSchemaNode();
                break;
            }

            parentNode = childNode;
            step = it.next();
            final var module = step.module();
            if (module != null) {
                namespace = resolveNamespace(module);
            }

            qname = step.identifier().bindTo(namespace);
        }

        return new Result(path, stack, node);
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

        TypeDefinition<? extends TypeDefinition<?>> typedef;
        if (schemaNode instanceof LeafListSchemaNode) {
            typedef = ((LeafListSchemaNode) schemaNode).getType();
        } else {
            typedef = ((LeafSchemaNode) schemaNode).getType();
        }
        if (typedef instanceof LeafrefTypeDefinition) {
            typedef = stack.resolveLeafref((LeafrefTypeDefinition) typedef);
        }

        if (typedef instanceof IdentityrefTypeDefinition) {
            return toIdentityrefQName(value, schemaNode);
        }

        try {
            if (typedef instanceof InstanceIdentifierTypeDefinition) {
                return new StringModuleInstanceIdentifierCodec(schemaContext).deserialize(value);
            }

            return verifyNotNull(TypeDefinitionAwareCodec.from(typedef),
                "Unhandled type %s decoding %s", typedef, value).deserialize(value);
        } catch (IllegalArgumentException e) {
            throw new RestconfDocumentedException("Invalid value '" + value + "' for " + schemaNode.getQName(),
                ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE, e);
        }
    }

    private NodeWithValue<?> prepareNodeWithValue(final SchemaInferenceStack stack, final QName qname,
            final DataSchemaNode schema, final List<String> keyValues) {
        // TODO: qname should be always equal to schema.getQName(), right?
        return new NodeWithValue<>(qname, prepareValueByType(stack, schema,
            // FIXME: ahem: we probably want to do something differently here
            keyValues.get(0)));
    }

    private QName toIdentityrefQName(final String value, final DataSchemaNode schemaNode) {
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

        return schemaContext.getModuleStatement(namespace)
            .streamEffectiveSubstatements(IdentityEffectiveStatement.class)
            .map(IdentityEffectiveStatement::argument)
            .filter(qname -> localName.equals(qname.getLocalName()))
            .findFirst()
            .orElseThrow(() -> new RestconfDocumentedException(
                "No identity found for '" + localName + "' in namespace " + namespace,
                ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE));
    }

    private @NonNull QNameModule resolveNamespace(final String moduleName) {
        final var modules = schemaContext.findModules(moduleName);
        RestconfDocumentedException.throwIf(modules.isEmpty(), ErrorType.PROTOCOL, ErrorTag.UNKNOWN_ELEMENT,
            "Failed to lookup for module with name '%s'.", moduleName);
        return modules.iterator().next().getQNameModule();
    }
}
