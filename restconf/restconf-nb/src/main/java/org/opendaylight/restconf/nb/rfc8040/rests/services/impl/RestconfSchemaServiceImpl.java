/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import static com.google.common.base.Preconditions.checkArgument;
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
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Response;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.api.DOMYangTextSourceProvider;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfFuture;
import org.opendaylight.restconf.nb.jaxrs.JaxRsRestconfCallback;
import org.opendaylight.restconf.nb.rfc8040.legacy.SchemaExportContext;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.ParserIdentifier;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.common.YangConstants;
import org.opendaylight.yangtools.yang.common.YangNames;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.Module;

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
    public void getSchema(@PathParam("identifier") final String identifier, @Suspended final AsyncResponse ar) {
        toSchemaExportContextFromIdentifier(schemaService.getGlobalContext(), identifier, mountPointService,
            sourceProvider).addCallback(new JaxRsRestconfCallback<>(ar) {
                @Override
                protected Response transform(final SchemaExportContext result) {
                    return Response.ok(result).build();
                }
            });
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
    static @NonNull RestconfFuture<SchemaExportContext> toSchemaExportContextFromIdentifier(
            final EffectiveModelContext schemaContext, final String identifier,
            final DOMMountPointService domMountPointService, final DOMYangTextSourceProvider sourceProvider) {
        final var pathComponents = SLASH_SPLITTER.split(identifier);

        final var it = pathComponents.iterator();
        final EffectiveModelContext context;
        final Object debugName;
        if (Iterables.contains(pathComponents, MOUNT)) {
            final var pathBuilder = new StringBuilder();
            while (it.hasNext()) {
                final var current = it.next();
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
            final var mountPoint = point.getMountPoint();
            debugName = mountPoint.getIdentifier();
            context = mountPoint.getService(DOMSchemaService.class)
                .map(DOMSchemaService::getGlobalContext)
                .orElse(null);
            if (context == null) {
                return RestconfFuture.failed(new RestconfDocumentedException(
                    "Mount point '" + debugName + "' does not have a model context"));
            }
        } else {
            context = requireNonNull(schemaContext);
            debugName = "controller";
        }

        final var moduleName = validateAndGetModulName(it);
        final var revision = validateAndGetRevision(it);
        final var optModule = context.findModule(moduleName, revision);
        if (optModule.isEmpty()) {
            return RestconfFuture.failed(new RestconfDocumentedException(
                "Module %s %s cannot be found on %s.".formatted(moduleName, revision, debugName),
                ErrorType.APPLICATION, ErrorTag.DATA_MISSING));
        }

        return RestconfFuture.of(new SchemaExportContext(context, optModule.orElseThrow().asEffectiveStatement(),
            // FIXME: this does not seem right -- mounts should have their own thing
            sourceProvider));
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
}
