/*
 * Copyright (c) 2020 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8639.layer.services.subscriptions;

import static java.util.Objects.requireNonNull;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.ws.rs.core.MediaType;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jetty.http2.api.Stream;
import org.eclipse.jetty.http2.frames.DataFrame;
import org.eclipse.jetty.util.Callback;
import org.glassfish.jersey.media.sse.EventOutput;
import org.glassfish.jersey.media.sse.OutboundEvent;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.mdsal.dom.api.DOMNotificationListener;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.restconf.nb.rfc8040.handlers.TransactionChainHandler;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.IdentifierCodec;
import org.opendaylight.restconf.nb.rfc8639.util.services.SubscribedNotificationsUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.ReplayCompleted;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionCompleted;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionTerminated;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.util.xml.UntrustedXML;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeWriter;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONCodecFactory;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONCodecFactorySupplier;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.gson.JsonWriterFactory;
import org.opendaylight.yangtools.yang.data.codec.xml.XMLStreamNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class NotificationStreamListener implements DOMNotificationListener {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationStreamListener.class);
    private static final XMLOutputFactory OF = XMLOutputFactory.newInstance();
    private static final TransformerFactory TF = TransformerFactory.newInstance();

    private final List<EventOutput> eventOutputs = new ArrayList<>();
    private final Set<Stream> http2Streams = new HashSet<>();

    private final SchemaContext schemaContext;
    private final String establishedSessionId;
    private final String streamName;
    private final TransactionChainHandler transactionChainHandler;
    private final Encoding encoding;

    private ListenerRegistration<NotificationStreamListener> registration;
    private ScheduledExecutorService scheduledExecutor;
    private DOMNotification lastNotification;
    private Instant replayStartTime;
    private Instant stopTime;
    private Instant anchorTime;
    private ReplayBuffer replayBufferForStream;
    private long period = -1;
    private Uint32 subscriptionId;

    public NotificationStreamListener(final String streamName, final EffectiveModelContext schemaContext,
            final String establishedSessionId, final TransactionChainHandler transactionChainHandler,
            final Encoding encoding) {
        this.schemaContext = requireNonNull(schemaContext);
        this.establishedSessionId = requireNonNull(establishedSessionId);
        this.streamName = streamName;
        this.transactionChainHandler = requireNonNull(transactionChainHandler);
        this.encoding = requireNonNull(encoding);
    }

    @Override
    public void onNotification(final DOMNotification notification) {
        LOG.debug("Notification was generated in onNotificationMethod: {}", notification.getBody());
        if (period == -1) {
            pushNotification(notification);
        } else {
            lastNotification = notification;
            if (scheduledExecutor == null) {
                executePeriodicSentEvent(
                        anchorTime != null ? anchorTime.getEpochSecond() - Instant.now().getEpochSecond() : 0);
            }
        }
    }

    private void executePeriodicSentEvent(final long initialDelay) {
        scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
        scheduledExecutor.scheduleAtFixedRate(() ->
                pushNotification(lastNotification),
                resolveInitialDelay(initialDelay),
                period,
                TimeUnit.SECONDS);
    }

    private long resolveInitialDelay(final long initialDelay) {
        if (initialDelay < 0) {
            LOG.warn("Initial delay must not be negative number of seconds, but was {}. It has been reset to zero.",
                    initialDelay);
            return 0;
        }
        return initialDelay;
    }

    private void pushNotification(final DOMNotification notification) {
        if (!eventOutputs.isEmpty()) {
            final Set<EventOutput> closedConnections = new HashSet<>();
            for (final EventOutput eventOutput : eventOutputs) {
                writeDOMNotificationIntoEventOutput(notification, Instant.now(), eventOutput, closedConnections);
            }
            eventOutputs.removeAll(closedConnections);
        }

        if (!http2Streams.isEmpty()) {
            final Set<Stream> closedConnections = new HashSet<>();
            for (final Stream http2Stream : http2Streams) {
                writeDOMNotificationIntoHttp2Stream(notification, Instant.now(), http2Stream, closedConnections);
            }

            http2Streams.removeAll(closedConnections);
        }
    }

    public void replayNotifications() {
        if ((replayStartTime != null) && (replayBufferForStream != null)) {
            if (replayStartTime.isAfter(replayBufferForStream.getNewestNotificationTimeStamp())) {
                LOG.warn("Parameter replay-start-time is later than the oldest record stored within the publisher's "
                                + "replay buffer for the stream {}. The latest record in the buffer is {}.",
                        streamName, replayBufferForStream.getNewestNotificationTimeStamp());
                sendReplayCompletedNotification();
            } else {
                pushNotificationsFromReplayBuffer();
            }
        }
    }

    private void pushNotificationsFromReplayBuffer() {
        final SortedMap<Instant, DOMNotification> notifsToReplay;
        if (stopTime != null) {
            // if stopTime is not null it means that is set to a later time than replay-start-time parameter
            notifsToReplay = replayBufferForStream.getAllRecordedNotificationsFromTo(replayStartTime, stopTime);
        } else {
            notifsToReplay = replayBufferForStream.getAllRecordedNotificationsFrom(replayStartTime);
        }

        if (!eventOutputs.isEmpty()) {
            final Set<EventOutput> closedConnections = new HashSet<>();
            for (final EventOutput eventOutput : eventOutputs) {
                for (final Entry<Instant, DOMNotification> timeStampWithNotification : notifsToReplay.entrySet()) {
                    LOG.debug("Going to write a notification from replay buffer.");
                    writeDOMNotificationIntoEventOutput(timeStampWithNotification.getValue(),
                            timeStampWithNotification.getKey(), eventOutput, closedConnections);
                    LOG.debug("Notification from replay buffer has been written.");
                }
            }

            eventOutputs.removeAll(closedConnections);
            sendReplayCompletedNotification();
        }

        if (!http2Streams.isEmpty()) {
            final Set<Stream> closedConnections = new HashSet<>();
            for (final Stream http2Stream : http2Streams) {
                for (final Entry<Instant, DOMNotification> timeStampWithNotification : notifsToReplay.entrySet()) {
                    LOG.debug("Going to write a notification from replay buffer.");
                    writeDOMNotificationIntoHttp2Stream(timeStampWithNotification.getValue(),
                            timeStampWithNotification.getKey(), http2Stream, closedConnections);
                    LOG.debug("Notification from replay buffer has been written.");
                }
            }

            http2Streams.removeAll(closedConnections);
            sendReplayCompletedNotification();
        }
    }

    private void sendReplayCompletedNotification() {
        final LeafNode<Object> identifier = Builders.leafBuilder().withNodeIdentifier(
                SubscribedNotificationsModuleUtils.IDENTIFIER_LEAF_ID).withValue(subscriptionId).build();
        final ContainerNode replayCompleted = Builders.containerBuilder().withNodeIdentifier(
                SubscribedNotificationsModuleUtils.REPLAY_COMPLETED_NOTIFICATION_ID).withChild(identifier).build();
        final DOMNotification replayCompletedNotification = new DOMNotification() {

            @Override
            public SchemaPath getType() {
                return SchemaPath.create(true, ReplayCompleted.QNAME);
            }

            @Override
            public @NonNull ContainerNode getBody() {
                return replayCompleted;
            }
        };

        pushNotification(replayCompletedNotification);
    }

    private void sendSubscriptionCompletedNotification() {
        final LeafNode<Object> identifier = Builders.leafBuilder().withNodeIdentifier(
                SubscribedNotificationsModuleUtils.IDENTIFIER_LEAF_ID).withValue(subscriptionId).build();
        final ContainerNode subscriptionCompleted = Builders.containerBuilder()
                .withNodeIdentifier(SubscribedNotificationsModuleUtils.SUBSCRIPTION_COMPLETED_NOTIFICATION_ID)
                .withChild(identifier)
                .build();
        final DOMNotification subscriptionCompletedNotification = new DOMNotification() {

            @Override
            public SchemaPath getType() {
                return SchemaPath.create(true, SubscriptionCompleted.QNAME);
            }

            @Override
            public @NonNull ContainerNode getBody() {
                return subscriptionCompleted;
            }
        };

        pushNotification(subscriptionCompletedNotification);
    }

    private void sendSubscriptionTerminatedNotification() {
        final LeafNode<Object> identifier = Builders.leafBuilder().withNodeIdentifier(
                SubscribedNotificationsModuleUtils.IDENTIFIER_LEAF_ID).withValue(subscriptionId).build();
        final ContainerNode subscriptionTerminated = Builders.containerBuilder().withNodeIdentifier(
                SubscribedNotificationsModuleUtils.SUBSCRIPTION_TERMINATED_NOTIFICATION_ID).withChild(identifier)
                .build();
        final DOMNotification subscriptionTerminatedNotification = new DOMNotification() {

            @Override
            public SchemaPath getType() {
                return SchemaPath.create(true, SubscriptionTerminated.QNAME);
            }

            @Override
            public @NonNull ContainerNode getBody() {
                return subscriptionTerminated;
            }
        };

        pushNotification(subscriptionTerminatedNotification);
    }

    private void writeDOMNotificationIntoEventOutput(final DOMNotification notification, final Instant timeStamp,
            final EventOutput eventOutput, final Set<EventOutput> closedConnections) {
        try {
            final String notificationStr;
            if (Encoding.ENCODE_XML.equals(encoding)) {
                notificationStr = prepareXml(notification, timeStamp);
            } else {
                notificationStr = prepareJson(notification, timeStamp);
            }

            LOG.debug("Going to write notification into SSE event stream.");
            eventOutput.write(new OutboundEvent.Builder().data(notificationStr)
                    .mediaType(MediaType.valueOf("text/event-stream")).build());
            LOG.debug("Successfully wrote notification into SSE event stream.");
        } catch (final IOException e) {
            closedConnections.add(eventOutput);
            LOG.debug("Added failed SSE connection.", e);
        }
    }

    private void writeDOMNotificationIntoHttp2Stream(final DOMNotification notification, final Instant timeStamp,
            final Stream http2Stream, final Set<Stream> closedConnections) {
        final String notificationStr;
        if (Encoding.ENCODE_XML.equals(encoding)) {
            notificationStr = prepareXml(notification, timeStamp);
        } else {
            notificationStr = prepareJson(notification, timeStamp);
        }

        if (http2Stream.isClosed()) {
            LOG.debug("http2stream was closed");
            closedConnections.add(http2Stream);
        } else {
            final DataFrame notificationFrame = new DataFrame(http2Stream.getId(),
                    ByteBuffer.wrap(notificationStr.getBytes(StandardCharsets.UTF_8)), false);
            LOG.debug("Going to write notification frame via http2 stream");
            http2Stream.data(notificationFrame, Callback.NOOP);
            LOG.debug("Successfully wrote notification frame via http2 stream");
        }
    }

    public boolean deleteSubscription(final String actualSessionId, final boolean isKill) {
        // kill-subscription rpc
        if (isKill) {
            sendSubscriptionTerminatedNotification();
            killSubscription();
            return true;
        }

        // delete-subscription rpc
        if (establishedSessionId.equals(actualSessionId)) {
            killSubscription();
            return true;
        }

        return false;
    }

    public void killSubscription() {
        if (registration != null) {
            registration.close();
        }

        if (!eventOutputs.isEmpty()) {
            eventOutputs.clear();
        }

        if (!http2Streams.isEmpty()) {
            http2Streams.clear();
        }

        if (scheduledExecutor != null) {
            scheduledExecutor.shutdown();
            scheduledExecutor = null;
        }

        lastNotification = null;

        deleteStreamSubscriptionFromDatastore();
    }

    public boolean hasEqualSessionId(final String currentSessionId) {
        return establishedSessionId.equals(currentSessionId);
    }

    public void addEventOutput(final EventOutput eventOutput) {
        this.eventOutputs.add(eventOutput);
    }

    public void addHttp2Stream(final Stream stream) {
        http2Streams.add(stream);
    }

    private String prepareJson(final DOMNotification notification, final Instant timeStamp) {
        final JsonParser jsonParser = new JsonParser();
        final JsonObject json = new JsonObject();
        json.add("ietf-restconf:notification", jsonParser.parse(writeBodyToString(notification)));
        json.addProperty("event-time", SubscribedNotificationsUtil.timeStampToRFC3339Format(timeStamp));
        return json.toString();
    }

    private String writeBodyToString(final DOMNotification notification) {
        final Writer writer = new StringWriter();
        final JSONCodecFactory jsonCodecFactory =
                JSONCodecFactorySupplier.DRAFT_LHOTKA_NETMOD_YANG_JSON_02.createLazy(schemaContext);
        final NormalizedNodeStreamWriter jsonStream = JSONNormalizedNodeStreamWriter.createExclusiveWriter(
                jsonCodecFactory, notification.getType(), null,
                JsonWriterFactory.createJsonWriter(writer));
        final NormalizedNodeWriter nodeWriter = NormalizedNodeWriter.forStreamWriter(jsonStream);
        try {
            nodeWriter.write(notification.getBody());
            nodeWriter.close();
        } catch (final IOException e) {
            throw new RestconfDocumentedException("Serialization of notification message to JSON failed.", e);
        }
        return writer.toString();
    }

    private String prepareXml(final DOMNotification notification, final Instant timeStamp) {
        final Document doc = UntrustedXML.newDocumentBuilder().newDocument();
        final Element notificationElement = basePartDoc(doc, timeStamp);
        addValuesToNotificationEventElement(doc, notificationElement, notification);

        return transformDoc(doc);
    }

    private String transformDoc(final Document doc) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            final Transformer transformer = TF.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
            transformer.transform(new DOMSource(doc), new StreamResult(out));
        } catch (final TransformerException e) {
            final String msg = "Error during transformation of Document into String";
            LOG.error(msg, e);
            return msg;
        }

        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private void addValuesToNotificationEventElement(final Document doc,
            final Element element, final DOMNotification notification) {
        if (notification == null) {
            return;
        }

        final NormalizedNode<NodeIdentifier, Collection<DataContainerChild<? extends PathArgument, ?>>> body =
                notification.getBody();
        try {
            final DOMResult domResult = writeNormalizedNode(body, schemaContext, notification.getType());
            final Node result = doc.importNode(domResult.getNode().getFirstChild(), true);
            element.appendChild(result);
        } catch (final IOException | XMLStreamException e) {
            throw new RestconfDocumentedException("Serialization of notification message to XML failed.",
                    RestconfError.ErrorType.APPLICATION, RestconfError.ErrorTag.OPERATION_FAILED, e);
        }
    }

    private DOMResult writeNormalizedNode(final NormalizedNode<?, ?> normalized, final SchemaContext context,
            final SchemaPath schemaPath) throws IOException, XMLStreamException {
        final Document doc = UntrustedXML.newDocumentBuilder().newDocument();
        final DOMResult result = new DOMResult(doc);
        NormalizedNodeWriter normalizedNodeWriter = null;
        NormalizedNodeStreamWriter normalizedNodeStreamWriter = null;
        XMLStreamWriter writer = null;

        try {
            writer = OF.createXMLStreamWriter(result);
            normalizedNodeStreamWriter = XMLStreamNormalizedNodeStreamWriter.create(writer, context, schemaPath);
            normalizedNodeWriter = NormalizedNodeWriter.forStreamWriter(normalizedNodeStreamWriter);

            normalizedNodeWriter.write(normalized);

            normalizedNodeWriter.flush();
        } finally {
            if (normalizedNodeWriter != null) {
                normalizedNodeWriter.close();
            }
            if (normalizedNodeStreamWriter != null) {
                normalizedNodeStreamWriter.close();
            }
            if (writer != null) {
                writer.close();
            }
        }

        return result;
    }

    private Element basePartDoc(final Document doc, final Instant timeStamp) {
        final Element notificationElement = doc.createElementNS("urn:ietf:params:xml:ns:netconf:notification:1.0",
                "notification");

        doc.appendChild(notificationElement);

        final Element eventTimeElement = doc.createElement("eventTime");
        eventTimeElement.setTextContent(SubscribedNotificationsUtil.timeStampToRFC3339Format(timeStamp));
        notificationElement.appendChild(eventTimeElement);

        return notificationElement;
    }

    public void setRegistration(final ListenerRegistration<NotificationStreamListener> registration) {
        this.registration = registration;
    }

    public void setReplayStartTime(final Instant replayStartTime) {
        this.replayStartTime = replayStartTime;
    }

    public void setReplayBufferForStream(final ReplayBuffer replayBufferForStream) {
        this.replayBufferForStream = replayBufferForStream;
    }

    public void setStopTime(final Instant time) {
        final long delay = time.getEpochSecond() - Instant.now().getEpochSecond();
        if (replayStartTime == null) {
            if (delay <= 0) {
                LOG.error("Parameter stop-time must be set for a future time because replay-start-time parameter is "
                        + "not available. Ignoring stop-time.");
            } else {
                LOG.info("Delay to stop subscription {} is {} seconds.", subscriptionId, delay);
                Executors.newSingleThreadScheduledExecutor().schedule(() -> {
                    sendSubscriptionCompletedNotification();
                    killSubscription();
                }, delay, TimeUnit.SECONDS);
            }
        } else {
            if (time.isAfter(replayStartTime)) {
                stopTime = time;
            } else {
                LOG.error("Parameter stop-time ({}) must be set to a later time than parameter replay-start-time ({})"
                        + ". Ignoring stop-time.", time, replayStartTime);
            }
        }
    }

    public void setPeriod(final Uint32 period) {
        this.period = period.longValue();
    }

    public void setAnchorTime(final String anchorTime) {
        this.anchorTime = Instant.parse(anchorTime);
    }

    public void scheduleNotifications() {
        if ((period == 0) && (scheduledExecutor != null)) {
            scheduledExecutor.shutdown();
            scheduledExecutor = null;
            lastNotification = null;
        } else if ((period > 0) && (lastNotification != null)) {
            scheduledExecutor.shutdown();
            executePeriodicSentEvent(anchorTime != null
                    ? anchorTime.getEpochSecond() - Instant.now().getEpochSecond() : 0);
        }
    }

    public void setSubscriptionId(final Uint32 id) {
        this.subscriptionId = id;
    }

    public String getStreamName() {
        return streamName;
    }

    private void deleteStreamSubscriptionFromDatastore() {
        final String pathToSubscriptionListEntry = SubscribedNotificationsModuleUtils
                .PATH_TO_SUBSCRIPTION_WITHOUT_KEY + subscriptionId;
        final DOMTransactionChain domTransactionChain = transactionChainHandler.get();
        final DOMDataTreeReadWriteTransaction transaction = domTransactionChain.newReadWriteTransaction();
        transaction.delete(LogicalDatastoreType.OPERATIONAL,
                IdentifierCodec.deserialize(pathToSubscriptionListEntry, schemaContext));
        SubscribedNotificationsUtil.submitData(transaction, domTransactionChain);
    }
}
