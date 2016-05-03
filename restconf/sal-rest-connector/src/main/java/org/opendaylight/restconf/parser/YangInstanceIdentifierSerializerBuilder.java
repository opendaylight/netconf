/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.parser;

import com.google.common.base.CharMatcher;
import com.google.common.base.Preconditions;
import java.net.URI;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.opendaylight.yangtools.concepts.Builder;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeWithValue;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextNode;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextTree;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

public class YangInstanceIdentifierSerializerBuilder implements Builder<String> {

    private final SchemaContext schemaContext;
    private final YangInstanceIdentifier data;
    private static final CharMatcher PERCENT_ENCODE_CHARS = CharMatcher.anyOf(",': /").precomputed();

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
                    if (PERCENT_ENCODE_CHARS.matchesAnyOf(valueOf)) {
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

    private String parsePercentEncodeChars(final String valueOf) {
        final StringBuilder sb = new StringBuilder();
        int start = 0;
        while (start < valueOf.length()) {
            if (PERCENT_ENCODE_CHARS.matches(valueOf.charAt(start))) {
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

    protected final StringBuilder appendQName(final StringBuilder sb, final QName qname) {
        final String prefix = prefixForNamespace(qname.getNamespace());
        Preconditions.checkArgument(prefix != null, "Failed to map QName {}", qname);
        sb.append(prefix);
        sb.append(':');
        sb.append(qname.getLocalName());
        return sb;
    }

    private String prefixForNamespace(final URI namespace) {
        final String prefix = namespace.toString();
        return prefix.replace(':', '-');
    }

}
