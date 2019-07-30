/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.utils.parser;

import static com.google.common.base.Verify.verifyNotNull;

import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import java.time.format.DateTimeParseException;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import org.eclipse.jdt.annotation.Nullable;
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
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextNode;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextTree;
import org.opendaylight.yangtools.yang.model.api.ActionDefinition;
import org.opendaylight.yangtools.yang.model.api.ActionNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Util class for parsing identifier.
 */
public final class ParserIdentifier {

    private static final Logger LOG = LoggerFactory.getLogger(ParserIdentifier.class);
    private static final Splitter MP_SPLITTER = Splitter.on("/" + RestconfConstants.MOUNT);

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
        if (identifier == null || !identifier.contains(RestconfConstants.MOUNT)) {
            return createIIdContext(IdentifierCodec.deserialize(identifier, schemaContext), schemaContext, null);
        }
        if (!mountPointService.isPresent()) {
            throw new RestconfDocumentedException("Mount point service is not available");
        }

        final Iterator<String> pathsIt = MP_SPLITTER.split(identifier).iterator();
        final String mountPointId = pathsIt.next();
        final YangInstanceIdentifier mountPath = IdentifierCodec.deserialize(mountPointId, schemaContext);
        final DOMMountPoint mountPoint = mountPointService.get().getMountPoint(mountPath)
                .orElseThrow(() -> new RestconfDocumentedException("Mount point does not exist.",
                    ErrorType.PROTOCOL, ErrorTag.DATA_MISSING));

        final SchemaContext mountSchemaContext = mountPoint.getSchemaContext();
        final String pathId = pathsIt.next().replaceFirst("/", "");
        return createIIdContext(IdentifierCodec.deserialize(pathId, mountSchemaContext), mountSchemaContext,
            mountPoint);
    }

    /**
     * Method to create {@link InstanceIdentifierContext} from {@link YangInstanceIdentifier}
     * and {@link SchemaContext}, {@link DOMMountPoint}.
     *
     * @param urlPath PathArgument-based path interpretation of URL
     * @param schemaContext SchemaContext in which the path is to be interpreted in
     * @param mountPoint A mount point handle, if the path is being interpreted relative to a mount point
     * @return {@link InstanceIdentifierContext}
     */
    private static InstanceIdentifierContext<?> createIIdContext(final YangInstanceIdentifier urlPath,
            final SchemaContext schemaContext, final @Nullable DOMMountPoint mountPoint) {
        // First things first: an empty path means data invocation on SchemaContext
        if (urlPath.isEmpty()) {
            return new InstanceIdentifierContext<>(urlPath, schemaContext, mountPoint, schemaContext);
        }

        // Peel the last component and locate the parent data node, empty path resolves to SchemaContext
        final DataSchemaContextNode<?> parent = DataSchemaContextTree.from(schemaContext)
                .findChild(verifyNotNull(urlPath.getParent()))
                .orElseThrow(
                    // Parent data node is not present, this is not a valid location.
                    () -> new RestconfDocumentedException("Parent of " + urlPath + " not found",
                        ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE));

        // Now try to resolve the last component as a data item...
        final DataSchemaContextNode<?> data = parent.getChild(urlPath.getLastPathArgument());
        if (data != null) {
            return new InstanceIdentifierContext<>(urlPath, data.getDataSchemaNode(), mountPoint, schemaContext);
        }

        // ... otherwise this has to be an operation invocation. RPCs cannot be defined anywhere but schema root,
        // actions can reside everywhere else (and SchemaContext reports them empty)
        final QName qname = urlPath.getLastPathArgument().getNodeType();
        final DataSchemaNode parentSchema = parent.getDataSchemaNode();
        if (parentSchema instanceof SchemaContext) {
            for (final RpcDefinition rpc : ((SchemaContext) parentSchema).getOperations()) {
                if (qname.equals(rpc.getQName())) {
                    return new InstanceIdentifierContext<>(urlPath, rpc, mountPoint, schemaContext);
                }
            }
        }
        if (parentSchema instanceof ActionNodeContainer) {
            for (final ActionDefinition action : ((ActionNodeContainer) parentSchema).getActions()) {
                if (qname.equals(action.getQName())) {
                    return new InstanceIdentifierContext<>(urlPath, action, mountPoint, schemaContext);
                }
            }
        }

        // No luck: even if we found the parent, we did not locate a data, nor RPC, nor action node, hence the URL
        //          is deemed invalid
        throw new RestconfDocumentedException("Context for " + urlPath + " not found", ErrorType.PROTOCOL,
            ErrorTag.INVALID_VALUE);
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
