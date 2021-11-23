/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.utils.parser;

import static com.google.common.base.Verify.verifyNotNull;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.util.RestUtil;
import org.opendaylight.restconf.nb.rfc8040.ApiPath;
import org.opendaylight.restconf.nb.rfc8040.ApiPath.ListInstance;
import org.opendaylight.restconf.nb.rfc8040.ApiPath.Step;
import org.opendaylight.restconf.nb.rfc8040.codecs.RestCodec;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeWithValue;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextNode;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextTree;
import org.opendaylight.yangtools.yang.model.api.ActionDefinition;
import org.opendaylight.yangtools.yang.model.api.ActionNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.LeafListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.TypeDefinition;
import org.opendaylight.yangtools.yang.model.api.stmt.IdentityEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.type.IdentityrefTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.LeafrefTypeDefinition;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;

/**
 * Deserializer for {@link String} to {@link YangInstanceIdentifier} for restconf.
 */
public final class YangInstanceIdentifierDeserializer {
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
    public static List<PathArgument> create(final EffectiveModelContext schemaContext, final String data) {
        final ApiPath path;
        try {
            path = ApiPath.parse(requireNonNull(data));
        } catch (ParseException e) {
            throw new RestconfDocumentedException("Invalid path '" + data + "' at offset " + e.getErrorOffset(),
                ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE, e);
        }
        return create(schemaContext, path);
    }

    public static List<PathArgument> create(final EffectiveModelContext schemaContext, final ApiPath path) {
        return new YangInstanceIdentifierDeserializer(schemaContext, path).parse();
    }

    private ImmutableList<PathArgument> parse() {
        final var steps = apiPath.steps();
        if (steps.isEmpty()) {
            return ImmutableList.of();
        }

        final List<PathArgument> path = new ArrayList<>();
        DataSchemaContextNode<?> parentNode = DataSchemaContextTree.from(schemaContext).getRoot();
        QNameModule parentNs = null;
        for (Step step : steps) {
            final var module = step.module();
            final QNameModule ns;
            if (module == null) {
                if (parentNs == null) {
                    throw new RestconfDocumentedException(
                        "First member must use namespace-qualified form, '" + step.identifier() + "' does not",
                        ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE);
                }
                ns = parentNs;
            } else {
                ns = resolveNamespace(module);
            }

            final QName qname = step.identifier().bindTo(ns);
            final DataSchemaContextNode<?> childNode = nextContextNode(parentNode, qname, path);
            final PathArgument pathArg;
            if (step instanceof ListInstance) {
                final var values = ((ListInstance) step).keyValues();
                final var schema = childNode.getDataSchemaNode();
                pathArg = schema instanceof ListSchemaNode
                    ? prepareNodeWithPredicates(qname, (ListSchemaNode) schema, values)
                        : prepareNodeWithValue(qname, schema, values);
            } else if (childNode != null) {
                RestconfDocumentedException.throwIf(childNode.isKeyedEntry(),
                    ErrorType.PROTOCOL, ErrorTag.MISSING_ATTRIBUTE,
                    "Entry '%s' requires key or value predicate to be present.", qname);
                pathArg = childNode.getIdentifier();
            } else {
                // FIXME: this should be a hard error here, as we cannot resolve the node correctly!
                pathArg = NodeIdentifier.create(qname);
            }

            path.add(pathArg);
            parentNode = childNode;
            parentNs = ns;
        }

        return ImmutableList.copyOf(path);
    }

    private NodeIdentifierWithPredicates prepareNodeWithPredicates(final QName qname,
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
        for (int i = 0; i < keySize; ++i) {
            final QName keyName = keyDef.get(i);
            values.put(keyName, prepareValueByType(schema.getDataChildByName(keyName), keyValues.get(i)));
        }

        return NodeIdentifierWithPredicates.of(qname, values.build());
    }

    private Object prepareValueByType(final DataSchemaNode schemaNode, final @NonNull String value) {

        TypeDefinition<? extends TypeDefinition<?>> typedef;
        if (schemaNode instanceof LeafListSchemaNode) {
            typedef = ((LeafListSchemaNode) schemaNode).getType();
        } else {
            typedef = ((LeafSchemaNode) schemaNode).getType();
        }
        final TypeDefinition<?> baseType = RestUtil.resolveBaseTypeFrom(typedef);
        if (baseType instanceof LeafrefTypeDefinition) {
            typedef = SchemaInferenceStack.ofInstantiatedPath(schemaContext, schemaNode.getPath())
                .resolveLeafref((LeafrefTypeDefinition) baseType);
        }

        if (typedef instanceof IdentityrefTypeDefinition) {
            return toIdentityrefQName(value, schemaNode);
        }
        try {
            return RestCodec.from(typedef, null, schemaContext).deserialize(value);
        } catch (IllegalArgumentException e) {
            throw new RestconfDocumentedException("Invalid value '" + value + "' for " + schemaNode.getQName(),
                ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE, e);
        }
    }

    private NodeWithValue<?> prepareNodeWithValue(final QName qname, final DataSchemaNode schema,
            final List<String> keyValues) {
        // TODO: qname should be always equal to schema.getQName(), right?
        return new NodeWithValue<>(qname, prepareValueByType(schema,
            // FIXME: ahem: we probably want to do something differently here
            keyValues.get(0)));
    }

    private DataSchemaContextNode<?> nextContextNode(final DataSchemaContextNode<?> parent, final QName qname,
            final List<PathArgument> path) {
        final var found = parent.getChild(qname);
        if (found == null) {
            // FIXME: why are we making this special case here, especially with ...
            final var module = schemaContext.findModule(qname.getModule());
            if (module.isPresent()) {
                for (final RpcDefinition rpcDefinition : module.get().getRpcs()) {
                    // ... this comparison?
                    if (rpcDefinition.getQName().getLocalName().equals(qname.getLocalName())) {
                        return null;
                    }
                }
            }
            if (findActionDefinition(parent.getDataSchemaNode(), qname.getLocalName()).isPresent()) {
                return null;
            }

            throw new RestconfDocumentedException("Schema for '" + qname + "' not found",
                ErrorType.PROTOCOL, ErrorTag.DATA_MISSING);
        }

        var result = found;
        while (result.isMixin()) {
            path.add(result.getIdentifier());
            result = verifyNotNull(result.getChild(qname), "Mixin %s is missing child for %s while resolving %s",
                result, qname, found);
        }
        return result;
    }

    private static Optional<? extends ActionDefinition> findActionDefinition(final DataSchemaNode dataSchemaNode,
            // FIXME: this should be using a namespace
            final String nodeName) {
        if (dataSchemaNode instanceof ActionNodeContainer) {
            return ((ActionNodeContainer) dataSchemaNode).getActions().stream()
                    .filter(actionDef -> actionDef.getQName().getLocalName().equals(nodeName))
                    .findFirst();
        }
        return Optional.empty();
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
