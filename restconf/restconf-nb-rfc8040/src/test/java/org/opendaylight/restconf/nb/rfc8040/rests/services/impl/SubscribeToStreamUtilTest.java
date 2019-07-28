/*
 * Copyright © 2020 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableClassToInstanceMap;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import javax.ws.rs.core.MultivaluedHashMap;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeChangeListener;
import org.opendaylight.mdsal.dom.api.DOMDataTreeChangeService;
import org.opendaylight.mdsal.dom.api.DOMDataTreeIdentifier;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMNotificationListener;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.util.SimpleUriInfo;
import org.opendaylight.restconf.nb.rfc8040.Rfc8040;
import org.opendaylight.restconf.nb.rfc8040.TestUtils;
import org.opendaylight.restconf.nb.rfc8040.handlers.DOMDataBrokerHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.NotificationServiceHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.SchemaContextHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.TransactionChainHandler;
import org.opendaylight.restconf.nb.rfc8040.rests.services.impl.RestconfStreamsSubscriptionServiceImpl.HandlersHolder;
import org.opendaylight.restconf.nb.rfc8040.rests.services.impl.RestconfStreamsSubscriptionServiceImpl.NotificationQueryParams;
import org.opendaylight.restconf.nb.rfc8040.streams.listeners.ListenerAdapter;
import org.opendaylight.restconf.nb.rfc8040.streams.listeners.ListenersBroker;
import org.opendaylight.restconf.nb.rfc8040.streams.listeners.NotificationListenerAdapter;
import org.opendaylight.restconf.nb.rfc8040.utils.RestconfConstants;
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev140708.NotificationOutputTypeGrouping.NotificationOutputType;
import org.opendaylight.yangtools.concepts.NoOpListenerRegistration;
import org.opendaylight.yangtools.util.concurrent.FluentFutures;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

@RunWith(MockitoJUnitRunner.class)
public class SubscribeToStreamUtilTest {

    private static final QNameModule EXAMPLE_NOTIFICATIONS_MODULE = QNameModule.create(
            URI.create("urn:ietf:paras:xml:ns:yang:example-notifications"), Revision.of("2019-07-27")).intern();
    private static final QName ROOT_NOTIFY_QNAME = QName.create(EXAMPLE_NOTIFICATIONS_MODULE, "root-notify").intern();
    private static final QName EXAMPLE_CONTAINER_QNAME = QName.create(
            EXAMPLE_NOTIFICATIONS_MODULE, "example-container").intern();
    private static final ListenersBroker LISTENERS_BROKER = new ListenersBroker();

    private static EffectiveModelContext schemaContext;
    private HandlersHolder handlersHolder;

    @Mock
    private DOMDataBroker domDataBroker;
    @Mock
    private DOMNotificationService domNotificationService;
    @Mock
    private DOMDataTreeChangeService domDataTreeChangeService;

    @BeforeClass
    public static void setupSchemaContext() {
        schemaContext = YangParserTestUtils.parseYangResources(SubscribeToStreamUtilTest.class,
                "/modules/example-notifications.yang",
                "/restconf/impl/ietf-restconf-monitoring@2017-01-26.yang",
                "/restconf/impl/ietf-inet-types.yang",
                "/restconf/impl/ietf-yang-types.yang");
    }

    @Before
    public void setupHandlersHolder() {
        final TransactionChainHandler transactionChainHandler = new TransactionChainHandler(domDataBroker);
        final SchemaContextHandler schemaContextHandler = TestUtils.newSchemaContextHandler(schemaContext);
        final NotificationServiceHandler notificationHandler = new NotificationServiceHandler(domNotificationService);
        final DOMDataBrokerHandler domDataBrokerHandler = new DOMDataBrokerHandler(domDataBroker);
        handlersHolder = new HandlersHolder(domDataBrokerHandler, notificationHandler,
                transactionChainHandler, schemaContextHandler);
        when(domDataBroker.getExtensions()).thenReturn(ImmutableClassToInstanceMap.of(
                DOMDataTreeChangeService.class, domDataTreeChangeService));
    }

    @Test
    public void subscribeToYangNotifiStreamTest() {
        // mocking of transactions
        final DOMTransactionChain domTransactionChain = mock(DOMTransactionChain.class);
        when(domDataBroker.createTransactionChain(any())).thenReturn(domTransactionChain);
        final DOMDataTreeWriteTransaction woTransaction = mock(DOMDataTreeWriteTransaction.class);
        when(domTransactionChain.newWriteOnlyTransaction()).thenReturn(woTransaction);
        when(woTransaction.commit()).thenReturn(FluentFutures.immediateNullFluentFuture());
        when(domNotificationService.registerNotificationListener(any(DOMNotificationListener.class),
                any(SchemaPath.class))).thenReturn(
                NoOpListenerRegistration.of(mock(DOMNotificationListener.class)));

        // preparation of input parameters
        final String streamName = "notification-stream/example-notifications:root-notify";
        final String url = "http://127.0.0.1:8181/" + RestconfConstants.BASE_URI_PATTERN + '/' + streamName;
        final String startTime = "2020-04-01T01:01:01.0Z";
        final SimpleUriInfo uriInfo = new SimpleUriInfo(url, new MultivaluedHashMap<>(Collections.singletonMap(
                "start-time", startTime)));

        // preparation of listeners broker state
        final SchemaPath notificationSchemaPath = SchemaPath.create(true, ROOT_NOTIFY_QNAME);
        final NotificationListenerAdapter listenerAdapter = LISTENERS_BROKER.registerNotificationListener(
                notificationSchemaPath, streamName, NotificationOutputType.XML);

        // subscription test
        final URI actualUri = SubscribeToStreamUtil.subscribeToYangStream(streamName,
                NotificationQueryParams.fromUriInfo(uriInfo), handlersHolder, StreamUrlResolver.webSockets(), uriInfo,
                LISTENERS_BROKER);
        final URI expectedUri = URI.create("ws://127.0.0.1:8181/" + RestconfConstants.BASE_URI_PATTERN
                + "/notification-stream/example-notifications:root-notify");
        assertNotNull(listenerAdapter.getStart());
        assertEquals(expectedUri, actualUri);
        assertTrue(listenerAdapter.isListening());

        verify(domNotificationService).registerNotificationListener(
                any(), eq(notificationSchemaPath));
        checkWrittenStartTime(woTransaction);
    }

    @Test
    public void subscribeToNonExistingYangNotifiStreamTest() {
        final String streamName = "notification-stream/example-notifications:roots-notify";
        final String url = "http://127.0.0.1:8181/" + RestconfConstants.BASE_URI_PATTERN + '/' + streamName;
        final String startTime = "2020-04-01T01:01:01.0Z";
        final SimpleUriInfo uriInfo = new SimpleUriInfo(url, new MultivaluedHashMap<>(Collections.singletonMap(
                "start-time", startTime)));
        assertThrows(RestconfDocumentedException.class, () -> SubscribeToStreamUtil.subscribeToYangStream(streamName,
                NotificationQueryParams.fromUriInfo(uriInfo), handlersHolder, StreamUrlResolver.webSockets(), uriInfo,
                LISTENERS_BROKER));
    }

    @Test
    public void subscribeToDataStreamTest() {
        // mocking of transactions
        final DOMTransactionChain domTransactionChain = mock(DOMTransactionChain.class);
        when(domDataBroker.createTransactionChain(any())).thenReturn(domTransactionChain);
        final DOMDataTreeWriteTransaction woTransaction = mock(DOMDataTreeWriteTransaction.class);
        when(domTransactionChain.newWriteOnlyTransaction()).thenReturn(woTransaction);
        when(woTransaction.commit()).thenReturn(FluentFutures.immediateNullFluentFuture());
        when(domDataTreeChangeService.registerDataTreeChangeListener(any(DOMDataTreeIdentifier.class),
                any(DOMDataTreeChangeListener.class))).thenReturn(
                NoOpListenerRegistration.of(mock(DOMDataTreeChangeListener.class)));

        // preparation of input parameters
        final String streamName = "data-change-event-subscriptions/example-notifications:example-container"
                + "/datastore=CONFIGURATION/scope=BASE/JSON";
        final String url = "https://127.0.0.1:8181/" + RestconfConstants.BASE_URI_PATTERN + '/' + streamName;
        final SimpleUriInfo uriInfo = new SimpleUriInfo(url);

        // preparation of listeners broker state
        final YangInstanceIdentifier containerYiid = YangInstanceIdentifier.builder()
                .node(EXAMPLE_CONTAINER_QNAME)
                .build();
        final ListenerAdapter listenerAdapter = LISTENERS_BROKER.registerDataChangeListener(
                containerYiid, streamName, NotificationOutputType.JSON);

        // subscription test
        final URI actualUri = SubscribeToStreamUtil.subscribeToDataStream(streamName,
                NotificationQueryParams.fromUriInfo(uriInfo), handlersHolder, StreamUrlResolver.webSockets(), uriInfo,
                LISTENERS_BROKER);
        final URI expectedUri = URI.create("wss://127.0.0.1:8181/" +  RestconfConstants.BASE_URI_PATTERN
                + "/data-change-event-subscriptions/example-notifications:example-container/"
                + "datastore=CONFIGURATION/scope=BASE/JSON");
        assertNotNull(listenerAdapter.getStart());
        assertEquals(expectedUri, actualUri);
        assertTrue(listenerAdapter.isListening());

        verify(domDataTreeChangeService).registerDataTreeChangeListener(eq(
                new DOMDataTreeIdentifier(LogicalDatastoreType.CONFIGURATION, containerYiid)), any());
        checkWrittenStartTime(woTransaction);
    }

    @Test
    public void subscribeToNonExistingDataStreamTest() {
        final String streamName = "data-change-event-subscription/stream/datastore=CONFIGURATION/scope=BASE/JSON";
        final String url = "https://127.0.0.1:8181/" + RestconfConstants.BASE_URI_PATTERN + '/' + streamName;
        final SimpleUriInfo uriInfo = new SimpleUriInfo(url);
        assertThrows(RestconfDocumentedException.class, () -> SubscribeToStreamUtil.subscribeToDataStream(streamName,
                NotificationQueryParams.fromUriInfo(uriInfo), handlersHolder, StreamUrlResolver.webSockets(), uriInfo,
                LISTENERS_BROKER));
    }

    @Test
    public void subscribeToDataStreamWithMissingDatastoreTest() {
        final String streamName = "data-change-event-subscription/example-notifications:example-container/scope=BASE";
        final String url = "http://127.0.0.1:8181/" + RestconfConstants.BASE_URI_PATTERN + '/' + streamName;
        final SimpleUriInfo uriInfo = new SimpleUriInfo(url);
        assertThrows(RestconfDocumentedException.class, () -> SubscribeToStreamUtil.subscribeToDataStream(streamName,
                NotificationQueryParams.fromUriInfo(uriInfo), handlersHolder, StreamUrlResolver.webSockets(), uriInfo,
                LISTENERS_BROKER));
    }

    private static void checkWrittenStartTime(final DOMDataTreeWriteTransaction woTransaction) {
        final ArgumentCaptor<?> nnCaptor = ArgumentCaptor.forClass(NormalizedNode.class);
        verify(woTransaction).merge(eq(LogicalDatastoreType.OPERATIONAL),
                any(), (NormalizedNode<?, ?>) nnCaptor.capture());
        //noinspection ResultOfMethodCallIgnored
        verify(woTransaction).commit();
        assertTrue(nnCaptor.getValue() instanceof MapEntryNode);
        final Collection<DataContainerChild<? extends PathArgument, ?>> childrenNodes
                = ((MapEntryNode) nnCaptor.getValue()).getValue();
        assertTrue(childrenNodes.stream().anyMatch(dataContainerChild -> dataContainerChild.getIdentifier()
                .getNodeType().equals(Rfc8040.MonitoringModule.LEAF_START_TIME_STREAM_QNAME)));
    }
}