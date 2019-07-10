/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import static java.util.Objects.requireNonNull;
import static org.opendaylight.restconf.nb.rfc8040.rests.utils.RestconfStreamsConstants.CREATE_NOTIFICATION_STREAM;
import static org.opendaylight.restconf.nb.rfc8040.rests.utils.RestconfStreamsConstants.STREAM_ACCESS_PATH_PART;
import static org.opendaylight.restconf.nb.rfc8040.rests.utils.RestconfStreamsConstants.STREAM_LOCATION_PATH_PART;
import static org.opendaylight.restconf.nb.rfc8040.rests.utils.RestconfStreamsConstants.STREAM_PATH;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import javax.ws.rs.Path;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMActionResult;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.context.NormalizedNodeContext;
import org.opendaylight.restconf.common.context.WriterParameters;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorTag;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorType;
import org.opendaylight.restconf.common.patch.PatchContext;
import org.opendaylight.restconf.common.patch.PatchStatusContext;
import org.opendaylight.restconf.nb.rfc8040.handlers.ActionServiceHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.DOMMountPointServiceHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.SchemaContextHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.TransactionChainHandler;
import org.opendaylight.restconf.nb.rfc8040.references.SchemaContextRef;
import org.opendaylight.restconf.nb.rfc8040.rests.services.api.RestconfDataService;
import org.opendaylight.restconf.nb.rfc8040.rests.services.api.RestconfStreamsSubscriptionService;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.TransactionVarsWrapper;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.DeleteDataTransactionUtil;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.PatchDataTransactionUtil;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.PostDataTransactionUtil;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.PutDataTransactionUtil;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.ReadDataTransactionUtil;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.RestconfDataServiceConstant;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.RestconfInvokeOperationsUtil;
import org.opendaylight.restconf.nb.rfc8040.utils.RestconfConstants;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.ParserIdentifier;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.ActionDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link RestconfDataService}.
 */
@Path("/")
public class RestconfDataServiceImpl implements RestconfDataService {

    private static final Logger LOG = LoggerFactory.getLogger(RestconfDataServiceImpl.class);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MMM-dd HH:mm:ss");

    private SchemaContextHandler schemaContextHandler;
    private TransactionChainHandler transactionChainHandler;
    private DOMMountPointServiceHandler mountPointServiceHandler;
    private volatile ActionServiceHandler actionServiceHandler;

    private final RestconfStreamsSubscriptionService delegRestconfSubscrService;

    public RestconfDataServiceImpl(final SchemaContextHandler schemaContextHandler,
        final TransactionChainHandler transactionChainHandler,
        final DOMMountPointServiceHandler mountPointServiceHandler,
        final RestconfStreamsSubscriptionService delegRestconfSubscrService,
        final ActionServiceHandler actionServiceHandler) {
        this.actionServiceHandler = Objects.requireNonNull(actionServiceHandler);
        this.schemaContextHandler = Objects.requireNonNull(schemaContextHandler);
        this.transactionChainHandler = Objects.requireNonNull(transactionChainHandler);
        this.mountPointServiceHandler = Objects.requireNonNull(mountPointServiceHandler);
        this.delegRestconfSubscrService = Objects.requireNonNull(delegRestconfSubscrService);
    }

    @Override
    public synchronized void updateHandlers(final Object... handlers) {
        for (final Object object : handlers) {
            if (object instanceof SchemaContextHandler) {
                schemaContextHandler = (SchemaContextHandler) object;
            } else if (object instanceof ActionServiceHandler) {
                actionServiceHandler = (ActionServiceHandler) object;
            } else if (object instanceof DOMMountPointServiceHandler) {
                mountPointServiceHandler = (DOMMountPointServiceHandler) object;
            } else if (object instanceof TransactionChainHandler) {
                transactionChainHandler = (TransactionChainHandler) object;
            }
        }
    }

    @Override
    public Response readData(final UriInfo uriInfo) {
        return readData(null, uriInfo);
    }

