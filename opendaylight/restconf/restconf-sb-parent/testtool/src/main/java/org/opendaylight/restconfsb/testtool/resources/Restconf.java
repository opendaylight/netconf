/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.testtool.resources;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import org.glassfish.jersey.media.sse.EventOutput;
import org.glassfish.jersey.media.sse.OutboundEvent;
import org.glassfish.jersey.media.sse.SseFeature;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadOnlyTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMDataWriteTransaction;
import org.opendaylight.restconfsb.communicator.impl.common.YangInstanceIdentifierToUrlCodec;
import org.opendaylight.restconfsb.testtool.custom.rest.PATCH;
import org.opendaylight.restconfsb.testtool.datastore.Datastore;
import org.opendaylight.restconfsb.testtool.rpc.NoopRpcHandler;
import org.opendaylight.restconfsb.testtool.rpc.RpcHandler;
import org.opendaylight.restconfsb.testtool.rpc.RpcHandlerImpl;
import org.opendaylight.restconfsb.testtool.xml.RequestContext;
import org.opendaylight.restconfsb.testtool.xml.notification.RestconfNotifications;
import org.opendaylight.restconfsb.testtool.xml.notification.files.streams.Notification;
import org.opendaylight.restconfsb.testtool.xml.notification.files.streams.Stream;
import org.opendaylight.restconfsb.testtool.xml.notification.files.streams.Streams;
import org.opendaylight.restconfsb.testtool.xml.rpc.Rpcs;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("restconf")
public class Restconf {

