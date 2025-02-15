/*
 * Copyright (c) 2025 Paxet s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.odl.device.notification;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gson.stream.JsonWriter;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.xpath.XPathExpressionException;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMEvent;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointListener;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.mdsal.dom.api.DOMNotificationListener;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.mdsal.spi.AbstractNotificationSource;
import org.opendaylight.restconf.mdsal.spi.NotificationFormatter;
import org.opendaylight.restconf.mdsal.spi.NotificationFormatterFactory;
import org.opendaylight.restconf.server.api.EventStreamGetParams;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.opendaylight.restconf.server.spi.TextParameters;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.optional.rev221225.NetconfNodeAugmentedOptional;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.optional.rev221225.netconf.node.augmented.optional.fields.Notifications;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yang.svc.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.rev170126.YangModuleInfoImpl;
import org.opendaylight.yangtools.binding.DataObjectIdentifier;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeWriter;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONCodecFactorySupplier;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.xml.XMLStreamNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.stmt.NotificationEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@Component(immediate = true)
public final class DeviceNotificationAggregate extends AbstractNotificationSource implements DOMMountPointListener,
    AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(DeviceNotificationAggregate.class);
    private static final String STREAM_NAME = "netconf-stream-device-notifications";
    private static final String STREAM_DESCRIPTION = "Notifications from NETCONF device received via NETCONF stream. "
        + "Location URLs are relative to RESTCONF base path.";
    private static final QName TOPOLOGY_ID_QNAME = QName.create(Node.QNAME, "topology-id").intern();
    private static final QName NODE_ID_QNAME = QName.create(Node.QNAME, "node-id").intern();
    public static final ImmutableMap<RestconfStream.EncodingName, NotificationFormatterFactory> ENCODINGS =
        ImmutableMap.of(
            RestconfStream.EncodingName.RFC8040_XML, XMLNodeNotificationFormatter.FACTORY,
            RestconfStream.EncodingName.RFC8040_JSON, JSONNodeNotificationFormatter.FACTORY);
    private final DOMMountPointService mountPointService;
    private final RestconfStream.Registry streamRegistry;
    private final DataBroker dataBroker;
    private Registration registration;
    private RestconfStream.Sink<DOMNotification> restconfSink;

    @Inject
    @Activate
    @SuppressFBWarnings(value = "MC_OVERRIDABLE_METHOD_CALL_IN_CONSTRUCTOR",
        justification = "MP listener registration of 'this'")
    public DeviceNotificationAggregate(@Reference final DOMMountPointService mountPointService,
        @Reference final RestconfStream.Registry streamRegistry,
        @Reference final DataBroker dataBroker) {
        super(ENCODINGS);
        this.mountPointService = requireNonNull(mountPointService);
        this.streamRegistry = requireNonNull(streamRegistry);
        this.dataBroker = dataBroker;
        init();
    }

    private void init() {
        LOG.debug("Creating stream {}", STREAM_NAME);
        streamRegistry.createStream(STREAM_NAME, this, STREAM_DESCRIPTION);
        final var stream = this.streamRegistry.lookupStream(STREAM_NAME);
        if (stream == null) {
            LOG.error("Failed to create stream {}.", STREAM_NAME);
            return;
        }
        LOG.debug("Subscribing to stream {}", stream.name());
        // this will ensure that:
        // - the stream is never removed
        // - this.start(RestconfStream.Sink<DOMNotification> sink) is called before mountpoint listener registration
        try {
            stream.addSubscriber(new RestconfStream.Sender() {
                @Override
                public void sendDataMessage(String data) {
                    LOG.trace("Observed node notification: {}", data);
                }

                @Override
                public void endOfStream() {
                    LOG.info("End of stream {}", STREAM_NAME);
                }
            }, RestconfStream.EncodingName.RFC8040_JSON, new EventStreamGetParams(null, null, null, null, null, null,
                null));
        } catch (UnsupportedEncodingException | XPathExpressionException e) {
            LOG.error("Error while adding subscriber to stream {}. The stream will not work.", STREAM_NAME, e);
            return;
        }
        registration = this.mountPointService.registerProvisionListener(this);
    }

    @Override
    public void onMountPointCreated(DOMMountPoint mountPoint) {
        LOG.trace("onMountPointCreated: {}", mountPoint.getIdentifier());
        final var devicePath = mountPoint.getIdentifier();

        final var optTopologyId = getTopologyId(devicePath);
        final var optNodeId = getNodeId(devicePath);
        if (optTopologyId.isEmpty() || optNodeId.isEmpty()) {
            LOG.debug("Mount point {} is not a topology node, ignoring", devicePath);
            return;
        }

        DataObjectIdentifier<Notifications> notificationObjId = DataObjectIdentifier.builder(NetworkTopology.class)
            .child(Topology.class, new TopologyKey(new TopologyId(optTopologyId.orElseThrow())))
            .child(Node.class, new NodeKey(new NodeId(optNodeId.orElseThrow())))
            .augmentation(NetconfNodeAugmentedOptional.class)
            .child(Notifications.class)
            .build();
        try (var roTx = dataBroker.newReadOnlyTransaction()) {
            final var optNotifications = roTx.read(LogicalDatastoreType.CONFIGURATION, notificationObjId);
            optNotifications.addCallback(new FutureCallback<>() {
                @Override
                public void onSuccess(Optional<Notifications> result) {
                    if (!result.map(Notifications::getNetconfStreamNotificationsEnabled).orElse(false)) {
                        LOG.debug("Node {} does not have netconf stream notifications enabled, ignoring", devicePath);
                        return;
                    }

                    final var optSchema = mountPoint.getService(DOMSchemaService.class);
                    if (optSchema.isEmpty()) {
                        LOG.info("Mount point {} does not have a DOMSchemaService, ignoring", devicePath);
                        return;
                    }

                    final var optNotification = mountPoint.getService(DOMNotificationService.class);
                    if (optNotification.isEmpty()) {
                        LOG.info("Mount point {} does not have a DOMNotificationService, ignoring", devicePath);
                        return;
                    }

                    // Find all notifications
                    final var modelContext = optSchema.orElseThrow().getGlobalContext();
                    final var paths = modelContext.getModuleStatements().values().stream()
                        .flatMap(module -> module.streamEffectiveSubstatements(NotificationEffectiveStatement.class))
                        .map(notification -> SchemaNodeIdentifier.Absolute.of(notification.argument()))
                        .collect(ImmutableSet.toImmutableSet());
                    if (paths.isEmpty()) {
                        LOG.info("Mount point {} does not advertise any YANG notifications, ignoring", devicePath);
                        return;
                    }

                    // no need to close registration because it will be cleaned when mountpoint is removed
                    optNotification.orElseThrow().registerNotificationListener(new NodeAwareListener(restconfSink,
                        () -> modelContext, optNodeId.orElseThrow()), paths);
                }

                @Override
                public void onFailure(Throwable throwable) {
                    LOG.error("Error while reading notifications settings {}. Ignoring mount point {}",
                        notificationObjId, devicePath, throwable);
                }
            }, MoreExecutors.directExecutor());
        }
    }

    private static Optional<String> getTopologyId(final YangInstanceIdentifier path) {
        var optTopologyPathArg = path.getPathArguments().stream()
            .filter(arg -> Topology.QNAME.isEqualWithoutRevision(arg.getNodeType()))
            .filter(arg -> arg instanceof YangInstanceIdentifier.NodeIdentifierWithPredicates)
            .findFirst();
        if (optTopologyPathArg.isPresent()
            && optTopologyPathArg.orElseThrow() instanceof YangInstanceIdentifier.NodeIdentifierWithPredicates
            topologyPathArg) {
            return Optional.ofNullable(topologyPathArg.getValue(TOPOLOGY_ID_QNAME, String.class));
        } else {
            return Optional.empty();
        }
    }

    private static Optional<String> getNodeId(final YangInstanceIdentifier path) {
        if (path.getLastPathArgument() instanceof YangInstanceIdentifier.NodeIdentifierWithPredicates nodePathArg) {
            return Optional.ofNullable(nodePathArg.getValue(NODE_ID_QNAME, String.class));
        } else {
            return Optional.empty();
        }
    }

    @Override
    public void onMountPointRemoved(YangInstanceIdentifier path) {
        LOG.trace("onMountPointRemoved: {}", path);
    }

    @Override
    protected @NonNull Registration start(RestconfStream.Sink<DOMNotification> sink) {
        // this is called when first subscriber is added to the stream - see this.init()
        LOG.trace("start, sink={}", sink);
        this.restconfSink = sink;
        return () -> {
            // No-op
        };
    }

    @Deactivate
    @Override
    public void close() throws Exception {
        if (registration != null) {
            registration.close();
        }
    }

    private static final class JSONNodeNotificationFormatter extends NotificationFormatter {

        private static final @NonNull String NOTIFICATION_NAME =
            YangModuleInfoImpl.getInstance().getName().getLocalName() + ":notification";
        @VisibleForTesting
        static final JSONNodeNotificationFormatter EMPTY = new JSONNodeNotificationFormatter(TextParameters.EMPTY);

        static final NotificationFormatterFactory FACTORY = new NotificationFormatterFactory(EMPTY) {
            @Override
            public JSONNodeNotificationFormatter getFormatter(final TextParameters textParams, final String xpathFilter)
                throws XPathExpressionException {
                return new JSONNodeNotificationFormatter(textParams, xpathFilter);
            }

            @Override
            public JSONNodeNotificationFormatter newFormatter(final TextParameters textParams) {
                return new JSONNodeNotificationFormatter(textParams);
            }
        };

        private JSONNodeNotificationFormatter(final TextParameters textParams) {
            super(textParams);
        }

        private JSONNodeNotificationFormatter(final TextParameters textParams, final String xpathFilter)
            throws XPathExpressionException {
            super(textParams, xpathFilter);
        }

        @Override
        protected String createText(final TextParameters params, final EffectiveModelContext schemaContext,
            final DOMNotification input, final Instant now) throws IOException {
            try (var writer = new StringWriter()) {
                try (var jsonWriter = new JsonWriter(writer)) {
                    jsonWriter.beginObject()
                        .name(NOTIFICATION_NAME).beginObject()
                        .name("event-time").value(toRFC3339(now));
                    if (input instanceof NodeDOMNotification nodeNotification) {
                        jsonWriter.name("node-id").value(nodeNotification.nodeId);
                    }
                    writeBody(JSONNormalizedNodeStreamWriter.createNestedWriter(
                            JSONCodecFactorySupplier.RFC7951.getShared(schemaContext), input.getType(), null,
                            jsonWriter),
                        input.getBody());
                    jsonWriter.endObject().endObject();
                }
                return writer.toString();
            }
        }
    }

    private static final class XMLNodeNotificationFormatter extends NotificationFormatter {

        @VisibleForTesting
        static final XMLNodeNotificationFormatter EMPTY = new XMLNodeNotificationFormatter(TextParameters.EMPTY);
        static final NotificationFormatterFactory FACTORY = new NotificationFormatterFactory(EMPTY) {
            @Override
            public XMLNodeNotificationFormatter newFormatter(final TextParameters textParams) {
                return new XMLNodeNotificationFormatter(textParams);
            }

            @Override
            public XMLNodeNotificationFormatter getFormatter(final TextParameters textParams, final String xpathFilter)
                throws XPathExpressionException {
                return new XMLNodeNotificationFormatter(textParams, xpathFilter);
            }
        };

        XMLNodeNotificationFormatter(final TextParameters textParams) {
            super(textParams);
        }

        XMLNodeNotificationFormatter(final TextParameters textParams, final String xpathFilter)
            throws XPathExpressionException {
            super(textParams, xpathFilter);
        }

        private static @NonNull XMLStreamWriter createStreamWriterWithNodeNotification(final Writer writer,
            final Instant now,
            final String nodeId) throws XMLStreamException {
            final var xmlStreamWriter = createStreamWriterWithNotification(writer, now);
            if (nodeId != null) {
                xmlStreamWriter.writeStartElement("nodeId");
                xmlStreamWriter.writeCharacters(nodeId);
                xmlStreamWriter.writeEndElement();
            }
            return xmlStreamWriter;
        }

        @Override
        protected String createText(final TextParameters params, final EffectiveModelContext schemaContext,
            final DOMNotification input, final Instant now) throws IOException {
            final var writer = new StringWriter();

            try {
                final XMLStreamWriter xmlStreamWriter;
                if (input instanceof NodeDOMNotification nodeNotification) {
                    xmlStreamWriter = createStreamWriterWithNodeNotification(writer, now, nodeNotification.nodeId);
                } else {
                    xmlStreamWriter = createStreamWriterWithNotification(writer, now);
                }

                try (var nnWriter = NormalizedNodeWriter.forStreamWriter(XMLStreamNormalizedNodeStreamWriter.create(
                    xmlStreamWriter, schemaContext, input.getType()))) {
                    nnWriter.write(input.getBody());
                    nnWriter.flush();

                    xmlStreamWriter.writeEndElement();
                    xmlStreamWriter.writeEndDocument();
                    xmlStreamWriter.flush();
                }
            } catch (XMLStreamException e) {
                throw new IOException("Failed to write notification content", e);
            }

            return writer.toString();
        }
    }

    private record NodeDOMNotification(DOMNotification delegate, String nodeId) implements DOMNotification {

        @Override
        public SchemaNodeIdentifier.@NonNull Absolute getType() {
            return delegate.getType();
        }

        @Override
        public @NonNull ContainerNode getBody() {
            return delegate.getBody();
        }
    }

    private record NodeAwareListener(RestconfStream.Sink<DOMNotification> sink,
                                     Supplier<EffectiveModelContext> modelContext,
                                     String nodeId) implements DOMNotificationListener {

        private NodeAwareListener(final RestconfStream.Sink<DOMNotification> sink,
            final Supplier<EffectiveModelContext> modelContext, final String nodeId) {
            this.sink = requireNonNull(sink);
            this.modelContext = requireNonNull(modelContext);
            this.nodeId = nodeId;
        }

        @Override
        public void onNotification(final DOMNotification notification) {
            sink.publish(modelContext.get(), new NodeDOMNotification(notification, nodeId),
                notification instanceof DOMEvent domEvent ? domEvent.getEventInstant() : Instant.now());
        }
    }
}
