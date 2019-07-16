/*
 * Copyright © 2019 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.nb.rfc8040.utils.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.restconf.nb.rfc8040.utils.RestconfConstants;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.builder.ParserBuilderConstants;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.builder.ParserBuilderConstants.Deserializer;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;

/**
 * Tools for serialization/deserialization between {@link SchemaPath} and {@link String}.
 */
public final class SchemaPathCodec {

    private SchemaPathCodec() {
        throw new UnsupportedOperationException("Util class.");
    }

    /**
     * Serialization of the {@link SchemaPath} to {@link String}. Input {@link SchemaContext} is used for encoding
     * of modules. Absolute schema paths start with '/' while relative schema paths start with '.' character.
     * WARNING - only modules are checked against schema context, the compliance of the schema path from the view of
     * existence of local names in schema context and their nesting is not verified.
     *
     * @param schemaPath    Schema path to be serialized.
     * @param schemaContext Schema context.
     * @return Serialized schema path.
     */
    public static String serialize(final SchemaPath schemaPath, final SchemaContext schemaContext) {
        if (schemaPath.equals(SchemaPath.ROOT)) {
            return String.valueOf(RestconfConstants.SLASH);
        } else if (schemaPath.equals(SchemaPath.SAME)) {
            return String.valueOf(RestconfConstants.DOT);
        }

        final Iterable<QName> pathFromRoot = schemaPath.getPathFromRoot();
        final StringBuilder serializedPath = new StringBuilder();

        QNameModule parentModule = null;
        for (final QName qname : pathFromRoot) {
            if (!schemaPath.isAbsolute() && serializedPath.length() == 0) {
                serializedPath.append(RestconfConstants.DOT);
            }
            serializedPath.append(RestconfConstants.SLASH);
            if (!qname.getModule().equals(parentModule)) {
                serializedPath.append(getModuleName(qname.getModule(), schemaContext))
                        .append(ParserBuilderConstants.Deserializer.COLON);
                parentModule = qname.getModule();
            }
            serializedPath.append(qname.getLocalName());
        }

        return serializedPath.toString();
    }

    /**
     * Deserialization of the {@link SchemaPath} from {@link String} representation. Input {@link SchemaContext} is used
     * for translation of prefixes that identify module.
     * WARNING - only modules are checked against schema context, the compliance of the schema path from the view of
     * existence of local names in schema context and their nesting is not verified.
     *
     * @param serializedPath String representation of the schema path that was previously serialized by this utility.
     * @param schemaContext  Schema context.
     * @return Parsed schema path.
     */
    public static SchemaPath deserialize(final String serializedPath, final SchemaContext schemaContext) {
        final String[] slicedPath = serializedPath.split(String.valueOf(RestconfConstants.SLASH));
        if (slicedPath.length == 0) {
            return SchemaPath.ROOT;
        } else if (slicedPath.length == 1 && slicedPath[0].trim().equals(String.valueOf(RestconfConstants.DOT))) {
            return SchemaPath.SAME;
        }

        QNameModule parentModule = null;
        boolean isAbsoluteSchemaPath = false;
        final List<QName> parsedQNames = new ArrayList<>();

        for (int i = 0; i < slicedPath.length; i++) {
            String pathPart = slicedPath[i].trim();

            if (i == 0) {
                if (pathPart.isEmpty()) {
                    isAbsoluteSchemaPath = true;
                } else if (!pathPart.equals(String.valueOf(RestconfConstants.DOT))) {
                    throw new RestconfDocumentedException(String.format("Input schema path '%s' should start with dot "
                            + "(relative schema path) or slash (absolute schema path).", serializedPath),
                            RestconfError.ErrorType.APPLICATION, RestconfError.ErrorTag.MALFORMED_MESSAGE);
                }
                continue;
            } else {
                if (pathPart.isEmpty() || pathPart.equals(String.valueOf(RestconfConstants.DOT))) {
                    throw new RestconfDocumentedException(String.format("Schema path '%s' contains dots or empty "
                            + "path parts on invalid places.", serializedPath), RestconfError.ErrorType.APPLICATION,
                            RestconfError.ErrorTag.MALFORMED_MESSAGE);
                }
            }

            final String[] moduleAndLocalName = pathPart.split(String.valueOf(Deserializer.COLON));
            if (moduleAndLocalName.length == 1) {
                if (parentModule == null) {
                    throw new RestconfDocumentedException(String.format("The first path element '%s' must be specified "
                            + "by module so it can be identified in schema context.", moduleAndLocalName[0].trim()),
                            RestconfError.ErrorType.APPLICATION, RestconfError.ErrorTag.MALFORMED_MESSAGE);
                }
                final String localName = moduleAndLocalName[0].trim();
                parsedQNames.add(QName.create(parentModule, localName));
            } else if (moduleAndLocalName.length == 2) {
                final QNameModule module = findModuleByName(moduleAndLocalName[0].trim(), schemaContext);
                final String localName = moduleAndLocalName[1].trim();
                parsedQNames.add(QName.create(module, localName));
                parentModule = module;
            } else {
                throw new RestconfDocumentedException(String.format("Schema path part '%s' contains more "
                        + "than two colons.", pathPart.trim()), RestconfError.ErrorType.APPLICATION,
                        RestconfError.ErrorTag.MALFORMED_MESSAGE);
            }
        }

        return SchemaPath.create(parsedQNames,isAbsoluteSchemaPath);
    }

    private static String getModuleName(final QNameModule module, final SchemaContext schemaContext) {
        return schemaContext.findModule(module)
                .map(Module::getName)
                .orElseThrow(() -> new RestconfDocumentedException(String.format(
                        "Cannot find module in schema context: %s.", module), RestconfError.ErrorType.APPLICATION,
                        RestconfError.ErrorTag.MISSING_ELEMENT));
    }

    private static QNameModule findModuleByName(final String moduleName, final SchemaContext schemaContext) {
        final Set<Module> modules = schemaContext.findModules(moduleName);
        if (modules.isEmpty()) {
            throw new RestconfDocumentedException(String.format("Module with name '%s' cannot be found in "
                    + "schema context.", moduleName), RestconfError.ErrorType.APPLICATION,
                    RestconfError.ErrorTag.MISSING_ELEMENT);
        } else {
            return modules.iterator().next().getQNameModule();
        }
    }
}