    private static final Logger LOG = LoggerFactory.getLogger(Restconf.class);
    private static final String YANG_API_XML = "application/yang.api+xml";
    private static final String YANG_DATA_XML = "application/yang.data+xml";
    private static final String YANG_OPERATION_XML = "application/yang.operation+xml";
    private static final ThreadLocal<SimpleDateFormat> DATE_FORMAT = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        }
    };
    private final YangInstanceIdentifierToUrlCodec codec;
    private final Datastore datastore;
    private final ScheduledExecutorService notificationExecutor = Executors.newSingleThreadScheduledExecutor();
    private final Multimap<String, Notification> streamMap = HashMultimap.create();
    private final RpcHandler rpcHandler;
    private final int port;
    private final String protocol;
    private Streams streams;

    public Restconf(final Datastore datastore, final Optional<File> notificationFile, final Optional<File> rpcFile, final int port, final boolean isSecured) {
        this.datastore = datastore;
        codec = new YangInstanceIdentifierToUrlCodec(datastore.getSchemaContext());
        this.port = port;

        if (isSecured) {
            protocol = "https";
        } else {
            protocol = "http";
        }

        if(notificationFile.isPresent()){
            streams =  new RestconfNotifications(notificationFile.get()).getStreams();
            for (final Stream str : streams.getStream()) {
                for (final Notification notif : str.getNotification()){
                    streamMap.put(str.getName(), notif);
                }
            }
        }
        if (rpcFile.isPresent()) {
            final Rpcs rpcs = Rpcs.loadRpcs(rpcFile.get());
            rpcHandler = new RpcHandlerImpl(datastore.getSchemaContext(), rpcs);
        } else {
            rpcHandler = new NoopRpcHandler();
        }
    }

    @GET
    @Path("/data/{identifier:.+}")
    @Produces(YANG_DATA_XML)
    public synchronized RequestContext getData(@PathParam("identifier") final String identifier, @QueryParam("content") final String dataStoreKind) {
        final LogicalDatastoreType datastoreType = getDatastoreType(dataStoreKind);
        final YangInstanceIdentifier path = codec.deserialize(identifier);

        final Optional<NormalizedNode<?, ?>> data;
        try {
            final DOMDataReadOnlyTransaction readTx = datastore.getDataBroker().newReadOnlyTransaction();
            data = readTx.read(datastoreType, path).checkedGet();
            if (!data.isPresent()) {
                LOG.warn("Response: {} Data doesn't exist for path {}", Response.Status.NOT_FOUND.getStatusCode(), path);
                throw new WebApplicationException("Data doesn't exist for path " + path, Response.Status.NOT_FOUND);
            }
            LOG.info("Response: {} {}", Response.Status.OK.getStatusCode(), Response.Status.OK);
            return new RequestContext(path, data.get());
        } catch (final ReadFailedException e) {
            LOG.error("Response: {} Failed to read data", Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), e);
            throw new IllegalStateException(e);
        }
    }

    @PUT
    @Path("/data/{identifier:.+}")
    @Consumes(YANG_DATA_XML)
    public synchronized Response updateConfigurationData(final RequestContext payload) {
        final LogicalDatastoreType datastoreType = LogicalDatastoreType.CONFIGURATION;
        final Response.Status status;
        final boolean exist;
        final DOMDataReadWriteTransaction readWriteTx;
        try {
            readWriteTx = datastore.getDataBroker().newReadWriteTransaction();
            exist = readWriteTx.exists(datastoreType, payload.getPath()).checkedGet();
            if (!exist) {
                status = Response.Status.CREATED;
            } else {
                status = Response.Status.OK;
            }
        } catch (final ReadFailedException e) {
            LOG.error("Response : {} Failed to check if data exist {}", Response.Status.INTERNAL_SERVER_ERROR, e);
            throw new WebApplicationException("Failed to check if data exist", e, Response.Status.INTERNAL_SERVER_ERROR);
        }

        final YangInstanceIdentifier path = payload.getPath();
        final NormalizedNode<?, ?> data = payload.getData();

        if (path.getLastPathArgument() instanceof YangInstanceIdentifier.NodeIdentifierWithPredicates) {
            final MapEntryNode listEntry = getListEntryNode(path, (MapNode) data);
            createListMixinNode(readWriteTx, path, datastoreType);
            readWriteTx.put(datastoreType, path, listEntry);
        } else {
            readWriteTx.put(datastoreType, path, data);
        }
        try {
            readWriteTx.submit().checkedGet();
        } catch (final TransactionCommitFailedException e) {
            LOG.error("Response : {} Failed to write data {}", Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), e);
            throw new WebApplicationException("Failed to write data", e, Response.Status.INTERNAL_SERVER_ERROR);
        }
        LOG.info("Response : {} {}", status.getStatusCode(), status);
        return Response.status(status).build();
    }

    private MapEntryNode getListEntryNode(final YangInstanceIdentifier path, final MapNode listNode) {
        final Optional<MapEntryNode> listEntry = listNode.getChild((YangInstanceIdentifier.NodeIdentifierWithPredicates) path.getLastPathArgument());
        Preconditions.checkState(listEntry.isPresent());
        return listEntry.get();
    }

    @PATCH
    @Path("/data/{identifier:.+}")
    @Consumes(YANG_DATA_XML)
    public synchronized Response updateSubresource(final RequestContext payload) {
        final LogicalDatastoreType datastoreType = LogicalDatastoreType.CONFIGURATION;
        final DOMDataReadWriteTransaction tx = datastore.getDataBroker().newReadWriteTransaction();
        try {
            if (!tx.exists(datastoreType, payload.getPath()).checkedGet()) {
                LOG.warn("Response : Resource must exist to patch it {}", Response.Status.NOT_FOUND.getStatusCode());
                throw new WebApplicationException("Resource must exist to patch it", Response.Status.NOT_FOUND);
            }
            if (payload.getPath().getLastPathArgument() instanceof YangInstanceIdentifier.NodeIdentifierWithPredicates) {
                final MapEntryNode listEntryNode = getListEntryNode(payload.getPath(), (MapNode) payload.getData());
                tx.merge(datastoreType, payload.getPath(), listEntryNode);
            } else {
                tx.merge(datastoreType, payload.getPath(), payload.getData());
            }
            tx.submit().checkedGet();
            LOG.info("Response : {} {}", Response.Status.OK.getStatusCode(), Response.Status.OK);
            return Response.status(Response.Status.OK).build();
        } catch (TransactionCommitFailedException | ReadFailedException e) {
            LOG.error("Response : failed to merge data {} {}", Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DELETE
    @Path("/data/{identifier:.+}")
    public synchronized Response deleteConfigurationData(final RequestContext payload) {
        final LogicalDatastoreType datastoreType = LogicalDatastoreType.CONFIGURATION;
        final DOMDataReadWriteTransaction tx = datastore.getDataBroker().newReadWriteTransaction();
        Response.Status status = Response.Status.NO_CONTENT;

        try {
            final boolean exist = tx.exists(datastoreType, payload.getPath()).checkedGet();
            if (!exist) {
                status = Response.Status.NOT_FOUND;
            } else {
                tx.delete(datastoreType, payload.getPath());
                tx.submit().checkedGet();
            }
        } catch (final ReadFailedException e) {
            throw new WebApplicationException("Failed to check if data exist", e, Response.Status.INTERNAL_SERVER_ERROR);
        } catch (final TransactionCommitFailedException e) {
            LOG.error("Response: failed to delete {} {} on path {}", Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), Response.Status.INTERNAL_SERVER_ERROR, payload.getPath(),e);
        }
        LOG.info("Response: {} {}", Response.Status.NO_CONTENT.getStatusCode(), Response.Status.NO_CONTENT);
        return Response.status(status).build();
    }

    @POST
    @Path("/operations/{identifier:.+}")
    @Consumes(YANG_OPERATION_XML)
    @Produces(YANG_OPERATION_XML)
    public synchronized String invokeRpc(@PathParam("identifier") final String identifier, final NormalizedNode<?, ?> body) {
        return rpcHandler.invokeRpc(identifier, body);
    }

    @GET
    @Path("/data/ietf-yang-library:modules")
    @Produces(YANG_DATA_XML)
    public String getModules() {
        final StringBuilder builder = new StringBuilder("<modules xmlns=\"urn:ietf:params:xml:ns:yang:ietf-yang-library\">\n");
        final Set<Module> modules = datastore.getSchemaContext().getModules();
        for (final Module module : modules) {
            builder.append("<module>\n");
            builder.append("<name>").append(module.getName()).append("</name>\n");
            builder.append("<revision>").append(module.getQNameModule().getFormattedRevision()).append("</revision>\n");
            builder.append("<namespace>").append(module.getQNameModule().getNamespace().toString()).append("</namespace>\n");
            builder.append("</module>\n");
        }
        builder.append("</modules>");
        return builder.toString();
    }

    @GET
    @Path("/data/ietf-restconf-monitoring:restconf-state/streams")
    @Produces(YANG_DATA_XML)
    public String getStreams() {
        LOG.info("GET: {}", "/data/ietf-restconf-monitoring:restconf-state/streams");
        final StringBuilder builder = new StringBuilder("");

        if (streams == null) {
            throw new WebApplicationException("There are no streams present.", Response.Status.NOT_FOUND);
        } else {
            builder.append("<streams xmlns=\"").append("urn:ietf:params:xml:ns:yang:ietf-restconf-monitoring").append("\">");
            for (final Stream str : streams.getStream()) {
                builder.append("<stream>");
                builder.append("<name>").append(str.getName()).append("</name>");
                builder.append("<description>").append("default").append("</description>");
                builder.append("<replay-support>").append("true").append("</replay-support>");
                builder.append("<replay-log-creation-time>").append(DATE_FORMAT.get().format(new Date())).append("</replay-log-creation-time>");
                builder.append("<encoding>");
                builder.append("<type>");
                builder.append("xml");
                builder.append("</type>");
                builder.append("<events>")
                        .append(protocol).append("://localhost:").append(port).append("/restconf/streams/stream/").append(str.getName());
                builder.append("</events>");
                builder.append("</encoding>");
                builder.append("</stream>");
            }
            builder.append("</streams>");
            return builder.toString();
        }
    }

    @GET
    @Path("/streams/stream/{name}")
    @Produces(SseFeature.SERVER_SENT_EVENTS)
    public EventOutput getServerSentEvents(@PathParam("name") final String streamName) {
        final EventOutput eventOutput = new EventOutput();
        for (final Notification ntf : streamMap.get(streamName)) {
            notificationExecutor.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    try {
                        final OutboundEvent.Builder eventBuilder = new OutboundEvent.Builder();
                        String content = ntf.getContent();
                        content = content.replace("#eventtime#", DATE_FORMAT.get().format(new Date()));
                        content = content.replaceAll("\\n|\\r", "");
                        eventBuilder.data(String.class, content);
                        final OutboundEvent event = eventBuilder.build();
                        eventOutput.write(event);
                    } catch (final IOException e) {
                        throw new IllegalStateException(
                                "Error when writing the event.", e);
                    }
                }

            }, ntf.getDelay(), ntf.getPeriod(), TimeUnit.SECONDS);
        }
        return eventOutput;
    }

    private static void createListMixinNode(final DOMDataWriteTransaction tx, final YangInstanceIdentifier path, final LogicalDatastoreType datastore) {
        final List<YangInstanceIdentifier.PathArgument> pathArguments = path.getPathArguments();
        final MapNode listMixin = Builders.mapBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(path.getLastPathArgument().getNodeType()))
                .build();
        tx.merge(datastore, YangInstanceIdentifier.create(pathArguments.subList(0, pathArguments.size() - 1)), listMixin);
    }

    private static LogicalDatastoreType getDatastoreType(final String dataStoreKind) {
        if ("config".equals(dataStoreKind)) {
            return LogicalDatastoreType.CONFIGURATION;
        } else if ("noconfig".equals(dataStoreKind)) {
            return LogicalDatastoreType.OPERATIONAL;
        }
        throw new UnsupportedOperationException("unknown type of DataStore");
    }
}