    @Override
    public Response readData(final String identifier, final UriInfo uriInfo) {
        final SchemaContextRef schemaContextRef = new SchemaContextRef(this.schemaContextHandler.get());
        final InstanceIdentifierContext<?> instanceIdentifier = ParserIdentifier.toInstanceIdentifier(
                identifier, schemaContextRef.get(), Optional.of(this.mountPointServiceHandler.get()));

        boolean withDefaUsed = false;
        String withDefa = null;

        for (final Entry<String, List<String>> entry : uriInfo.getQueryParameters().entrySet()) {
            switch (entry.getKey()) {
                case "with-defaults":
                    if (!withDefaUsed) {
                        withDefaUsed = true;
                        withDefa = entry.getValue().iterator().next();
                    } else {
                        throw new RestconfDocumentedException("With-defaults parameter can be used only once.");
                    }
                    break;
                default:
                    LOG.info("Unknown key : {}.", entry.getKey());
                    break;
            }
        }
        boolean tagged = false;
        if (withDefaUsed) {
            if ("report-all-tagged".equals(withDefa)) {
                tagged = true;
                withDefa = null;
            }
            if ("report-all".equals(withDefa)) {
                withDefa = null;
            }
        }

        final WriterParameters parameters = ReadDataTransactionUtil.parseUriParameters(
                instanceIdentifier, uriInfo, tagged);

        final DOMMountPoint mountPoint = instanceIdentifier.getMountPoint();
        final TransactionChainHandler localTransactionChainHandler;
        if (mountPoint == null) {
            localTransactionChainHandler = this.transactionChainHandler;
        } else {
            localTransactionChainHandler = transactionChainOfMountPoint(mountPoint);
        }

        final TransactionVarsWrapper transactionNode = new TransactionVarsWrapper(
                instanceIdentifier, mountPoint, localTransactionChainHandler);
        final NormalizedNode<?, ?> node =
                ReadDataTransactionUtil.readData(identifier, parameters.getContent(), transactionNode, withDefa,
                        schemaContextRef, uriInfo);
        if (identifier.contains(STREAM_PATH) && identifier.contains(STREAM_ACCESS_PATH_PART)
                && identifier.contains(STREAM_LOCATION_PATH_PART)) {
            final String value = (String) node.getValue();
            final String streamName = value.substring(
                    value.indexOf(CREATE_NOTIFICATION_STREAM + RestconfConstants.SLASH));
            this.delegRestconfSubscrService.subscribeToStream(streamName, uriInfo);
        }
        if (node == null) {
            throw new RestconfDocumentedException(
                    "Request could not be completed because the relevant data model content does not exist",
                    RestconfError.ErrorType.PROTOCOL,
                    RestconfError.ErrorTag.DATA_MISSING);
        }

        if (parameters.getContent().equals(RestconfDataServiceConstant.ReadData.ALL)
                    || parameters.getContent().equals(RestconfDataServiceConstant.ReadData.CONFIG)) {
            final QName type = node.getNodeType();
            return Response.status(200)
                    .entity(new NormalizedNodeContext(instanceIdentifier, node, parameters))
                    .header("ETag", '"' + type.getModule().getRevision().map(Revision::toString).orElse(null)
                        + type.getLocalName() + '"')
                    .header("Last-Modified", FORMATTER.format(LocalDateTime.now(Clock.systemUTC())))
                    .build();
        }

        return Response.status(200).entity(new NormalizedNodeContext(instanceIdentifier, node, parameters)).build();
    }

    @Override
    public Response putData(final String identifier, final NormalizedNodeContext payload, final UriInfo uriInfo) {
        requireNonNull(payload);

        boolean insertUsed = false;
        boolean pointUsed = false;
        String insert = null;
        String point = null;

        for (final Entry<String, List<String>> entry : uriInfo.getQueryParameters().entrySet()) {
            switch (entry.getKey()) {
                case "insert":
                    if (!insertUsed) {
                        insertUsed = true;
                        insert = entry.getValue().iterator().next();
                    } else {
                        throw new RestconfDocumentedException("Insert parameter can be used only once.");
                    }
                    break;
                case "point":
                    if (!pointUsed) {
                        pointUsed = true;
                        point = entry.getValue().iterator().next();
                    } else {
                        throw new RestconfDocumentedException("Point parameter can be used only once.");
                    }
                    break;
                default:
                    throw new RestconfDocumentedException("Bad parameter for post: " + entry.getKey());
            }
        }

        checkQueryParams(insertUsed, pointUsed, insert);

        final InstanceIdentifierContext<? extends SchemaNode> iid = payload
                .getInstanceIdentifierContext();

        PutDataTransactionUtil.validInputData(iid.getSchemaNode(), payload);
        PutDataTransactionUtil.validTopLevelNodeName(iid.getInstanceIdentifier(), payload);
        PutDataTransactionUtil.validateListKeysEqualityInPayloadAndUri(payload);

        final DOMMountPoint mountPoint = payload.getInstanceIdentifierContext().getMountPoint();
        final TransactionChainHandler localTransactionChainHandler;
        final SchemaContextRef ref;
        if (mountPoint == null) {
            localTransactionChainHandler = this.transactionChainHandler;
            ref = new SchemaContextRef(this.schemaContextHandler.get());
        } else {
            localTransactionChainHandler = transactionChainOfMountPoint(mountPoint);
            ref = new SchemaContextRef(mountPoint.getSchemaContext());
        }

        final TransactionVarsWrapper transactionNode = new TransactionVarsWrapper(
                payload.getInstanceIdentifierContext(), mountPoint, localTransactionChainHandler);
        return PutDataTransactionUtil.putData(payload, ref, transactionNode, insert, point);
    }

