/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.utils.parser;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import java.time.format.DateTimeParseException;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.Optional;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.api.DOMYangTextSourceProvider;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.schema.SchemaExportContext;
import org.opendaylight.restconf.nb.rfc8040.ModeledRequest;
import org.opendaylight.restconf.nb.rfc8040.utils.RestconfConstants;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Util class for parsing identifier.
 */
public final class ParserIdentifier {
    private static final Logger LOG = LoggerFactory.getLogger(ParserIdentifier.class);
    private static final Splitter MP_SPLITTER = Splitter.on("/" + RestconfConstants.MOUNT);

    private ParserIdentifier() {
        // Hidden on purpose
    }

    /**
     * Make a moduleName/Revision pair from identifier.
     *
     * @param identifier
     *             path parameter
     * @return {@link QName}
     */
    @VisibleForTesting
    static Entry<String, Revision> makeQNameFromIdentifier(final String identifier) {
        // check if more than one slash is not used as path separator
        if (identifier.contains("//")) {
            LOG.debug("URI has bad format. It should be \'moduleName/yyyy-MM-dd\' {}", identifier);
            throw new RestconfDocumentedException(
                    "URI has bad format. End of URI should be in format \'moduleName/yyyy-MM-dd\'", ErrorType.PROTOCOL,
                    ErrorTag.INVALID_VALUE);
        }

        final int mountIndex = identifier.indexOf(RestconfConstants.MOUNT);
        final String moduleNameAndRevision;
        if (mountIndex >= 0) {
            moduleNameAndRevision = identifier.substring(mountIndex + RestconfConstants.MOUNT.length())
                    .replaceFirst("/", "");
        } else {
            moduleNameAndRevision = identifier;
        }

        final List<String> pathArgs = RestconfConstants.SLASH_SPLITTER.splitToList(moduleNameAndRevision);
        if (pathArgs.size() != 2) {
            LOG.debug("URI has bad format '{}'. It should be 'moduleName/yyyy-MM-dd'", identifier);
            throw new RestconfDocumentedException(
                    "URI has bad format. End of URI should be in format \'moduleName/yyyy-MM-dd\'", ErrorType.PROTOCOL,
                    ErrorTag.INVALID_VALUE);
        }

        final Revision moduleRevision;
        try {
            moduleRevision = Revision.of(pathArgs.get(1));
        } catch (final DateTimeParseException e) {
            LOG.debug("URI has bad format: '{}'. It should be 'moduleName/yyyy-MM-dd'", identifier);
            throw new RestconfDocumentedException("URI has bad format. It should be \'moduleName/yyyy-MM-dd\'",
                    ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE, e);
        }

        return new SimpleImmutableEntry<>(pathArgs.get(0), moduleRevision);
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
    public static SchemaExportContext toSchemaExportContextFromIdentifier(final EffectiveModelContext schemaContext,
            final String identifier, final DOMMountPointService domMountPointService,
            final DOMYangTextSourceProvider sourceProvider) {
        final Iterable<String> pathComponents = RestconfConstants.SLASH_SPLITTER.split(identifier);
        final Iterator<String> componentIter = pathComponents.iterator();
        if (!Iterables.contains(pathComponents, RestconfConstants.MOUNT)) {
            final String moduleName = validateAndGetModulName(componentIter);
            final Revision revision = validateAndGetRevision(componentIter);
            final Module module = schemaContext.findModule(moduleName, revision).orElse(null);
            return new SchemaExportContext(schemaContext, module, sourceProvider);
        } else {
            final StringBuilder pathBuilder = new StringBuilder();
            while (componentIter.hasNext()) {
                final String current = componentIter.next();

                if (RestconfConstants.MOUNT.equals(current)) {
                    pathBuilder.append('/').append(RestconfConstants.MOUNT);
                    break;
                }

                if (pathBuilder.length() != 0) {
                    pathBuilder.append('/');
                }

                pathBuilder.append(current);
            }
            final InstanceIdentifierContext<?> point = ModeledRequest.createIdentifierContext(pathBuilder.toString(),
                schemaContext, Optional.of(domMountPointService));
            final String moduleName = validateAndGetModulName(componentIter);
            final Revision revision = validateAndGetRevision(componentIter);
            final EffectiveModelContext context = coerceModelContext(point.getMountPoint());
            final Module module = context.findModule(moduleName, revision).orElse(null);
            return new SchemaExportContext(context, module, sourceProvider);
        }
    }

    public static YangInstanceIdentifier parserPatchTarget(final InstanceIdentifierContext<?> context,
            final String target) {
        final var schemaContext = context.getSchemaContext();
        final var urlPath = context.getInstanceIdentifier();
        final String targetUrl;
        if (urlPath.isEmpty()) {
            targetUrl = target.startsWith("/") ? target.substring(1) : target;
        } else {
            targetUrl = IdentifierCodec.serialize(urlPath, schemaContext) + target;
        }

        // FIXME: this needs to be strict parsing and we should support mount points down the road
        return ModeledRequest.createIdentifierContext(targetUrl, schemaContext, Optional.empty())
            .getInstanceIdentifier();
    }

    /**
     * Validation and parsing of revision.
     *
     * @param revisionDate iterator
     * @return A Revision
     */
    @VisibleForTesting
    static Revision validateAndGetRevision(final Iterator<String> revisionDate) {
        RestconfDocumentedException.throwIf(!revisionDate.hasNext(), "Revision date must be supplied.",
            ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
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
        RestconfDocumentedException.throwIf(!moduleName.hasNext(), "Module name must be supplied.", ErrorType.PROTOCOL,
            ErrorTag.INVALID_VALUE);
        final String name = moduleName.next();

        RestconfDocumentedException.throwIf(
            name.isEmpty() || !ParserConstants.YANG_IDENTIFIER_START.matches(name.charAt(0)),
            "Identifier must start with character from set 'a-zA-Z_", ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
        RestconfDocumentedException.throwIf(name.toUpperCase(Locale.ROOT).startsWith("XML"),
            "Identifier must NOT start with XML ignore case.", ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
        RestconfDocumentedException.throwIf(
            !ParserConstants.YANG_IDENTIFIER_PART.matchesAllOf(name.substring(1)),
            "Supplied name has not expected identifier format.", ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);

        return name;
    }

    private static EffectiveModelContext coerceModelContext(final DOMMountPoint mountPoint) {
        final EffectiveModelContext context = modelContext(mountPoint);
        checkState(context != null, "Mount point %s does not have a model context", mountPoint);
        return context;
    }

    private static EffectiveModelContext modelContext(final DOMMountPoint mountPoint) {
        return mountPoint.getService(DOMSchemaService.class)
            .flatMap(svc -> Optional.ofNullable(svc.getGlobalContext()))
            .orElse(null);
    }
}
