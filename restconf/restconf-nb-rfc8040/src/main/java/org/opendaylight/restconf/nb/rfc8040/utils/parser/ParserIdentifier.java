/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.utils.parser;

import static java.util.Objects.requireNonNull;

import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import java.time.format.DateTimeParseException;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMYangTextSourceProvider;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorTag;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorType;
import org.opendaylight.restconf.common.schema.SchemaExportContext;
import org.opendaylight.restconf.nb.rfc8040.utils.RestconfConstants;
import org.opendaylight.restconf.nb.rfc8040.utils.validations.RestconfValidation;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextNode;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextTree;
import org.opendaylight.yangtools.yang.model.api.ActionDefinition;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.util.SchemaContextUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Util class for parsing identifier.
 */
public final class ParserIdentifier {

    private static final Logger LOG = LoggerFactory.getLogger(ParserIdentifier.class);

    private ParserIdentifier() {
        throw new UnsupportedOperationException("Util class.");
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
    public static InstanceIdentifierContext<?> toInstanceIdentifier(
            final String identifier,
            final SchemaContext schemaContext,
            final Optional<DOMMountPointService> mountPointService) {
        if (identifier != null && identifier.contains(RestconfConstants.MOUNT)) {
            if (!mountPointService.isPresent()) {
                throw new RestconfDocumentedException("Mount point service is not available");
            }
            final Iterator<String> pathsIt = Splitter.on("/" + RestconfConstants.MOUNT).split(identifier).iterator();

            final String mountPointId = pathsIt.next();
            final YangInstanceIdentifier mountYangInstanceIdentifier = IdentifierCodec
                .deserialize(mountPointId, schemaContext);
            final Optional<DOMMountPoint> mountPoint = mountPointService.get()
                .getMountPoint(mountYangInstanceIdentifier);

            if (!mountPoint.isPresent()) {
                throw new RestconfDocumentedException("Mount point does not exist.", ErrorType.PROTOCOL,
                    ErrorTag.DATA_MISSING);
            }
            final DOMMountPoint domMountPoint = mountPoint.get();
            final SchemaContext mountSchemaContext = domMountPoint.getSchemaContext();

            final String pathId = pathsIt.next().replaceFirst("/", "");
            YangInstanceIdentifier pathYIId = IdentifierCodec.deserialize(pathId, mountSchemaContext);
            return createIIdContext(pathYIId, mountSchemaContext, domMountPoint);
        } else {
            YangInstanceIdentifier pathYIId = IdentifierCodec.deserialize(identifier, schemaContext);
            return createIIdContext(pathYIId, schemaContext, null);
        }
    }

    /**
     * Method to create {@link InstanceIdentifierContext} from {@link YangInstanceIdentifier}
     * and {@link SchemaContext}, {@link DOMMountPoint}.
     *
     * @param yangIId
     *               - instance identifier
     * @param schemaContext
     *               - schema context
     * @param domMountPoint
     *               - dOMMountPoint
     * @return {@link InstanceIdentifierContext}
     */
    public static InstanceIdentifierContext<?> createIIdContext(final YangInstanceIdentifier yangIId,
        final SchemaContext schemaContext, final DOMMountPoint domMountPoint) {
        final Optional<DataSchemaContextNode<?>> child = DataSchemaContextTree.from(schemaContext).findChild(yangIId);
        if (child.isPresent()) {
            return new InstanceIdentifierContext<SchemaNode>(yangIId, child.get().getDataSchemaNode(), domMountPoint,
                schemaContext);
        }
        List<QName> qnames = yangIId.getPathArguments().stream()
            .filter(pathArgument -> pathArgument instanceof NodeIdentifier).map(PathArgument::getNodeType)
            .collect(Collectors.toList());
        final SchemaNode schemaNode = SchemaContextUtil.findNodeInSchemaContext(schemaContext, qnames);
        requireNonNull(schemaNode, "SchemaNode must not be null.");

        if (schemaNode instanceof ActionDefinition) {
            return new InstanceIdentifierContext<>(yangIId, (ActionDefinition) schemaNode, domMountPoint,
                schemaContext);
        }
        return new InstanceIdentifierContext<>(yangIId, (RpcDefinition) schemaNode, domMountPoint, schemaContext);
    }

    /**
     * Make {@link String} from {@link YangInstanceIdentifier}.
     *
     * @param instanceIdentifier    Instance identifier
     * @param schemaContext         Schema context
     * @return                      Yang instance identifier serialized to String
     */
    public static String stringFromYangInstanceIdentifier(final YangInstanceIdentifier instanceIdentifier,
            final SchemaContext schemaContext) {
        return IdentifierCodec.serialize(instanceIdentifier, schemaContext);
    }

    /**
     * Make a moduleName/Revision pair from identifier.
     *
     * @param identifier
     *             path parameter
     * @return {@link QName}
     */
    public static Entry<String, Revision> makeQNameFromIdentifier(final String identifier) {
        // check if more than one slash is not used as path separator
        if (identifier.contains(
                String.valueOf(RestconfConstants.SLASH).concat(String.valueOf(RestconfConstants.SLASH)))) {
            LOG.debug("URI has bad format. It should be \'moduleName/yyyy-MM-dd\' {}", identifier);
            throw new RestconfDocumentedException(
                    "URI has bad format. End of URI should be in format \'moduleName/yyyy-MM-dd\'", ErrorType.PROTOCOL,
                    ErrorTag.INVALID_VALUE);
        }

        final int mountIndex = identifier.indexOf(RestconfConstants.MOUNT);
        final String moduleNameAndRevision;
        if (mountIndex >= 0) {
            moduleNameAndRevision = identifier.substring(mountIndex + RestconfConstants.MOUNT.length())
                    .replaceFirst(String.valueOf(RestconfConstants.SLASH), "");
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
     *             {@link SchemaContext}
     * @param identifier
     *             path parameter
     * @param domMountPointService
     *             {@link DOMMountPointService}
     * @return {@link SchemaExportContext}
     */
    public static SchemaExportContext toSchemaExportContextFromIdentifier(final SchemaContext schemaContext,
            final String identifier, final DOMMountPointService domMountPointService,
            final DOMYangTextSourceProvider sourceProvider) {
        final Iterable<String> pathComponents = RestconfConstants.SLASH_SPLITTER.split(identifier);
        final Iterator<String> componentIter = pathComponents.iterator();
        if (!Iterables.contains(pathComponents, RestconfConstants.MOUNT)) {
            final String moduleName = RestconfValidation.validateAndGetModulName(componentIter);
            final Revision revision = RestconfValidation.validateAndGetRevision(componentIter);
            final Module module = schemaContext.findModule(moduleName, revision).orElse(null);
            return new SchemaExportContext(schemaContext, module, sourceProvider);
        } else {
            final StringBuilder pathBuilder = new StringBuilder();
            while (componentIter.hasNext()) {
                final String current = componentIter.next();

                if (RestconfConstants.MOUNT.equals(current)) {
                    pathBuilder.append("/");
                    pathBuilder.append(RestconfConstants.MOUNT);
                    break;
                }

                if (pathBuilder.length() != 0) {
                    pathBuilder.append("/");
                }

                pathBuilder.append(current);
            }
            final InstanceIdentifierContext<?> point = ParserIdentifier
                    .toInstanceIdentifier(pathBuilder.toString(), schemaContext, Optional.of(domMountPointService));
            final String moduleName = RestconfValidation.validateAndGetModulName(componentIter);
            final Revision revision = RestconfValidation.validateAndGetRevision(componentIter);
            final Module module = point.getMountPoint().getSchemaContext().findModule(moduleName, revision)
                    .orElse(null);
            return new SchemaExportContext(point.getMountPoint().getSchemaContext(), module, sourceProvider);
        }
    }
}
