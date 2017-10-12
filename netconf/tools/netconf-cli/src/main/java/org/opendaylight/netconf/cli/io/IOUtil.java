/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.cli.io;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.opendaylight.netconf.cli.reader.ReadingException;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.LeafListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;

public class IOUtil {

    public static final String SKIP = "skip";
    public static final String PROMPT_SUFIX = ">";
    public static final String PATH_SEPARATOR = "/";

    private IOUtil() {}

    public static boolean isQName(final String qualifiedName) {
        final Matcher matcher = PATTERN.matcher(qualifiedName);
        return matcher.matches();
    }

    public static String listType(final SchemaNode schemaNode) {
        if (schemaNode instanceof LeafListSchemaNode) {
            return "Leaf-list";
        } else if (schemaNode instanceof ListSchemaNode) {
            return "List";
        } else if (schemaNode instanceof LeafSchemaNode) {
            return "Leaf";
        }
        // FIXME throw exception on unexpected state, not null/emptyString
        return "";
    }

    public static String qNameToKeyString(final QName qualifiedName, final String moduleName) {
        return String.format("%s(%s)", qualifiedName.getLocalName(), moduleName);
    }

    // TODO test and check regex + review format of string for QName
    static final Pattern PATTERN = Pattern.compile("([^\\)]+)\\(([^\\)]+)\\)");

    public static QName qNameFromKeyString(final String qualifiedName, final Map<String, QName> mappedModules)
            throws ReadingException {
        final Matcher matcher = PATTERN.matcher(qualifiedName);
        if (!matcher.matches()) {
            final String message = String.format("QName in wrong format: %s should be: %s", qualifiedName, PATTERN);
            throw new ReadingException(message);
        }
        final QName base = mappedModules.get(matcher.group(2));
        if (base == null) {
            final String message = String.format("Module %s cannot be found", matcher.group(2));
            throw new ReadingException(message);
        }
        return QName.create(base, matcher.group(1));
    }

    public static boolean isSkipInput(final String rawValue) {
        return rawValue.equals(SKIP);
    }

}
