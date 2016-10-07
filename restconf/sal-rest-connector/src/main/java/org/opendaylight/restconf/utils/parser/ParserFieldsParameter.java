/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.utils.parser;

import com.google.common.base.CharMatcher;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import javax.annotation.Nonnull;
import org.opendaylight.netconf.sal.restconf.impl.InstanceIdentifierContext;
import org.opendaylight.restconf.parser.IdentifierCodec;
import org.opendaylight.restconf.utils.parser.builder.ParserBuilderConstants;
import org.opendaylight.restconf.utils.parser.builder.ParserBuilderConstants.Deserializer;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;

public class ParserFieldsParameter {
    public static @Nonnull List<YangInstanceIdentifier> parseFieldsParameter(@Nonnull final InstanceIdentifierContext<?> identifier,
                                                                             @Nonnull final String fields) {
        final List<YangInstanceIdentifier> result = new LinkedList<>();
        int j = 0;

        final SchemaContext context = identifier.getSchemaContext();
        DataSchemaContextNode<?> currentNode = DataSchemaContextNode.fromDataSchemaNode(
                (DataSchemaNode) identifier.getSchemaNode());
        String namespace = identifier.getSchemaNode().getQName().getNamespace().toString();

        for (int i = 0; i < fields.length(); i++) {
            if (Deserializer.IDENTIFIER.matches(fields.charAt(i)) || fields.charAt(i) == '/') {
                if (fields.charAt(i) == '/') {
                    final QName childQName = QName.create(namespace, fields.substring(j, i));
                    final DataSchemaContextNode<?> child = currentNode.getChild(childQName);
                    currentNode = child;
                    j = i + 1;
                }

                continue;
            }

            if (CharMatcher.is(':').matches(fields.charAt(i))) {
                // new namespace
                namespace = context.findModuleByName(fields.substring(j, i), null).getNamespace().toString();
            } else if (CharMatcher.is('(').matches(fields.charAt(i))) {
                final QName qName = QName.create(
                        namespace, fields.substring(j, i), currentNode.getIdentifier().getNodeType().getRevision());
                final DataSchemaContextNode<?> child = currentNode.getChild(qName);
                currentNode = child;
            } else if (CharMatcher.is(';').matches(fields.charAt(i))) {
                // FIXME lists, containers
                final QName qName = QName.create(
                        namespace, fields.substring(j, i), currentNode.getIdentifier().getNodeType().getRevision());
                result.add(YangInstanceIdentifier.create(currentNode.getChild(qName).getIdentifier()));
            }

            j = i + 1;
        }

        if (j != fields.length()) {
            final QName qName = QName.create(
                    namespace, fields.substring(j), currentNode.getIdentifier().getNodeType().getRevision());
            result.add(YangInstanceIdentifier.create(currentNode.getChild(qName).getIdentifier()));
        }

        return result;
    }
}
