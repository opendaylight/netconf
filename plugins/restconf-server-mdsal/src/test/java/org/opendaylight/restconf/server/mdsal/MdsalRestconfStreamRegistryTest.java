/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.mdsal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.mdsal.singleton.api.ClusterSingletonServiceProvider;
import org.opendaylight.netconf.databind.DatabindProvider;
import org.opendaylight.netconf.databind.RequestException;
import org.opendaylight.restconf.server.api.TransportSession;
import org.opendaylight.restconf.server.api.testlib.CompletingServerRequest;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EncodeJson$I;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EncodeXml$I;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.Streams;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionTerminatedReason;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.Subscriptions;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.streams.Stream;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.Subscription;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

@ExtendWith(MockitoExtension.class)
class MdsalRestconfStreamRegistryTest {
    private static final Uint32 ID = Uint32.valueOf(2147483648L);
    private static final Instant STOP_TIME = Instant.parse("2024-10-30T12:34:56Z");
    private static final String STREAM_NAME = "NETCONF";
    private static final String URI = "http://example.com";
    private static final NodeIdentifier URI_NODEID = NodeIdentifier.create(
        org.opendaylight.yang.svc.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.subscribed.notifications.rev191117
            .YangModuleInfoImpl.qnameOf("uri"));

    @Mock
    private DOMDataBroker dataBroker;
    @Mock
    private DOMNotificationService notifService;
    @Mock
    private DOMSchemaService schemaService;
    @Mock
    private DatabindProvider databindProvider;
    @Mock
    private RestconfStream.LocationProvider locProvider;
    @Mock
    private DOMDataBroker.DataTreeChangeExtension dtxExt;
    @Mock
    private DOMTransactionChain txChain;
    @Mock
    private DOMDataTreeWriteTransaction writeTx;
    @Mock
    private Registration regMock;
    @Mock
    private CompletingServerRequest<Uint32> request;
    @Mock
    private TransportSession session;
    @Mock
    private TransportSession.Description sessionDesc;
    @Mock
    private EffectiveModelContext effectiveModelContext;
    @Mock
    private EffectiveModelContext updatedModelContext;
    @Mock
    private ClusterSingletonServiceProvider cssProvider;

    private MdsalRestconfStreamRegistry registry;

    @BeforeEach
    void setUp() {
        when(dataBroker.createMergingTransactionChain()).thenReturn(txChain);
        doReturn(CommitInfo.emptyFluentFuture()).when(writeTx).commit();
        when(dataBroker.newWriteOnlyTransaction()).thenReturn(writeTx);
        when(schemaService.registerSchemaContextListener(any())).thenReturn(regMock);
        when(dataBroker.extension(DOMDataBroker.DataTreeChangeExtension.class)).thenReturn(dtxExt);
        when(dtxExt.registerTreeChangeListener(any(), any())).thenReturn(regMock);
        when(schemaService.getGlobalContext()).thenReturn(effectiveModelContext);
        registry = new MdsalRestconfStreamRegistry(dataBroker, notifService, schemaService, locProvider,
            databindProvider, cssProvider);
    }

    @AfterEach
    void tearDown() {
        registry.close();
    }

