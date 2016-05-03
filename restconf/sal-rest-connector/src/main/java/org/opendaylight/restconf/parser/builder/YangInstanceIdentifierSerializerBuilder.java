/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.parser.builder;

import com.google.common.base.Preconditions;
import java.net.URI;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.opendaylight.restconf.utils.parser.builder.ParserBuilderConstants;
import org.opendaylight.yangtools.concepts.Builder;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeWithValue;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextNode;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextTree;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

/**
 * Serializer for {@link YangInstanceIdentifier} to {@link String} for restconf
 *
 */
public class YangInstanceIdentifierSerializerBuilder implements Builder<String> {

    private final SchemaContext schemaContext;
    private final YangInstanceIdentifier data;

    /**
     * Set {@link SchemaContext} and {@link YangInstanceIdentifier}
     *
     * @param schemaContext
     *            - {@link SchemaContext}
     * @param data
     *            - {@link YangInstanceIdentifier} to serialize
     */
    public YangInstanceIdentifierSerializerBuilder(final SchemaContext schemaContext,
            final YangInstanceIdentifier data) {
        this.schemaContext = schemaContext;
        this.data = data;
    }

    @Override
    public String build() {
        final StringBuilder sb = new StringBuilder();
        DataSchemaContextNode<?> current = DataSchemaContextTree.from(this.schemaContext).getRoot();
        int i = 0;
        for (final PathArgument arg : this.data.getPathArguments()) {
            current = current.getChild(arg);
            Preconditions.checkArgument(current != null,
                    "Invalid input %s: schema for argument %s (after %s) not found", this.data, arg, sb);

            if (current.isMixin()) {
                continue;
            }
            sb.append('/');
            if(i==0){
                i++;
                appendQName(sb, arg.getNodeType());
            }

            if (arg instanceof NodeIdentifierWithPredicates) {
                sb.append(arg.getNodeType().getLocalName());
                sb.append("=");

                final Set<Entry<QName, Object>> entrySet = ((NodeIdentifierWithPredicates) arg).getKeyValues().entrySet();
                final int endOfSet = entrySet.size();
                int s = 1;
                for (final Map.Entry<QName, Object> entry : entrySet) {
                    String valueOf = String.valueOf(entry.getValue());
                    if (ParserBuilderConstants.Serializer.PERCENT_ENCODE_CHARS.matchesAnyOf(valueOf)) {
                        valueOf = parsePercentEncodeChars(valueOf);
                    }
                    sb.append(valueOf);
                    if (s != endOfSet) {
                        sb.append(",");
                        s++;
                    }
                }
            } else if (arg instanceof NodeWithValue) {
                sb.append(arg.getNodeType().getLocalName());
                sb.append("=");
                sb.append(((NodeWithValue) arg).getValue());
            }
        }
        return sb.toString();
    }

    /**
     * Encode {@link YangInstanceIdentifierSerializerBuilder#DISABLED_CHARS}
     * chars to percent encoded chars
     *
     * @param valueOf
     *            - string to encode
     * @return encoded {@link String}
     */
    private String parsePercentEncodeChars(final String valueOf) {
        final StringBuilder sb = new StringBuilder();
        int start = 0;
        while (start < valueOf.length()) {
            if (ParserBuilderConstants.Serializer.PERCENT_ENCODE_CHARS.matches(valueOf.charAt(start))) {
                final String format = String.format("%x", (int) valueOf.charAt(start));
                final String upperCase = format.toUpperCase();
                sb.append("%" + upperCase);
            } else {
                sb.append(valueOf.charAt(start));
            }
            start++;
        }
        return sb.toString();
    }

    /**
     * Add {@link QName} to the serialized string
     *
     * @param sb
     *            - {@link StringBuilder}
     * @param qname
     *            - {@link QName} node
     * @return {@link StringBuilder}
     */
    private final StringBuilder appendQName(final StringBuilder sb, final QName qname) {
        final String prefix = prefixForNamespace(qname.getNamespace());
        Preconditions.checkArgument(prefix != null, "Failed to map QName {}", qname);
        sb.append(prefix);
        sb.append(':');
        sb.append(qname.getLocalName());
        return sb;
    }

    /**
     * Create prefix of namespace from {@link URI}
     *
     * @param namespace
     *            - {@link URI}
     * @return {@link String}
     */
    private String prefixForNamespace(final URI namespace) {
        final String prefix = namespace.toString();
        return prefix.replace(':', '-');
    }

}
