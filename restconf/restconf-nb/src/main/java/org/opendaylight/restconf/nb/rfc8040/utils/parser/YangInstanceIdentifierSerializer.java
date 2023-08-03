/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.utils.parser;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CharMatcher;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map.Entry;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
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
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextTree;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

/**
 * Serializer for {@link YangInstanceIdentifier} to {@link String} for restconf.
 */
public final class YangInstanceIdentifierSerializer {
    // RFC8040 specifies that reserved characters need to be percent-encoded
    @VisibleForTesting
    static final CharMatcher PERCENT_ENCODE_CHARS =
            CharMatcher.anyOf(ParserConstants.RFC3986_RESERVED_CHARACTERS).precomputed();

    private YangInstanceIdentifierSerializer() {
        // Hidden on purpose
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
    public static String create(final EffectiveModelContext schemaContext, final YangInstanceIdentifier data) {
        final DataSchemaContext current = DataSchemaContextTree.from(schemaContext).getRoot();
        final MainVarsWrapper variables = new MainVarsWrapper(current);
        final StringBuilder path = new StringBuilder();

        QNameModule parentModule = null;
        for (final PathArgument arg : data.getPathArguments()) {
            // get module of the parent
            final var currentContext = variables.getCurrent();

            if (!(currentContext instanceof PathMixin)) {
                parentModule = currentContext.dataSchemaNode().getQName().getModule();
            }

            final var childContext = currentContext instanceof DataSchemaContext.Composite composite
                ? composite.childByArg(arg) : null;

            RestconfDocumentedException.throwIfNull(childContext, ErrorType.APPLICATION,
                    ErrorTag.UNKNOWN_ELEMENT, "Invalid input '%s': schema for argument '%s' (after '%s') not found",
                    data, arg, path);
            variables.setCurrent(childContext);
            if (childContext instanceof PathMixin) {
                continue;
            }

            // append namespace before every node which is defined in other module than its parent
            // condition is satisfied also for the first path argument
            if (!arg.getNodeType().getModule().equals(parentModule)) {
                // append slash if it is not the first path argument
                if (path.length() > 0) {
                    path.append('/');
                }

                path.append(prefixForNamespace(arg.getNodeType(), schemaContext)).append(':');
            } else {
                path.append('/');
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
        path.append('=');

        String value = String.valueOf(((NodeWithValue<String>) arg).getValue());
        if (PERCENT_ENCODE_CHARS.matchesAnyOf(value)) {
            value = parsePercentEncodeChars(value);
        }
        path.append(value);
    }

    private static void prepareNodeWithPredicates(final StringBuilder path, final PathArgument arg) {
        path.append(arg.getNodeType().getLocalName());

        final Iterator<Entry<QName, Object>> iterator = ((NodeIdentifierWithPredicates) arg).entrySet().iterator();
        if (iterator.hasNext()) {
            path.append('=');
        }

        while (iterator.hasNext()) {
            String valueOf = String.valueOf(iterator.next().getValue());
            if (PERCENT_ENCODE_CHARS.matchesAnyOf(valueOf)) {
                valueOf = parsePercentEncodeChars(valueOf);
            }
            path.append(valueOf);
            if (iterator.hasNext()) {
                path.append(',');
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

        for (int i = 0; i < valueOf.length(); ++i) {
            final char ch = valueOf.charAt(i);

            if (PERCENT_ENCODE_CHARS.matches(ch)) {
                final String upperCase = String.format(Locale.ROOT, "%X", (int) ch);
                sb.append('%').append(upperCase);
            } else {
                sb.append(ch);
            }
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
        final Module module = schemaContext.findModule(qname.getModule()).orElse(null);
        return module.getName();
    }

    private static final class MainVarsWrapper {
        private DataSchemaContext current;

        MainVarsWrapper(final DataSchemaContext current) {
            setCurrent(current);
        }

        public DataSchemaContext getCurrent() {
            return current;
        }

        public void setCurrent(final DataSchemaContext current) {
            this.current = current;
        }
    }
}
