/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.utils.parser;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
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
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.api.TypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.IdentityrefTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.LeafrefTypeDefinition;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;

/**
 * Deserializer for {@link String} to {@link YangInstanceIdentifier} for restconf.
 */
public final class YangInstanceIdentifierDeserializer {
    private final EffectiveModelContext schemaContext;
    private final ApiPath apiPath;

    private DataSchemaContextNode<?> current;

    private YangInstanceIdentifierDeserializer(final EffectiveModelContext schemaContext, final ApiPath apiPath) {
        this.schemaContext = requireNonNull(schemaContext);
        this.apiPath = requireNonNull(apiPath);
        current = DataSchemaContextTree.from(schemaContext).getRoot();
    }

    /**
     * Method to create {@link Iterable} from {@link PathArgument} which are parsing from data by {@link SchemaContext}.
     *
     * @param schemaContext for validate of parsing path arguments
     * @param data path to data, in URL string form
     * @return {@link Iterable} of {@link PathArgument}
     * @throws RestconfDocumentedException
     */
    public static List<PathArgument> create(final EffectiveModelContext schemaContext, final String data) {
        final ApiPath path;
        try {
            path = ApiPath.parse(requireNonNull(data));
        } catch (ParseException e) {
            throw new RestconfDocumentedException("Invalid path '" + data + "' at offset " + e.getErrorOffset(),
                ErrorType.APPLICATION, ErrorTag.MALFORMED_MESSAGE, e);
        }
        return new YangInstanceIdentifierDeserializer(schemaContext, path).parse();
    }

    private ImmutableList<PathArgument> parse() {
        final var steps = apiPath.steps();
        if (steps.isEmpty()) {
            return ImmutableList.of();
        }

        final List<PathArgument> path = new ArrayList<>();
        QNameModule prevNs = null;
        for (Step step : steps) {
            final var module = step.module();
            final QNameModule ns;
            if (module == null) {
                if (prevNs == null) {
                    throw new RestconfDocumentedException(
                        "First member must use namespace-qualified form, '" + step.identifier() + "' does not",
                        ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE);
                }
                ns = prevNs;
            } else {
                ns = resolveNamespace(module);
            }

            final QName qname = step.identifier().bindTo(ns);
            final DataSchemaContextNode<?> nextContext = nextContextNode(qname, path);
            final PathArgument pathArg;
            if (step instanceof ListInstance) {
                final var values = ((ListInstance) step).keyValues();
                final var schema = nextContext.getDataSchemaNode();
                pathArg = schema instanceof ListSchemaNode
                    // FIXME: is 'current' always the same as 'next' ? can we use 'schema' instead here?
                    ? prepareNodeWithPredicates(qname, (ListSchemaNode) current.getDataSchemaNode(), values)
                        : prepareNodeWithValue(qname, values);
            } else {
                RestconfDocumentedException.throwIf(nextContext != null && nextContext.isKeyedEntry(),
                    ErrorType.PROTOCOL, ErrorTag.MISSING_ATTRIBUTE,
                    "Entry '%s' requires key or value predicate to be present.", qname);
                pathArg = current == null ? NodeIdentifier.create(qname) : current.getIdentifier();
            }
            path.add(pathArg);
            prevNs = ns;
        }

        return ImmutableList.copyOf(path);
    }

    private NodeIdentifierWithPredicates prepareNodeWithPredicates(final QName qname,
            final ListSchemaNode schema, final List<@NonNull String> keyValues) {
        // FIXME: this should be guaranteed by caller
        final var listSchemaNode = RestconfDocumentedException.throwIfNull(schema,
            ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE, "Data schema node is null");

        final var keyDef = listSchemaNode.getKeyDefinition();
        final var keySize = keyDef.size();
        final var varSize = keyValues.size();
        if (keySize != varSize) {
            throw new RestconfDocumentedException(
                "Schema for " + qname + " requires " + keySize + " key values, " + varSize + " supplied",
                ErrorType.PROTOCOL,keySize > varSize ? ErrorTag.MISSING_ATTRIBUTE : ErrorTag.UNKNOWN_ATTRIBUTE);
        }

        final var values = ImmutableMap.<QName, Object>builderWithExpectedSize(keySize);
        for (int i = 0; i < keySize; ++i) {
            final QName keyName = keyDef.get(i);
            values.put(keyName, prepareValueByType(listSchemaNode.findDataChildByName(keyName).orElseThrow(),
                keyValues.get(i)));
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

    private NodeWithValue<?> prepareNodeWithValue(final QName qname, final List<String> keyValues) {
        return new NodeWithValue<>(qname, prepareValueByType(current.getDataSchemaNode(),
            // FIXME: ahem: we probably wnat to do something differently here
            keyValues.get(0)));
    }

    @SuppressFBWarnings(value = "NP_NULL_ON_SOME_PATH",
            justification = "code does check for null 'current' but FB doesn't recognize it")
    // FIXME: receive current node and make this method static, adjusting the caller to do the right thing
    private DataSchemaContextNode<?> nextContextNode(final QName qname, final List<PathArgument> path) {
        final DataSchemaContextNode<?> initialContext = current;
        final DataSchemaNode initialDataSchema = initialContext.getDataSchemaNode();

        current = initialContext.getChild(qname);

        if (current == null) {
            final Optional<Module> module = schemaContext.findModule(qname.getModule());
            if (module.isPresent()) {
                for (final RpcDefinition rpcDefinition : module.get().getRpcs()) {
                    if (rpcDefinition.getQName().getLocalName().equals(qname.getLocalName())) {
                        return null;
                    }
                }
            }
            if (findActionDefinition(initialDataSchema, qname.getLocalName()).isPresent()) {
                return null;
            }
        }

        RestconfDocumentedException.throwIfNull(current, ErrorType.PROTOCOL, ErrorTag.DATA_MISSING,
            "Schema for '%s' not found", qname);
        while (current.isMixin()) {
            path.add(current.getIdentifier());
            current = current.getChild(qname);
        }
        return current;
    }

    private static Optional<? extends ActionDefinition> findActionDefinition(final SchemaNode dataSchemaNode,
            final String nodeName) {
        requireNonNull(dataSchemaNode, "DataSchema Node must not be null.");
        if (dataSchemaNode instanceof ActionNodeContainer) {
            return ((ActionNodeContainer) dataSchemaNode).getActions().stream()
                    .filter(actionDef -> actionDef.getQName().getLocalName().equals(nodeName)).findFirst();
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

        for (var identity : schemaContext.findModule(namespace).orElseThrow().getIdentities()) {
            final var qname = identity.getQName();
            if (qname.getLocalName().equals(localName)) {
                return qname;
            }
        }

        throw new RestconfDocumentedException("No identity found for '" + localName + "' in namespace " + namespace,
            ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
    }

    private @NonNull QNameModule resolveNamespace(final String moduleName) {
        final var modules = schemaContext.findModules(moduleName);
        RestconfDocumentedException.throwIf(modules.isEmpty(), ErrorType.PROTOCOL, ErrorTag.UNKNOWN_ELEMENT,
            "Failed to lookup for module with name '%s'.", moduleName);
        return modules.iterator().next().getQNameModule();
    }
}
