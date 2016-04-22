/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.rest.impl.services;

import java.text.ParseException;
import java.util.Date;
import java.util.Iterator;
import org.opendaylight.netconf.md.sal.rest.common.RestconfValidationUtils;
import org.opendaylight.netconf.md.sal.rest.schema.SchemaExportContext;
import org.opendaylight.netconf.sal.restconf.impl.InstanceIdentifierContext;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorTag;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorType;
import org.opendaylight.restconf.rest.api.schema.context.SchemaContextHandler;
import org.opendaylight.restconf.rest.api.services.schema.RestconfSchemaService;
import org.opendaylight.restconf.utils.RestconfConstants;
import org.opendaylight.restconf.utils.parser.ParserIdentifier;
import org.opendaylight.yangtools.yang.common.SimpleDateFormatUtil;
import org.opendaylight.yangtools.yang.model.api.Module;
import com.google.common.collect.Iterables;

public class RestconfSchemaServiceImpl implements RestconfSchemaService {

    private final SchemaContextHandler schemaContextHandler;

    public RestconfSchemaServiceImpl(final SchemaContextHandler schemaContextHandler) {
        this.schemaContextHandler = schemaContextHandler;
    }

    @Override
    public SchemaExportContext getSchema(final String mountAndModule) {
        final SchemaContextRef schemaContextRef = new SchemaContextRef(this.schemaContextHandler.getSchemaContext());
        final Iterable<String> pathComponents = RestconfConstants.SLASH_SPLITTER.split(mountAndModule);
        final Iterator<String> componentIter = pathComponents.iterator();
        if (!Iterables.contains(pathComponents, RestconfConstants.MOUNT)) {
            final String moduleName = validAndGetModulName(componentIter);
            final Date revision = validAndGetRevision(componentIter);
            final Module module = schemaContextRef.findModuleByNameAndRevision(moduleName, revision);
            return new SchemaExportContext(schemaContextRef.get(), module);
        } else {
            final StringBuilder pathBuilder = new StringBuilder();
            while (componentIter.hasNext()) {
                final String current = componentIter.next();
                if (pathBuilder.length() != 0) {
                    pathBuilder.append("/");
                }
                pathBuilder.append(current);
                if (RestconfConstants.MOUNT.equals(current)) {
                    break;
                }
            }
            final InstanceIdentifierContext<?> mountPoint = ParserIdentifier
                    .toInstanceIdentifier(pathBuilder.toString());
            final String moduleName = validAndGetModulName(componentIter);
            final Date revision = validAndGetRevision(componentIter);
            final Module module = mountPoint.getSchemaContext().findModuleByName(moduleName, revision);
            return new SchemaExportContext(mountPoint.getSchemaContext(), module);
        }
    }

    private Date validAndGetRevision(final Iterator<String> componentIter) {
        RestconfValidationUtils.checkDocumentedError(componentIter.hasNext(), ErrorType.PROTOCOL,
                ErrorTag.INVALID_VALUE, "Revision date must be supplied.");
        try {
            return SimpleDateFormatUtil.getRevisionFormat().parse(componentIter.next());
        } catch (final ParseException e) {
            throw new RestconfDocumentedException("Supplied revision is not in expected date format YYYY-mm-dd", e);
        }
    }

    private String validAndGetModulName(final Iterator<String> componentIter) {
        RestconfValidationUtils.checkDocumentedError(componentIter.hasNext(), ErrorType.PROTOCOL,
                ErrorTag.INVALID_VALUE, "Module name must be supplied.");
        return componentIter.next();
    }
}