    private static void checkQueryParams(final boolean insertUsed, final boolean pointUsed, final String insert) {
        if (pointUsed && !insertUsed) {
            throw new RestconfDocumentedException("Point parameter can't be used without Insert parameter.");
        }
        if (pointUsed && (insert.equals("first") || insert.equals("last"))) {
            throw new RestconfDocumentedException(
                    "Point parameter can be used only with 'after' or 'before' values of Insert parameter.");
        }
    }

    @Override
    public Response postData(final String identifier, final NormalizedNodeContext payload, final UriInfo uriInfo) {
        return postData(payload, uriInfo);
    }

    @Override
    public Response postData(final NormalizedNodeContext payload, final UriInfo uriInfo) {
        requireNonNull(payload);
        if (payload.getInstanceIdentifierContext().getSchemaNode() instanceof ActionDefinition) {
            return invokeAction(payload, uriInfo);
        }
        boolean insertUsed = false;
        boolean pointUsed = false;
        String insert = null;
        String point = null;

        for (final Entry<String, List<String>> entry : uriInfo.getQueryParameters().entrySet()) {
            switch (entry.getKey()) {
                case "insert":
                    if (!insertUsed) {
                        insertUsed = true;
                        insert = entry.getValue().iterator().next();
                    } else {
                        throw new RestconfDocumentedException("Insert parameter can be used only once.");
                    }
                    break;
                case "point":
                    if (!pointUsed) {
                        pointUsed = true;
                        point = entry.getValue().iterator().next();
                    } else {
                        throw new RestconfDocumentedException("Point parameter can be used only once.");
                    }
                    break;
                default:
                    throw new RestconfDocumentedException("Bad parameter for post: " + entry.getKey());
            }
        }

        checkQueryParams(insertUsed, pointUsed, insert);

        final DOMMountPoint mountPoint = payload.getInstanceIdentifierContext().getMountPoint();
        final TransactionChainHandler localTransactionChainHandler;
        final SchemaContextRef ref;
        if (mountPoint == null) {
            localTransactionChainHandler = this.transactionChainHandler;
            ref = new SchemaContextRef(this.schemaContextHandler.get());
        } else {
            localTransactionChainHandler = transactionChainOfMountPoint(mountPoint);
            ref = new SchemaContextRef(mountPoint.getSchemaContext());
        }
        final TransactionVarsWrapper transactionNode = new TransactionVarsWrapper(
                payload.getInstanceIdentifierContext(), mountPoint, localTransactionChainHandler);
        return PostDataTransactionUtil.postData(uriInfo, payload, transactionNode, ref, insert, point);
    }

    @Override
    public Response deleteData(final String identifier) {
        final SchemaContextRef schemaContextRef = new SchemaContextRef(this.schemaContextHandler.get());
        final InstanceIdentifierContext<?> instanceIdentifier = ParserIdentifier.toInstanceIdentifier(
                identifier, schemaContextRef.get(), Optional.of(this.mountPointServiceHandler.get()));

        final DOMMountPoint mountPoint = instanceIdentifier.getMountPoint();
        final TransactionChainHandler localTransactionChainHandler;
        if (mountPoint == null) {
            localTransactionChainHandler = this.transactionChainHandler;
        } else {
            localTransactionChainHandler = transactionChainOfMountPoint(mountPoint);
        }

        final TransactionVarsWrapper transactionNode = new TransactionVarsWrapper(instanceIdentifier, mountPoint,
                localTransactionChainHandler);
        return DeleteDataTransactionUtil.deleteData(transactionNode);
    }

