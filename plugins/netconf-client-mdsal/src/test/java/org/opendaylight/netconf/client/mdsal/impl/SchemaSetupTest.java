/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.common.util.concurrent.Futures;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.client.mdsal.AbstractTestModelTest;
import org.opendaylight.netconf.client.mdsal.NetconfDevice;
import org.opendaylight.netconf.client.mdsal.api.NetconfDeviceSchemas;
import org.opendaylight.netconf.client.mdsal.api.NetconfSessionPreferences;
import org.opendaylight.netconf.client.mdsal.api.ProvidedSources;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev241009.connection.oper.available.capabilities.AvailableCapability.CapabilityOrigin;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev241009.connection.oper.available.capabilities.AvailableCapabilityBuilder;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.source.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.api.source.YangTextSource;
import org.opendaylight.yangtools.yang.model.api.stmt.FeatureSet;
import org.opendaylight.yangtools.yang.model.repo.api.EffectiveModelContextFactory;
import org.opendaylight.yangtools.yang.model.repo.api.MissingSchemaSourceException;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaRepository;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaResolutionException;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceProvider;

@ExtendWith(MockitoExtension.class)
class SchemaSetupTest extends AbstractTestModelTest {
    private static final RemoteDeviceId DEVICE_ID = new RemoteDeviceId("someDevice", new InetSocketAddress(42));
    private static final String TEST_NAMESPACE = "test:namespace";
    private static final String TEST_MODULE = "test-module";
    private static final String TEST_REVISION = "2013-07-22";
    private static final SourceIdentifier TEST_SID = new SourceIdentifier(TEST_MODULE, TEST_REVISION);
    private static final SourceIdentifier TEST_SID2 = new SourceIdentifier(TEST_MODULE + "2", TEST_REVISION);
    private static final QName TEST_QNAME = QName.create(TEST_NAMESPACE, TEST_REVISION, TEST_MODULE);
    private static final QName TEST_QNAME2 = QName.create(TEST_NAMESPACE, TEST_REVISION, TEST_MODULE + "2");

    @Mock
    private EffectiveModelContextFactory contextFactory;
    @Mock
    private SchemaRepository schemaRepository;
    @Mock
    private SchemaSourceProvider<YangTextSource> sourceProvider;
    @Mock
    private YangTextSource source;

    @Test
    void testNetconfDeviceFlawedModelFailedResolution() throws Exception {
        final var ex = new SchemaResolutionException("fail first", TEST_SID, new Throwable("YangTools parser fail"));
        doAnswer(invocation -> invocation.getArgument(0, Collection.class).size() == 2
            ? Futures.immediateFailedFuture(ex) : TEST_MODEL_FUTURE)
            .when(contextFactory).createEffectiveModelContext(anyCollection());

        doReturn(Futures.immediateFuture(source)).when(schemaRepository)
            .getSchemaSource(any(), eq(YangTextSource.class));

        final var setup = new SchemaSetup(schemaRepository, contextFactory, DEVICE_ID,
            new NetconfDeviceSchemas(Set.of(TEST_QNAME, TEST_QNAME2), FeatureSet.builder().build(), Set.of(),
                List.of(new ProvidedSources<>(YangTextSource.class, sourceProvider, Set.of(TEST_QNAME, TEST_QNAME2)))),
            NetconfSessionPreferences.fromStrings(Set.of()));

        final var result = Futures.getDone(setup.startResolution());
        verify(contextFactory, times(2)).createEffectiveModelContext(anyCollection());
        assertSame(TEST_MODEL, result.modelContext());
    }

