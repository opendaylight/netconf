/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.mdsal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.spi.FixedDOMSchemaService;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.mdsal.spi.DOMServerRpcOperations;
import org.opendaylight.restconf.mdsal.spi.DOMServerStrategy;
import org.opendaylight.restconf.mdsal.spi.data.MdsalRestconfStrategy;
import org.opendaylight.restconf.server.api.DataGetResult;
import org.opendaylight.restconf.server.api.testlib.AbstractJukeboxTest;
import org.opendaylight.restconf.server.api.testlib.CompletingServerRequest;
import org.opendaylight.restconf.server.spi.CompositeServerStrategy;
import org.opendaylight.restconf.server.spi.ExportingServerModulesOperations;
import org.opendaylight.restconf.server.spi.NotSupportedServerActionOperations;
import org.opendaylight.restconf.server.spi.NotSupportedServerModulesOperations;
import org.opendaylight.restconf.server.spi.NotSupportedServerMountPointResolver;
import org.opendaylight.restconf.server.spi.NotSupportedServerRpcOperations;
import org.opendaylight.restconf.server.spi.ServerStrategy.StrategyAndPath;
import org.opendaylight.yangtools.databind.DatabindContext;
import org.opendaylight.yangtools.databind.RequestException;
import org.opendaylight.yangtools.yang.common.ErrorMessage;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;

@ExtendWith(MockitoExtension.class)
public class MdsalServerStrategyTest extends AbstractJukeboxTest {
    @Mock
    private DOMDataBroker dataBroker;
    @Mock
    private DOMRpcService rpcService;
    @Mock
    private DOMMountPointService mountPointService;
    @Mock
    private DOMMountPoint mountPoint;

    @Test
    void testGetRestconfStrategyLocal() throws Exception {
        final var strategy = jukeboxStrategy();
        assertEquals(new StrategyAndPath(strategy, ApiPath.empty()), strategy.resolveStrategy(ApiPath.empty()));
    }

    @Test
    void testGetRestconfStrategyMountDataBroker() throws Exception {
        doReturn(Optional.empty()).when(mountPoint).getService(DOMServerStrategy.class);
        doReturn(Optional.of(dataBroker)).when(mountPoint).getService(DOMDataBroker.class);
        doReturn(Optional.of(rpcService)).when(mountPoint).getService(DOMRpcService.class);
        doReturn(Optional.of(new FixedDOMSchemaService(JUKEBOX_SCHEMA))).when(mountPoint)
            .getService(DOMSchemaService.class);
        doReturn(Optional.empty()).when(mountPoint).getService(DOMMountPointService.class);
        doReturn(Optional.empty()).when(mountPoint).getService(DOMActionService.class);
        doReturn(Optional.of(mountPoint)).when(mountPointService).getMountPoint(YangInstanceIdentifier.of());

        final var root = jukeboxStrategy();
        final var strategyAndPath = root.resolveStrategy(ApiPath.parse("yang-ext:mount"));
        assertEquals(ApiPath.empty(), strategyAndPath.path());
        final var strategy = assertInstanceOf(MdsalServerStrategy.class, strategyAndPath.strategy());
        assertNotSame(root, strategy);
        assertInstanceOf(MdsalRestconfStrategy.class, strategy.data());
    }

    @Test
    void testGetRestconfStrategyFromMountPointDOMServerStrategy() throws Exception {
        // Prepare DOMServerStrategy instance.
        final var databindContext = DatabindContext.ofModel(JUKEBOX_SCHEMA);
        final var mdsalRestconfStrategy = new MdsalRestconfStrategy(databindContext, dataBroker);
        final var compositeServerStrategy = new CompositeServerStrategy(databindContext,
            NotSupportedServerMountPointResolver.INSTANCE, NotSupportedServerActionOperations.INSTANCE,
            mdsalRestconfStrategy, new ExportingServerModulesOperations(JUKEBOX_SCHEMA),
            NotSupportedServerRpcOperations.INSTANCE);
        final var domServerStrategy = new DOMServerStrategy(compositeServerStrategy);

        // Prepare environment.
        doReturn(Optional.of(domServerStrategy)).when(mountPoint).getService(DOMServerStrategy.class);
        doReturn(Optional.of(mountPoint)).when(mountPointService).getMountPoint(YangInstanceIdentifier.of());

        // Resolve strategy for mountPoint.
        final var strategyAndPath = jukeboxStrategy().resolveStrategy(ApiPath.parse("yang-ext:mount"));

        // Verify provided strategy.
        assertEquals(ApiPath.empty(), strategyAndPath.path());
        final var strategy = assertInstanceOf(CompositeServerStrategy.class, strategyAndPath.strategy());
        assertInstanceOf(MdsalRestconfStrategy.class, strategy.data());
    }

    @Test
    void testGetRestconfStrategyMountNone() throws Exception {
        doReturn(Optional.empty()).when(mountPoint).getService(DOMServerStrategy.class);
        doReturn(Optional.empty()).when(mountPoint).getService(DOMDataBroker.class);
        doReturn(Optional.empty()).when(mountPoint).getService(DOMMountPointService.class);
        doReturn(Optional.empty()).when(mountPoint).getService(DOMActionService.class);
        doReturn(Optional.of(rpcService)).when(mountPoint).getService(DOMRpcService.class);
        doReturn(Optional.of(new FixedDOMSchemaService(JUKEBOX_SCHEMA))).when(mountPoint)
            .getService(DOMSchemaService.class);
        doReturn(Optional.of(mountPoint)).when(mountPointService).getMountPoint(YangInstanceIdentifier.of());

        final var strategy = jukeboxStrategy();
        final var mountPath = ApiPath.parse("yang-ext:mount");

        final var strategyAndPath = strategy.resolveStrategy(mountPath);
        assertEquals(ApiPath.empty(), strategyAndPath.path());

        final var request = new CompletingServerRequest<DataGetResult>();
        strategyAndPath.strategy().dataGET(request);

        final var errors = assertThrows(RequestException.class, request::getResult).errors();
        assertEquals(1, errors.size());
        final var error = errors.get(0);
        assertEquals(ErrorType.PROTOCOL, error.type());
        assertEquals(ErrorTag.OPERATION_NOT_SUPPORTED, error.tag());
        assertEquals(new ErrorMessage("Data request not supported"), error.message());
        final var errorPath = error.path();
        assertNotNull(errorPath);
        assertEquals(YangInstanceIdentifier.of(), errorPath.path());
    }

    private MdsalServerStrategy jukeboxStrategy() {
        return new MdsalServerStrategy(JUKEBOX_DATABIND, new MdsalMountPointResolver(mountPointService),
            NotSupportedServerActionOperations.INSTANCE, new MdsalRestconfStrategy(JUKEBOX_DATABIND, dataBroker),
            NotSupportedServerModulesOperations.INSTANCE, new DOMServerRpcOperations(rpcService));
    }
}