    @Override
    public PatchStatusContext patchData(final String identifier, final PatchContext context, final UriInfo uriInfo) {
        return patchData(context, uriInfo);
    }

    @Override
    public PatchStatusContext patchData(final PatchContext context, final UriInfo uriInfo) {
        final DOMMountPoint mountPoint = requireNonNull(context).getInstanceIdentifierContext().getMountPoint();

        final TransactionChainHandler localTransactionChainHandler;
        final SchemaContextRef ref;
        if (mountPoint == null) {
            localTransactionChainHandler = this.transactionChainHandler;
            ref = new SchemaContextRef(this.schemaContextHandler.get());
        } else {
            localTransactionChainHandler = transactionChainOfMountPoint(mountPoint);
            ref = new SchemaContextRef(mountPoint.getSchemaContext());
        }

        final TransactionVarsWrapper transactionNode = new TransactionVarsWrapper(
                context.getInstanceIdentifierContext(), mountPoint, localTransactionChainHandler);

        return PatchDataTransactionUtil.patchData(context, transactionNode, ref);
    }

    /**
     * Prepare transaction chain to access data of mount point.
     * @param mountPoint
     *            mount point reference
     * @return {@link TransactionChainHandler}
     */
    private static TransactionChainHandler transactionChainOfMountPoint(final @NonNull DOMMountPoint mountPoint) {
        final Optional<DOMDataBroker> domDataBrokerService = mountPoint.getService(DOMDataBroker.class);
        if (domDataBrokerService.isPresent()) {
            return new TransactionChainHandler(domDataBrokerService.get());
        }

        final String errMsg = "DOM data broker service isn't available for mount point " + mountPoint.getIdentifier();
        LOG.warn(errMsg);
        throw new RestconfDocumentedException(errMsg);
    }

    /**
     * Invoke Action operation.
     *
     * @param payload
     *             {@link NormalizedNodeContext} - the body of the operation
     * @param uriInfo
     *             URI info
     * @return {@link NormalizedNodeContext} wrapped in {@link Response}
     */
    public Response invokeAction(final NormalizedNodeContext payload, final UriInfo uriInfo) {
        final DOMMountPoint mountPoint = payload.getInstanceIdentifierContext().getMountPoint();
        final SchemaPath schemaPath = payload.getInstanceIdentifierContext().getSchemaNode().getPath();
        final YangInstanceIdentifier yangIIdContext = payload.getInstanceIdentifierContext().getInstanceIdentifier();

        if (yangIIdContext.isEmpty()) {
            if (!payload.getData().getNodeType().equals(RestconfDataServiceConstant.NETCONF_BASE_QNAME)) {
                throw new RestconfDocumentedException("Instance identifier need to contain at least one path argument",
                    ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE);
            }
        }
        DOMActionResult response;
        SchemaContextRef schemaContextRef;
        if (mountPoint != null) {
            response = RestconfInvokeOperationsUtil
                .invokeActionViaMountPoint(mountPoint, (ContainerNode) payload.getData(), schemaPath, yangIIdContext);
            schemaContextRef = new SchemaContextRef(mountPoint.getSchemaContext());
        } else {
            response = RestconfInvokeOperationsUtil
                .invokeAction((ContainerNode) payload.getData(), schemaPath, this.actionServiceHandler, yangIIdContext);
            schemaContextRef = new SchemaContextRef(this.schemaContextHandler.get());
        }
        final DOMActionResult result = RestconfInvokeOperationsUtil.checkActionResponse(response);

        ActionDefinition resultNodeSchema = null;
        ContainerNode resultData = null;
        if (result != null && result.getOutput().isPresent()) {
            resultData = result.getOutput().get();
            resultNodeSchema = (ActionDefinition) payload.getInstanceIdentifierContext().getSchemaNode();
        }

        if (resultData != null && resultData.getValue().isEmpty()) {
            throw new WebApplicationException(Response.Status.NO_CONTENT);
        } else {
            return Response.status(200).entity(new NormalizedNodeContext(
                new InstanceIdentifierContext<>(yangIIdContext, resultNodeSchema, mountPoint, schemaContextRef.get()),
                resultData)).build();
        }
    }
}
