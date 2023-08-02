/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.utils.parser;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

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
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.api.DOMYangTextSourceProvider;
import org.opendaylight.restconf.common.ErrorTags;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.rests.services.api.SchemaExportContext;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.common.YangNames;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Util class for parsing identifier.
 */
public final class ParserIdentifier {
    private static final Logger LOG = LoggerFactory.getLogger(ParserIdentifier.class);

    // FIXME: Remove this constant. All logic relying on this constant should instead rely on YangInstanceIdentifier
    //        equivalent coming out of argument parsing. This may require keeping List<YangInstanceIdentifier> as the
    //        nested path split on yang-ext:mount. This splitting needs to be based on consulting the
    //        EffectiveModelContext and allowing it only where yang-ext:mount is actually used in models.
    private static final String MOUNT = "yang-ext:mount";
    private static final Splitter MP_SPLITTER = Splitter.on("/" + MOUNT);
    private static final Splitter SLASH_SPLITTER = Splitter.on('/');

    private ParserIdentifier() {
        // Hidden on purpose
    }

    /**
     * Make {@link InstanceIdentifierContext} from {@link String} identifier
     * <br>
     * For identifiers of data NOT behind mount points returned
     * {@link InstanceIdentifierContext} is prepared with {@code null} reference of {@link DOMMountPoint} and with
     * controller's {@link SchemaContext}.
     * <br>
     * For identifiers of data behind mount points returned
     * {@link InstanceIdentifierContext} is prepared with reference of {@link DOMMountPoint} and its
     * own {@link SchemaContext}.
     *
     * @param identifier
     *           - path identifier
     * @param schemaContext
     *           - controller schema context
     * @param mountPointService
     *           - mount point service
     * @return {@link InstanceIdentifierContext}
     */
    // FIXME: NETCONF-631: this method should not be here, it should be a static factory in InstanceIdentifierContext:
    //
    //        @NonNull InstanceIdentifierContext forUrl(identifier, schemaContexxt, mountPointService)
    //
    public static InstanceIdentifierContext toInstanceIdentifier(final String identifier,
            final EffectiveModelContext schemaContext, final @Nullable DOMMountPointService mountPointService) {
        if (identifier == null || !identifier.contains(MOUNT)) {
            return createIIdContext(schemaContext, identifier, null);
        }
        if (mountPointService == null) {
            throw new RestconfDocumentedException("Mount point service is not available");
        }

        final Iterator<String> pathsIt = MP_SPLITTER.split(identifier).iterator();
        final String mountPointId = pathsIt.next();
        final YangInstanceIdentifier mountPath = IdentifierCodec.deserialize(mountPointId, schemaContext);
        final DOMMountPoint mountPoint = mountPointService.getMountPoint(mountPath)
                .orElseThrow(() -> new RestconfDocumentedException("Mount point does not exist.",
                    ErrorType.PROTOCOL, ErrorTags.RESOURCE_DENIED_TRANSPORT));

        final EffectiveModelContext mountSchemaContext = coerceModelContext(mountPoint);
        final String pathId = pathsIt.next().replaceFirst("/", "");
        return createIIdContext(mountSchemaContext, pathId, mountPoint);
    }

    /**
     * Method to create {@link InstanceIdentifierContext} from {@link YangInstanceIdentifier}
     * and {@link SchemaContext}, {@link DOMMountPoint}.
     *
     * @param url Invocation URL
     * @param schemaContext SchemaContext in which the path is to be interpreted in
     * @param mountPoint A mount point handle, if the URL is being interpreted relative to a mount point
     * @return {@link InstanceIdentifierContext}
     * @throws RestconfDocumentedException if the path cannot be resolved
     */
    private static InstanceIdentifierContext createIIdContext(final EffectiveModelContext schemaContext,
            final String url, final @Nullable DOMMountPoint mountPoint) {
        // First things first: an empty path means data invocation on SchemaContext
        if (url == null) {
            return mountPoint != null ? InstanceIdentifierContext.ofMountPointRoot(mountPoint, schemaContext)
                : InstanceIdentifierContext.ofLocalRoot(schemaContext);
        }

        final var result = YangInstanceIdentifierDeserializer.create(schemaContext, url);
        return InstanceIdentifierContext.ofPath(result.stack, result.node, result.path, mountPoint);
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

        final int mountIndex = identifier.indexOf(MOUNT);
        final String moduleNameAndRevision;
        if (mountIndex >= 0) {
            moduleNameAndRevision = identifier.substring(mountIndex + MOUNT.length()).replaceFirst("/", "");
        } else {
            moduleNameAndRevision = identifier;
        }

        final List<String> pathArgs = SLASH_SPLITTER.splitToList(moduleNameAndRevision);
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
        final Iterable<String> pathComponents = SLASH_SPLITTER.split(identifier);
        final Iterator<String> componentIter = pathComponents.iterator();
        if (!Iterables.contains(pathComponents, MOUNT)) {
            final String moduleName = validateAndGetModulName(componentIter);
            final Revision revision = validateAndGetRevision(componentIter);
            final Module module = schemaContext.findModule(moduleName, revision).orElse(null);
            return new SchemaExportContext(schemaContext, module, sourceProvider);
        } else {
            final StringBuilder pathBuilder = new StringBuilder();
            while (componentIter.hasNext()) {
                final String current = componentIter.next();

                if (MOUNT.equals(current)) {
                    pathBuilder.append('/').append(MOUNT);
                    break;
                }

                if (pathBuilder.length() != 0) {
                    pathBuilder.append('/');
                }

                pathBuilder.append(current);
            }
            final InstanceIdentifierContext point = toInstanceIdentifier(pathBuilder.toString(), schemaContext,
                requireNonNull(domMountPointService));
            final String moduleName = validateAndGetModulName(componentIter);
            final Revision revision = validateAndGetRevision(componentIter);
            final EffectiveModelContext context = coerceModelContext(point.getMountPoint());
            final Module module = context.findModule(moduleName, revision).orElse(null);
            return new SchemaExportContext(context, module, sourceProvider);
        }
    }

    public static YangInstanceIdentifier parserPatchTarget(final InstanceIdentifierContext context,
            final String target) {
        final var schemaContext = context.getSchemaContext();
        final var urlPath = context.getInstanceIdentifier();
        final String targetUrl;
        if (urlPath.isEmpty()) {
            targetUrl = target.startsWith("/") ? target.substring(1) : target;
        } else {
            targetUrl = IdentifierCodec.serialize(urlPath, schemaContext) + target;
        }

        return toInstanceIdentifier(targetUrl, schemaContext, null).getInstanceIdentifier();
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
            name.isEmpty() || !YangNames.IDENTIFIER_START.matches(name.charAt(0)),
            "Identifier must start with character from set 'a-zA-Z_", ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
        RestconfDocumentedException.throwIf(name.toUpperCase(Locale.ROOT).startsWith("XML"),
            "Identifier must NOT start with XML ignore case.", ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
        RestconfDocumentedException.throwIf(
            YangNames.NOT_IDENTIFIER_PART.matchesAnyOf(name.substring(1)),
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
