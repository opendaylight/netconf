/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.communicator.impl.common;

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.codec.InstanceIdentifierCodec;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextNode;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextTree;
import org.opendaylight.yangtools.yang.model.api.ChoiceSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

/**
 * YangInstanceIdentifierToUrlCodec transforms {@link YangInstanceIdentifier} to restconf resource suffix.
 */
public class YangInstanceIdentifierToUrlCodec implements InstanceIdentifierCodec<String> {

    private final DataSchemaContextTree dataSchemaContextTree;
    private final SchemaContext schemaContext;

    public YangInstanceIdentifierToUrlCodec(final SchemaContext schemaContext) {
        this.schemaContext = schemaContext;
        this.dataSchemaContextTree = DataSchemaContextTree.from(schemaContext);
    }

    @Override
    public final String serialize(final YangInstanceIdentifier data) {
        final List<YangInstanceIdentifier.PathArgument> pathArguments = data.getPathArguments();
        final QName nodeType = pathArguments.get(0).getNodeType();
        final List<String> elements = new ArrayList<>();
        DataSchemaContextNode<?> current = dataSchemaContextTree.getRoot();
        for (final YangInstanceIdentifier.PathArgument arg : pathArguments) {
            current = current.getChild(arg);
            Preconditions.checkArgument(current != null,
                    "Invalid input %s: schema for argument %s not found", data, arg);

            if (current.isMixin()) {
                /*
                 * XML/YANG instance identifier does not have concept
                 * of augmentation identifier, or list as whole which
                 * identifies a mixin (same as the parent element),
                 * so we can safely ignore it if it is part of path
                 * (since child node) is identified in same fashion.
                 */
                continue;
            }
            elements.add(arg.getNodeType().getLocalName());


            if (arg instanceof YangInstanceIdentifier.NodeIdentifierWithPredicates) {
                appendKeyValues(elements, arg, current.getDataSchemaNode());
            } else if (arg instanceof YangInstanceIdentifier.NodeWithValue) {
                //TODO leaf-list id
            }
        }
        final Module module = schemaContext.findModuleByNamespaceAndRevision(nodeType.getNamespace(), nodeType.getRevision());
        return "/" + module.getName() + ":" + StringUtils.join(elements, "/");
    }

    private void appendKeyValues(final List<String> elements, final YangInstanceIdentifier.PathArgument pathArgument, final DataSchemaNode schemaNode) {
        final YangInstanceIdentifier.NodeIdentifierWithPredicates listId =
                (YangInstanceIdentifier.NodeIdentifierWithPredicates) pathArgument;
        Preconditions.checkState(schemaNode instanceof ListSchemaNode);
        final ListSchemaNode listSchemaNode = (ListSchemaNode) schemaNode;
        final List<QName> keyDefinition = listSchemaNode.getKeyDefinition();
        final StringBuilder builder = new StringBuilder(listId.getNodeType().getLocalName());
        builder.append("=");
        final List<String> keyValue = new ArrayList<>();
        for (final QName qName : keyDefinition) {
            final Object value = listId.getKeyValues().get(qName);
            if (value == null) {
                throw new IllegalStateException("all key values must be present");
            }
            keyValue.add(value.toString());
        }
        builder.append(StringUtils.join(keyValue, ","));
        elements.remove(elements.size() - 1);
        elements.add(builder.toString());
    }

    @Override
    public YangInstanceIdentifier deserialize(final String input) {
        String data = input;
        if (data.startsWith("/")) {
            data = data.substring(1);
        }
        if (data.endsWith("/")) {
            data = data.substring(0, data.length() - 1);
        }
        final List<String> pathArgs = Arrays.asList(data.split("/"));
        final String[] first = pathArgs.get(0).split(":");
        final String moduleName = first[0];
        final QNameModule qNameModule = schemaContext.findModuleByName(moduleName, null).getQNameModule();
        pathArgs.set(0, first[1]);
        final YangInstanceIdentifier.InstanceIdentifierBuilder builder = YangInstanceIdentifier.builder();
        DataSchemaContextNode<?> schemaNode = dataSchemaContextTree.getRoot();
        for (final String args : pathArgs) {
            final QName qName = getQname(qNameModule, args);
            schemaNode = schemaNode.getChild(qName);
            if (schemaNode.isMixin()) {
                final DataSchemaNode dataSchemaNode = schemaNode.getDataSchemaNode();
                if (dataSchemaNode instanceof ListSchemaNode) {
                    builder.node(qName);
                    final ListSchemaNode listSchemaNode = (ListSchemaNode) dataSchemaNode;
                    builder.node(buildNodeWithKey(listSchemaNode, args, qName));
                    schemaNode = schemaNode.getChild(qName);
                } else if (dataSchemaNode instanceof ChoiceSchemaNode) {
                    builder.node(dataSchemaNode.getQName());
                    builder.node(qName);
                }
            } else {
                builder.node(qName);
            }
        }
        return builder.build();
    }

    private static YangInstanceIdentifier.NodeIdentifierWithPredicates buildNodeWithKey(final ListSchemaNode listSchemaNode, final String pathArg,
                                                                                        final QName qName) {
        Preconditions.checkArgument(pathArg.contains("="), "pathArg does not containg list with keys");
        final String[] listWithKeys = pathArg.split("=");
        final String keys = listWithKeys[1];
        final Map<QName, Object> mapKeys = new HashMap<>();
        final String[] keyValues = keys.split(",");
        for (int i = 0; i < listSchemaNode.getKeyDefinition().size(); i++) {
            Preconditions.checkArgument(keyValues.length > i, "all key values must be present");
            mapKeys.put(listSchemaNode.getKeyDefinition().get(i), keyValues[i]);
        }
        return new YangInstanceIdentifier.NodeIdentifierWithPredicates(qName, mapKeys);
    }

    private static QName getQname(final QNameModule qNameModule, final String args) {
        if (args.contains("=")) {
            final String[] listWithKeys = args.split("=");
            return QName.create(qNameModule, listWithKeys[0]);
        }
        return QName.create(qNameModule, args);
    }
}