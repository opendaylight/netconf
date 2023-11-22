/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.api.DOMYangTextSourceProvider;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.legacy.SchemaExportContext;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.ParserIdentifier;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.common.YangConstants;
import org.opendaylight.yangtools.yang.common.YangNames;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.stmt.ModuleEffectiveStatement;

/**
 * Retrieval of the YANG modules which server supports.
 */
@Path("/")
public class RestconfSchemaServiceImpl {
    // FIXME: Remove this constant. All logic relying on this constant should instead rely on YangInstanceIdentifier
    //        equivalent coming out of argument parsing. This may require keeping List<YangInstanceIdentifier> as the
    //        nested path split on yang-ext:mount. This splitting needs to be based on consulting the
    //        EffectiveModelContext and allowing it only where yang-ext:mount is actually used in models.
    private static final String MOUNT = "yang-ext:mount";
    private static final Splitter SLASH_SPLITTER = Splitter.on('/');

    private final DOMSchemaService schemaService;
    private final DOMMountPointService mountPointService;
    private final DOMYangTextSourceProvider sourceProvider;

    /**
     * Default constructor.
     *
     * @param schemaService a {@link DOMSchemaService}
     * @param mountPointService a {@link DOMMountPointService}
     */
    public RestconfSchemaServiceImpl(final DOMSchemaService schemaService,
            final DOMMountPointService mountPointService) {
        this.schemaService = requireNonNull(schemaService);
        this.mountPointService = requireNonNull(mountPointService);
        sourceProvider = schemaService.getExtensions().getInstance(DOMYangTextSourceProvider.class);
        checkArgument(sourceProvider != null, "No DOMYangTextSourceProvider available in %s", schemaService);
    }

    /**
     * Get schema of specific module.
     *
     * @param identifier path parameter
     * @return {@link SchemaExportContext}
     */
    @GET
    @Produces({ YangConstants.RFC6020_YIN_MEDIA_TYPE, YangConstants.RFC6020_YANG_MEDIA_TYPE })
    @Path("modules/{identifier:.+}")
    public SchemaExportContext getSchema(@PathParam("identifier") final String identifier) {
        return toSchemaExportContextFromIdentifier(schemaService.getGlobalContext(), identifier, mountPointService,
            sourceProvider);
    }

    /**
     * Parsing {@link Module} module by {@link String} module name and
     * {@link Date} revision and from the parsed module create
     * {@link SchemaExportContext}.
     *
     * @param schemaContext
     *             {@link EffectiveModelContext}
     * @param identifier
     *             path parameter
     * @param domMountPointService
     *             {@link DOMMountPointService}
     * @return {@link SchemaExportContext}
     */
    @VisibleForTesting
    static SchemaExportContext toSchemaExportContextFromIdentifier(final EffectiveModelContext schemaContext,
            final String identifier, final DOMMountPointService domMountPointService,
            final DOMYangTextSourceProvider sourceProvider) {
        final var pathComponents = SLASH_SPLITTER.split(identifier);
        final var componentIter = pathComponents.iterator();
        if (!Iterables.contains(pathComponents, MOUNT)) {
            final var module = coerceModule(schemaContext, validateAndGetModulName(componentIter),
                validateAndGetRevision(componentIter), null);
            return new SchemaExportContext(schemaContext, module, sourceProvider);
        }

        final var pathBuilder = new StringBuilder();
        while (componentIter.hasNext()) {
            final var current = componentIter.next();
            if (MOUNT.equals(current)) {
                pathBuilder.append('/').append(MOUNT);
                break;
            }

            if (!pathBuilder.isEmpty()) {
                pathBuilder.append('/');
            }
            pathBuilder.append(current);
        }
        final var point = ParserIdentifier.toInstanceIdentifier(pathBuilder.toString(), schemaContext,
                requireNonNull(domMountPointService));
        final var context = coerceModelContext(point.getMountPoint());
        final var module = coerceModule(context, validateAndGetModulName(componentIter),
            validateAndGetRevision(componentIter), point.getMountPoint());
        return new SchemaExportContext(context, module, sourceProvider);
    }


    /**
     * Validation and parsing of revision.
     *
     * @param revisionDate iterator
     * @return A Revision
     */
    @VisibleForTesting
    static Revision validateAndGetRevision(final Iterator<String> revisionDate) {
        if (!revisionDate.hasNext()) {
            throw new RestconfDocumentedException("Revision date must be supplied.",
                ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
        }
        try {
            return Revision.of(revisionDate.next());
        } catch (final DateTimeParseException e) {
            throw new RestconfDocumentedException("Supplied revision is not in expected date format YYYY-mm-dd", e);
        }
    }

    /**
     * Validation of name.
     *
     * @param moduleName iterator
     * @return {@link String}
     */
    @VisibleForTesting
    static String validateAndGetModulName(final Iterator<String> moduleName) {
        if (!moduleName.hasNext()) {
            throw new RestconfDocumentedException("Module name must be supplied.",
                ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
        }

        final var name = moduleName.next();
        if (name.isEmpty() || !YangNames.IDENTIFIER_START.matches(name.charAt(0))) {
            throw new RestconfDocumentedException("Identifier must start with character from set 'a-zA-Z_",
                ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
        }
        if (name.toUpperCase(Locale.ROOT).startsWith("XML")) {
            throw new RestconfDocumentedException("Identifier must NOT start with XML ignore case.",
                ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
        }
        if (YangNames.NOT_IDENTIFIER_PART.matchesAnyOf(name.substring(1))) {
            throw new RestconfDocumentedException("Supplied name has not expected identifier format.",
                ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
        }
        return name;
    }

    private static ModuleEffectiveStatement coerceModule(final EffectiveModelContext context, final String moduleName,
            final Revision revision, final DOMMountPoint mountPoint) {
        return context.findModule(moduleName, revision)
            .map(Module::asEffectiveStatement)
            .orElseThrow(() -> {
                final var msg = "Module %s %s cannot be found on %s.".formatted(moduleName, revision,
                    mountPoint == null ? "controller" : mountPoint.getIdentifier());
                return new RestconfDocumentedException(msg, ErrorType.APPLICATION, ErrorTag.DATA_MISSING);
            });
    }

    private static EffectiveModelContext coerceModelContext(final DOMMountPoint mountPoint) {
        final EffectiveModelContext context = modelContext(mountPoint);
        checkState(context != null, "Mount point %s does not have a model context", mountPoint);
        return context;
    }

    private static EffectiveModelContext modelContext(final DOMMountPoint mountPoint) {
        return mountPoint.getService(DOMSchemaService.class)
            .map(DOMSchemaService::getGlobalContext)
            .orElse(null);
    }
}