    @Test
    void establishSubscriptionTest() {
        when(request.session()).thenReturn(session);
        when(session.description()).thenReturn(sessionDesc);
        when(sessionDesc.toFriendlyString()).thenReturn("session");
        when(txChain.newWriteOnlyTransaction()).thenReturn(writeTx);
        registry.establishSubscription(request, "NETCONF", EncodeJson$I.QNAME, null, null);

        final var idCap = ArgumentCaptor.forClass(Uint32.class);
        verify(request).completeWith(idCap.capture());
        final var newId = idCap.getValue();
        assertNotNull(newId);
        assertNotNull(registry.lookupSubscription(newId));

        final var subscriptionPath = YangInstanceIdentifier.of(
            YangInstanceIdentifier.NodeIdentifier.create(Subscriptions.QNAME),
            YangInstanceIdentifier.NodeIdentifier.create(Subscription.QNAME),
            YangInstanceIdentifier.NodeIdentifierWithPredicates.of(Subscription.QNAME,
                QName.create(Subscription.QNAME, "id"), newId));

        final var streamPath = YangInstanceIdentifier.of(YangInstanceIdentifier.NodeIdentifier.create(Streams.QNAME),
            YangInstanceIdentifier.NodeIdentifier.create(Stream.QNAME),
            YangInstanceIdentifier.NodeIdentifierWithPredicates.of(Stream.QNAME,
                QName.create(Stream.QNAME, "name"), "NETCONF"));

        final var order = inOrder(writeTx);
        order.verify(writeTx).put(eq(LogicalDatastoreType.OPERATIONAL), eq(streamPath), any());
        order.verify(writeTx).put(eq(LogicalDatastoreType.OPERATIONAL), eq(subscriptionPath), any());
        verify(writeTx, times(2)).commit();
    }

    @Test
    void establishSubscriptionUnknownStreamTest() {
        when(request.session()).thenReturn(session);
        registry.establishSubscription(request, "TEST", EncodeJson$I.QNAME, null, null);
        final var errCap = ArgumentCaptor.forClass(RequestException.class);
        verify(request).completeWith(errCap.capture());
        assertEquals("TEST refers to an unknown stream", errCap.getValue().getMessage());
    }

    @ParameterizedTest
    @EnumSource(MdsalRestconfStreamRegistry.State.class)
    void testSubscriptionStateEventsXml(final MdsalRestconfStreamRegistry.State type) {
        final var notification = switch (type) {
            case MODIFIED -> MdsalRestconfStreamRegistry.subscriptionModified(ID, STREAM_NAME, EncodeXml$I.QNAME, null,
                STOP_TIME, URI);
            case RESUMED -> MdsalRestconfStreamRegistry.subscriptionResumed(ID);
            case TERMINATED -> MdsalRestconfStreamRegistry.subscriptionTerminated(ID,
                SubscriptionTerminatedReason.QNAME);
            case SUSPENDED -> MdsalRestconfStreamRegistry.subscriptionSuspended(ID,
                SubscriptionTerminatedReason.QNAME);
        };

        final var eventQName = type.nodeId.getNodeType();
        assertNotNull(notification);
        assertEquals(ID, notification.getChildByArg(
            new NodeIdentifier(QName.create(eventQName, "id"))).body());

        switch (type) {
            case null -> throw new NullPointerException();
            case MODIFIED -> {
                assertEquals(EncodeXml$I.QNAME, notification.getChildByArg(
                    new NodeIdentifier(QName.create(eventQName, "encoding"))).body());
                assertEquals(STREAM_NAME, notification.getChildByArg(
                    new NodeIdentifier(QName.create(eventQName, "stream"))).body());
                assertEquals(STOP_TIME, Instant.parse(notification.getChildByArg(
                    new NodeIdentifier(QName.create(eventQName, "stop-time"))).body().toString()));
                assertEquals(URI, notification.getChildByArg(URI_NODEID).body());
            }
            case SUSPENDED, TERMINATED ->
                assertEquals(SubscriptionTerminatedReason.QNAME, notification.getChildByArg(
                    new NodeIdentifier(QName.create(eventQName, "reason"))).body());
            case RESUMED -> {
                // Nothing else
            }
        }
    }

    /**
     * Test that model context is present in AbstractRestconfStreamRegistry and is also modified when
     * onModelContextUpdated is invoked.
     */
    @Test
    void modelContextUpdateTest() {
        assertSame(effectiveModelContext, registry.modelContext());
        registry.onModelContextUpdated(updatedModelContext);
        assertSame(updatedModelContext, registry.modelContext());
    }
}
