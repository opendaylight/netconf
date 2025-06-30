/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.opendaylight.netconf.api.EffectiveOperation.CREATE;
import static org.opendaylight.netconf.api.EffectiveOperation.DELETE;
import static org.opendaylight.netconf.api.EffectiveOperation.MERGE;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.api.CapabilityURN;
import org.opendaylight.netconf.api.NetconfDocumentedException;
import org.opendaylight.netconf.client.mdsal.api.NetconfSessionPreferences;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.impl.NetconfBaseOps;
import org.opendaylight.netconf.client.mdsal.impl.NetconfBaseOps.ConfigNodeKey;
import org.opendaylight.netconf.client.mdsal.impl.NetconfRpcFutureCallback;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev241009.connection.oper.available.capabilities.AvailableCapability.CapabilityOrigin;
import org.opendaylight.yangtools.yang.common.ErrorSeverity;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ChoiceNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;

@ExtendWith(MockitoExtension.class)
class RunningTest {
    private static final NormalizedNode EMPTY_NODE = ImmutableNodes.newContainerBuilder()
        .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create("urn:test", "running-test")))
        .build();
    private static final ListenableFuture<DefaultDOMRpcResult> EMPTY_RESULT = Futures.immediateFuture(
        new DefaultDOMRpcResult());
    private static final ListenableFuture<DefaultDOMRpcResult> FAIL_RESULT = Futures.immediateFuture(
        new DefaultDOMRpcResult(RpcResultBuilder.newError(ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED,
            "test failure")));
    private static final YangInstanceIdentifier EMPTY_PATH = YangInstanceIdentifier.of();
    private static final Map<ConfigNodeKey, Optional<NormalizedNode>> CREATE_CONFIG_MAP = Map.of(
        new ConfigNodeKey(EMPTY_PATH, CREATE), Optional.of(EMPTY_NODE));
    private static final Map<ConfigNodeKey, Optional<NormalizedNode>> DELETE_MERGE_CONFIG_MAP = Map.of(
        new ConfigNodeKey(EMPTY_PATH, DELETE), Optional.empty(),
        new ConfigNodeKey(EMPTY_PATH, MERGE), Optional.of(EMPTY_NODE));

    @Mock
    private NetconfBaseOps mockNetconfBaseOps;
    @Mock
    private ChoiceNode mockNode;

    private DataStoreService dataStoreService;

    @BeforeEach
    void beforeEach() {
        final var candidatePreferencies = new NetconfSessionPreferences(ImmutableMap.of(CapabilityURN.WRITABLE_RUNNING,
            mock(CapabilityOrigin.class)), ImmutableMap.of(), null);
        final var testId = new RemoteDeviceId("testId", mock(InetSocketAddress.class));
        dataStoreService = AbstractDataStore.of(testId, mockNetconfBaseOps, candidatePreferencies, true);
    }

    @Test
    void createAndCommitTest() throws Exception {
        doReturn(EMPTY_RESULT).when(mockNetconfBaseOps).lockRunning(any());
        doReturn(EMPTY_RESULT).when(mockNetconfBaseOps).unlockRunning(any());
        doReturn(mockNode).when(mockNetconfBaseOps).createEditConfigStructure(CREATE_CONFIG_MAP);
        doReturn(EMPTY_RESULT).when(mockNetconfBaseOps).editConfigRunning(any(NetconfRpcFutureCallback.class),
            eq(mockNode), eq(false));

        dataStoreService.create(EMPTY_PATH, EMPTY_NODE).get(2, TimeUnit.SECONDS);
        dataStoreService.commit().get(2, TimeUnit.SECONDS);

        verify(mockNetconfBaseOps).lockRunning(any());
        verify(mockNetconfBaseOps).createEditConfigStructure(CREATE_CONFIG_MAP);
        verify(mockNetconfBaseOps).editConfigRunning(any(NetconfRpcFutureCallback.class), eq(mockNode),
            eq(false));
        verify(mockNetconfBaseOps).unlockRunning(any());
    }

    @Test
    void createAndCommitFailedSerializeAnyXmlNode() throws Exception {
        doThrow(new IllegalStateException("Unable to serialize edit config content element for path"))
            .when(mockNetconfBaseOps).createEditConfigStructure(CREATE_CONFIG_MAP);

        dataStoreService.create(YangInstanceIdentifier.of(), EMPTY_NODE).get(2, TimeUnit.SECONDS);

        final var commitFuture = dataStoreService.commit();
        final var commitExecutionException = assertThrows(ExecutionException.class,
            () -> commitFuture.get(2, TimeUnit.SECONDS));
        final var documentedException = assertInstanceOf(NetconfDocumentedException.class,
            commitExecutionException.getCause());
        assertEquals("Failed to create edit-config structure node",
            documentedException.getMessage());
        assertEquals("Unable to serialize edit config content element for path", documentedException
            .getCause().getMessage());
        assertEquals(ErrorTag.OPERATION_FAILED, documentedException.getErrorTag());
        assertEquals(ErrorType.APPLICATION, documentedException.getErrorType());
        assertEquals(ErrorSeverity.ERROR, documentedException.getErrorSeverity());


        verify(mockNetconfBaseOps).createEditConfigStructure(CREATE_CONFIG_MAP);
        verify(mockNetconfBaseOps, never()).lockRunning(any());
        verify(mockNetconfBaseOps, never()).unlockRunning(any());
    }

    @Test
    void deleteMergeAndCommitTest() throws Exception {
        doReturn(EMPTY_RESULT).when(mockNetconfBaseOps).lockRunning(any());
        doReturn(EMPTY_RESULT).when(mockNetconfBaseOps).unlockRunning(any());
        doReturn(mockNode).when(mockNetconfBaseOps).createEditConfigStructure(DELETE_MERGE_CONFIG_MAP);
        doReturn(EMPTY_RESULT).when(mockNetconfBaseOps).editConfigRunning(any(NetconfRpcFutureCallback.class),
            eq(mockNode), eq(false));

        dataStoreService.delete(YangInstanceIdentifier.of()).get(2, TimeUnit.SECONDS);
        dataStoreService.merge(YangInstanceIdentifier.of(), EMPTY_NODE).get(2, TimeUnit.SECONDS);
        dataStoreService.commit().get(2, TimeUnit.SECONDS);

        verify(mockNetconfBaseOps).lockRunning(any());
        verify(mockNetconfBaseOps).createEditConfigStructure(DELETE_MERGE_CONFIG_MAP);
        verify(mockNetconfBaseOps).editConfigRunning(any(NetconfRpcFutureCallback.class), eq(mockNode),
            eq(false));
        verify(mockNetconfBaseOps).unlockRunning(any());
    }

    @Test
    void deleteMergeAndCommitFailsTest() throws Exception {
        doReturn(EMPTY_RESULT).when(mockNetconfBaseOps).lockRunning(any());
        doReturn(EMPTY_RESULT).when(mockNetconfBaseOps).unlockRunning(any());
        doReturn(mockNode).when(mockNetconfBaseOps).createEditConfigStructure(DELETE_MERGE_CONFIG_MAP);
        doReturn(FAIL_RESULT).when(mockNetconfBaseOps).editConfigRunning(any(NetconfRpcFutureCallback.class),
            eq(mockNode), eq(false));

        dataStoreService.delete(YangInstanceIdentifier.of()).get(2, TimeUnit.SECONDS);
        dataStoreService.merge(YangInstanceIdentifier.of(), EMPTY_NODE).get(2, TimeUnit.SECONDS);

        final var commitFuture = dataStoreService.commit();
        final var commitExecutionException = assertThrows(ExecutionException.class,
            () -> commitFuture.get(2, TimeUnit.SECONDS));
        final var commitDocumentedException = assertInstanceOf(NetconfDocumentedException.class,
            commitExecutionException.getCause());
        assertEquals("RPC during tx failed. test failure null", commitDocumentedException.getMessage());
        assertEquals(ErrorTag.OPERATION_FAILED, commitDocumentedException.getErrorTag());
        assertEquals(ErrorType.APPLICATION, commitDocumentedException.getErrorType());
        assertEquals(ErrorSeverity.ERROR, commitDocumentedException.getErrorSeverity());

        verify(mockNetconfBaseOps).lockRunning(any());
        verify(mockNetconfBaseOps).createEditConfigStructure(DELETE_MERGE_CONFIG_MAP);
        verify(mockNetconfBaseOps).editConfigRunning(any(NetconfRpcFutureCallback.class), eq(mockNode),
            eq(false));
        verify(mockNetconfBaseOps).unlockRunning(any());
    }
}