    @Test
    void testNetconfDeviceMissingSource() throws Exception {
        // Make fallback attempt to fail due to empty resolved sources
        final var ex = new MissingSchemaSourceException(TEST_SID, "fail first");
        doReturn(Futures.immediateFailedFuture(ex))
                .when(schemaRepository).getSchemaSource(TEST_SID, YangTextSource.class);
        doReturn(Futures.immediateFuture(source)).when(schemaRepository)
            .getSchemaSource(TEST_SID2, YangTextSource.class);
        doReturn(TEST_MODEL_FUTURE).when(contextFactory).createEffectiveModelContext(anyCollection());

        final var setup = new SchemaSetup(schemaRepository, contextFactory, DEVICE_ID,
            new NetconfDeviceSchemas(Set.of(TEST_QNAME, TEST_QNAME2), FeatureSet.builder().build(), Set.of(),
                List.of(new ProvidedSources<>(YangTextSource.class, sourceProvider, Set.of(TEST_QNAME, TEST_QNAME2)))),
            NetconfSessionPreferences.fromStrings(Set.of()));

        final var result = Futures.getDone(setup.startResolution());
        final ArgumentCaptor<Collection<SourceIdentifier>> captor = ArgumentCaptor.captor();
        verify(contextFactory).createEffectiveModelContext(captor.capture());
        assertEquals(List.of(TEST_SID2), captor.getValue());
        assertSame(TEST_MODEL, result.modelContext());
    }

    @Test
    void testNetconfDeviceNotificationsCapabilityIsNotPresent() throws Exception {
        doReturn(Futures.immediateFuture(source)).when(schemaRepository)
            .getSchemaSource(any(), eq(YangTextSource.class));
        doReturn(TEST_MODEL_FUTURE).when(contextFactory).createEffectiveModelContext(anyCollection());

        final var setup = new SchemaSetup(schemaRepository, contextFactory, DEVICE_ID,
            new NetconfDeviceSchemas(Set.of(TEST_QNAME), FeatureSet.builder().build(), Set.of(),
                List.of(new ProvidedSources<>(YangTextSource.class, sourceProvider, Set.of(TEST_QNAME)))),
            NetconfSessionPreferences.fromStrings(
                Set.of(TEST_NAMESPACE + "?module=" + TEST_MODULE + "&amp;revision=" + TEST_REVISION)));

        final var result = Futures.getDone(setup.startResolution());
        assertEquals(Set.of(new AvailableCapabilityBuilder()
            .setCapability("(test:namespace?revision=2013-07-22)test-module")
            .setCapabilityOrigin(CapabilityOrigin.DeviceAdvertised)
            .build()), result.capabilities().resolvedCapabilities());
    }

    @Test
    void testNetconfDeviceReconnectWhileFetchingSources() throws Exception {
        // Return exception with first call of getSchema on any source.
        final var cancellationException = new MissingSchemaSourceException(TEST_SID,
            "Request canceled, device is going to reconnect", new CancellationException("test message"));
        doReturn(Futures.immediateFailedFuture(cancellationException))
            .when(schemaRepository).getSchemaSource(any(SourceIdentifier.class), eq(YangTextSource.class));

        // Call SchemaSetup with a single thread to simulate a scenario where all processor threads are already
        // occupied. The next schema source fetch execution is waiting in a thread pool for execution by parallelStream.
        // If an already processing source fails and flags reconnectingDevice as true, the next fetching source should
        // be skipped.
        final SchemaSetup setup;
        try (var oneThread = new ForkJoinPool(1)) {
            final var submit = oneThread.submit(() -> new SchemaSetup(schemaRepository, contextFactory, DEVICE_ID,
                new NetconfDeviceSchemas(Set.of(TEST_QNAME, TEST_QNAME2), FeatureSet.builder().build(), Set.of(),
                    List.of(new ProvidedSources<>(YangTextSource.class, sourceProvider,
                        Set.of(TEST_QNAME, TEST_QNAME2)))),
                NetconfSessionPreferences.fromStrings(Set.of())));
            setup = submit.get(1, TimeUnit.SECONDS);
        }

        // Verify that creating the device schema fails because of a device reconnection.
        final var executionEx = assertThrows(ExecutionException.class, () -> setup.startResolution()
            .get(1, TimeUnit.SECONDS));
        final var emptyContextEx = assertInstanceOf(NetconfDevice.EmptySchemaContextException.class,
            executionEx.getCause());
        assertEquals("RemoteDeviceId[name=someDevice, address=0.0.0.0/0.0.0.0:42]: Device is reconnecting",
            emptyContextEx.getMessage());

        // Verify that the second schema source fetch was not executed because the previous one failed,
        // leading to device reconnection.
        verify(schemaRepository, times(1)).getSchemaSource(any(SourceIdentifier.class), eq(YangTextSource.class));
    }
}
