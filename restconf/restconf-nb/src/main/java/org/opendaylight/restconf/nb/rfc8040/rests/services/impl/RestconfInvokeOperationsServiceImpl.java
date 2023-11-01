/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import javax.ws.rs.Consumes;
import javax.ws.rs.Encoded;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfFuture;
import org.opendaylight.restconf.nb.rfc8040.MediaTypes;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindContext;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindProvider;
import org.opendaylight.restconf.nb.rfc8040.databind.JsonOperationInputBody;
import org.opendaylight.restconf.nb.rfc8040.databind.OperationInputBody;
import org.opendaylight.restconf.nb.rfc8040.databind.XmlOperationInputBody;
import org.opendaylight.restconf.nb.rfc8040.legacy.InstanceIdentifierContext;
import org.opendaylight.restconf.nb.rfc8040.legacy.NormalizedNodePayload;
import org.opendaylight.restconf.nb.rfc8040.streams.ListenersBroker;
import org.opendaylight.yang.gen.v1.urn.opendaylight.device.notification.rev221106.SubscribeDeviceNotification;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.remote.rev140114.CreateDataChangeEventSubscription;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.remote.rev140114.CreateNotificationStream;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An operation resource represents a protocol operation defined with the YANG {@code rpc} statement. It is invoked
 * using a POST method on the operation resource.
 */
@Path("/")
public final class RestconfInvokeOperationsServiceImpl {
    private static final Logger LOG = LoggerFactory.getLogger(RestconfInvokeOperationsServiceImpl.class);

    private final DatabindProvider databindProvider;
    private final MdsalRestconfServer server;
    @Deprecated(forRemoval = true)
    private final DOMMountPointService mountPointService;
    private final ListenersBroker listenersBroker;

    public RestconfInvokeOperationsServiceImpl(final DatabindProvider databindProvider,
            final MdsalRestconfServer server, final DOMMountPointService mountPointService,
            final ListenersBroker listenersBroker) {
        this.databindProvider = requireNonNull(databindProvider);
        this.server = requireNonNull(server);
        this.mountPointService = requireNonNull(mountPointService);
        this.listenersBroker = requireNonNull(listenersBroker);
    }

    /**
     * Invoke RPC operation.
     *
     * @param identifier module name and rpc identifier string for the desired operation
     * @param body the body of the operation
     * @param uriInfo URI info
     * @param ar {@link AsyncResponse} which needs to be completed with a {@link NormalizedNodePayload} output
     */
    @POST
    // FIXME: identifier is just a *single* QName
    @Path("/operations/{identifier:.+}")
    @Consumes({
        MediaTypes.APPLICATION_YANG_DATA_XML,
        MediaType.APPLICATION_XML,
        MediaType.TEXT_XML
    })
    @Produces({
        MediaTypes.APPLICATION_YANG_DATA_JSON,
        MediaTypes.APPLICATION_YANG_DATA_XML,
        MediaType.APPLICATION_JSON,
        MediaType.APPLICATION_XML,
        MediaType.TEXT_XML
    })
    public void invokeRpcXML(@Encoded @PathParam("identifier") final String identifier, final InputStream body,
            @Context final UriInfo uriInfo, @Suspended final AsyncResponse ar) {
        try (var xmlBody = new XmlOperationInputBody(body)) {
            invokeRpc(identifier, uriInfo, ar, xmlBody);
        }
    }

    /**
     * Invoke RPC operation.
     *
     * @param identifier module name and rpc identifier string for the desired operation
     * @param body the body of the operation
     * @param uriInfo URI info
     * @param ar {@link AsyncResponse} which needs to be completed with a {@link NormalizedNodePayload} output
     */
    @POST
    // FIXME: identifier is just a *single* QName
    @Path("/operations/{identifier:.+}")
    @Consumes({
        MediaTypes.APPLICATION_YANG_DATA_JSON,
        MediaType.APPLICATION_JSON,
    })
    @Produces({
        MediaTypes.APPLICATION_YANG_DATA_JSON,
        MediaTypes.APPLICATION_YANG_DATA_XML,
        MediaType.APPLICATION_JSON,
        MediaType.APPLICATION_XML,
        MediaType.TEXT_XML
    })
    public void invokeRpcJSON(@Encoded @PathParam("identifier") final String identifier, final InputStream body,
            @Context final UriInfo uriInfo, @Suspended final AsyncResponse ar) {
        try (var jsonBody = new JsonOperationInputBody(body)) {
            invokeRpc(identifier, uriInfo, ar, jsonBody);
        }
    }

    private void invokeRpc(final String identifier, final UriInfo uriInfo, final AsyncResponse ar,
            final OperationInputBody body) {
        final var databind = databindProvider.currentContext();
        final var reqPath = server.bindRequestPath(databind, identifier);

        final ContainerNode input;
        try {
            input = body.toContainerNode(reqPath.inference());
        } catch (IOException e) {
            LOG.debug("Error reading input", e);
            throw new RestconfDocumentedException("Error parsing input: " + e.getMessage(), ErrorType.PROTOCOL,
                    ErrorTag.MALFORMED_MESSAGE, e);
        }

        hackInvokeRpc(databind, reqPath, uriInfo, input).addCallback(new JaxRsRestconfCallback<>(ar) {
            @Override
            Response transform(final Optional<ContainerNode> result) {
                return result
                    .filter(output -> !output.isEmpty())
                    .map(output -> Response.ok().entity(new NormalizedNodePayload(reqPath.inference(), output)).build())
                    .orElseGet(() -> Response.noContent().build());
            }
        });
    }

    private RestconfFuture<Optional<ContainerNode>> hackInvokeRpc(final DatabindContext localDatabind,
            final InstanceIdentifierContext reqPath, final UriInfo uriInfo, final ContainerNode input) {
        // RPC type
        final var type = reqPath.getSchemaNode().getQName();
        final var mountPoint = reqPath.getMountPoint();
        if (mountPoint == null) {
            // Hacked-up integration of streams
            if (CreateDataChangeEventSubscription.QNAME.equals(type)) {
                return CreateStreamUtil.createDataChangeNotifiStream(listenersBroker, input,
                    localDatabind.modelContext());
            } else if (CreateNotificationStream.QNAME.equals(type)) {
                return CreateStreamUtil.createNotificationStream(listenersBroker, input,
                    localDatabind.modelContext());
            } else if (SubscribeDeviceNotification.QNAME.equals(type)) {
                return CreateStreamUtil.createDeviceNotificationListener(listenersBroker, input,
                    listenersBroker.prepareUriByStreamName(uriInfo, "").toString(), mountPointService);
            }
        }

        return server.getRestconfStrategy(reqPath.getSchemaContext(), mountPoint).invokeRpc(type, input);
    }
}
