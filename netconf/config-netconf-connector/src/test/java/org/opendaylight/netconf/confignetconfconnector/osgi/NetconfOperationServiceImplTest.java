/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.confignetconfconnector.osgi;

import static junit.framework.TestCase.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Set;
import org.junit.Test;
import org.opendaylight.controller.config.facade.xml.ConfigSubsystemFacade;
import org.opendaylight.netconf.confignetconfconnector.operations.Commit;
import org.opendaylight.netconf.confignetconfconnector.operations.DiscardChanges;
import org.opendaylight.netconf.confignetconfconnector.operations.Lock;
import org.opendaylight.netconf.confignetconfconnector.operations.UnLock;
import org.opendaylight.netconf.confignetconfconnector.operations.Validate;
import org.opendaylight.netconf.confignetconfconnector.operations.editconfig.EditConfig;
import org.opendaylight.netconf.confignetconfconnector.operations.get.Get;
import org.opendaylight.netconf.confignetconfconnector.operations.getconfig.GetConfig;
import org.opendaylight.netconf.confignetconfconnector.operations.runtimerpc.RuntimeRpc;
import org.opendaylight.netconf.mapping.api.NetconfOperation;
import org.opendaylight.netconf.mapping.api.NetconfOperationService;

public class NetconfOperationServiceImplTest {

    @Test
    public void testOperationService() {
        final ConfigSubsystemFacade configSubsystemFacade = mock(ConfigSubsystemFacade.class);
        final NetconfOperationService netconfOperationService =
                new NetconfOperationServiceImpl(configSubsystemFacade, "reportingID");

        // testing operations in Set from NetconfOperationProvider

        Set<NetconfOperation> operations = netconfOperationService.getNetconfOperations();

        assertTrue(containInstance(operations, GetConfig.class));
        assertTrue(containInstance(operations, EditConfig.class));
        assertTrue(containInstance(operations, Commit.class));
        assertTrue(containInstance(operations, Lock.class));
        assertTrue(containInstance(operations, UnLock.class));
        assertTrue(containInstance(operations, Get.class));
        assertTrue(containInstance(operations, DiscardChanges.class));
        assertTrue(containInstance(operations, Validate.class));
        assertTrue(containInstance(operations, RuntimeRpc.class));

        // verify closing service

        doNothing().when(configSubsystemFacade).close();
        netconfOperationService.close();

        verify(configSubsystemFacade, times(1)).close();
    }

    private boolean containInstance(final Set<NetconfOperation> operations, final Class<?> cls) {
        return operations.stream().filter(cls::isInstance).findFirst().isPresent();
    }
}
