/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.utils.parser;

import com.google.common.base.Preconditions;
import java.net.URI;
import java.util.Iterator;
import java.util.Map.Entry;
import org.opendaylight.restconf.nb.rfc8040.utils.RestconfConstants;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.builder.ParserBuilderConstants;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.builder.ParserBuilderConstants.Serializer;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeWithValue;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextNode;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextTree;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

/**
 * Serializer for {@link YangInstanceIdentifier} to {@link String} for restconf.
 */
public final class YangInstanceIdentifierSerializer {

    private YangInstanceIdentifierSerializer() {
        throw new UnsupportedOperationException("Util class.");
    }

    /**
     * Method to create String from {@link Iterable} of {@link PathArgument}
     * which are parsing from data by {@link SchemaContext}.
     *
     * @param schemaContext
     *             for validate of parsing path arguments
     * @param data
     *             path to data
     * @return {@link String}
     */
    public static String create(final SchemaContext schemaContext, final YangInstanceIdentifier data) {
        final DataSchemaContextNode<?> current = DataSchemaContextTree.from(schemaContext).getRoot();
        final MainVarsWrapper variables = new MainVarsWrapper(current);
        final StringBuilder path = new StringBuilder();

        QNameModule parentModule = null;
        for (int i = 0; i < data.getPathArguments().size(); i++) {
            // get module of the parent
            if (!variables.getCurrent().isMixin()) {
                parentModule = variables.getCurrent().getDataSchemaNode().getQName().getModule();
            }

            final PathArgument arg = data.getPathArguments().get(i);
            variables.setCurrent(variables.getCurrent().getChild(arg));

            Preconditions.checkArgument(variables.getCurrent() != null,
                    "Invalid input %s: schema for argument %s (after %s) not found", data, arg, path);

            if (variables.getCurrent().isMixin()) {
                continue;
            }

            // append namespace before every node which is defined in other module than its parent
            // condition is satisfied also for the first path argument
            if (!arg.getNodeType().getModule().equals(parentModule)) {
                // append slash if it is not the first path argument
                if (path.length() > 0) {
                    path.append(RestconfConstants.SLASH);
                }

                path.append(prefixForNamespace(arg.getNodeType(), schemaContext));
                path.append(ParserBuilderConstants.Deserializer.COLON);
            } else {
                path.append(RestconfConstants.SLASH);
            }

            if (arg instanceof NodeIdentifierWithPredicates) {
                prepareNodeWithPredicates(path, arg);
            } else if (arg instanceof NodeWithValue) {
                prepareNodeWithValue(path, arg);
            } else {
                appendQName(path, arg.getNodeType());
            }
        }

        return path.toString();
    }

    private static void prepareNodeWithValue(final StringBuilder path, final PathArgument arg) {
        path.append(arg.getNodeType().getLocalName());
        path.append(ParserBuilderConstants.Deserializer.EQUAL);

        String value = String.valueOf(((NodeWithValue<String>) arg).getValue());
        if (Serializer.PERCENT_ENCODE_CHARS.matchesAnyOf(value)) {
            value = parsePercentEncodeChars(value);
        }
        path.append(value);
    }

    private static void prepareNodeWithPredicates(final StringBuilder path, final PathArgument arg) {
        path.append(arg.getNodeType().getLocalName());

        final Iterator<Entry<QName, Object>> iterator = ((NodeIdentifierWithPredicates) arg).getKeyValues()
                .entrySet().iterator();

        if (iterator.hasNext()) {
            path.append(ParserBuilderConstants.Deserializer.EQUAL);
        }

        while (iterator.hasNext()) {
            String valueOf = String.valueOf(iterator.next().getValue());
            if (Serializer.PERCENT_ENCODE_CHARS.matchesAnyOf(valueOf)) {
                valueOf = parsePercentEncodeChars(valueOf);
            }
            path.append(valueOf);
            if (iterator.hasNext()) {
                path.append(ParserBuilderConstants.Deserializer.COMMA);
            }
        }
    }

    /**
     * Encode {@link Serializer#DISABLED_CHARS} chars to percent encoded chars.
     *
     * @param valueOf
     *             string to encode
     * @return encoded {@link String}
     */
    private static String parsePercentEncodeChars(final String valueOf) {
        final StringBuilder sb = new StringBuilder();
        int start = 0;
        while (start < valueOf.length()) {
            if (Serializer.PERCENT_ENCODE_CHARS.matches(valueOf.charAt(start))) {
                final String format = String.format("%x", (int) valueOf.charAt(start));
                final String upperCase = format.toUpperCase();
                sb.append(ParserBuilderConstants.Deserializer.PERCENT_ENCODING + upperCase);
            } else {
                sb.append(valueOf.charAt(start));
            }
            start++;
        }
        return sb.toString();
    }

    /**
     * Add {@link QName} to the serialized string.
     *
     * @param path
     *             {@link StringBuilder}
     * @param qname
     *             {@link QName} node
     * @return {@link StringBuilder}
     */
    private static StringBuilder appendQName(final StringBuilder path, final QName qname) {
        path.append(qname.getLocalName());
        return path;
    }

    /**
     * Create prefix of namespace from {@link QName}.
     *
     * @param qname
     *             {@link QName}
     * @return {@link String}
     */
    private static String prefixForNamespace(final QName qname, final SchemaContext schemaContext) {
        final URI namespace = qname.getNamespace();
        Preconditions.checkArgument(namespace != null, "Failed to map QName {}", qname);
        final Module module = schemaContext.findModuleByNamespaceAndRevision(namespace, qname.getRevision());
        return module.getName();
    }

    private static final class MainVarsWrapper {

        private DataSchemaContextNode<?> current;

        MainVarsWrapper(final DataSchemaContextNode<?> current) {
            this.setCurrent(current);
        }

        public DataSchemaContextNode<?> getCurrent() {
            return this.current;
        }

        public void setCurrent(final DataSchemaContextNode<?> current) {
            this.current = current;
        }

    }
}
