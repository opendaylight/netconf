/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.mdsal.notification.impl;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.DataTreeIdentifier;
import org.opendaylight.mdsal.binding.api.DataTreeModification;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.yangtools.yang.binding.ChildOf;
import org.opendaylight.yangtools.yang.binding.DataRoot;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class OperationalDatastoreListenerTest {
    @Mock
    private DataBroker dataBroker;
    @Captor
    private ArgumentCaptor<DataTreeIdentifier<?>> argumentId;

    @Test
    public void testDataStoreListener() {
        final InstanceIdentifier<TestInterface> instanceIdentifier = InstanceIdentifier.create(TestInterface.class);

        final var op = new OperationalDatastoreListener<>(instanceIdentifier) {
            @Override
            public void onDataTreeChanged(final Collection<DataTreeModification<TestInterface>> collection) {
                // no-op
            }
        };
        doReturn(null).when(dataBroker).registerDataTreeChangeListener(any(), any());

        op.registerOnChanges(dataBroker);
        verify(dataBroker).registerDataTreeChangeListener(argumentId.capture(), any());

        assertEquals(DataTreeIdentifier.create(LogicalDatastoreType.OPERATIONAL, instanceIdentifier),
            argumentId.getValue());
    }

    interface TestInterface extends ChildOf<DataRoot> {
        @Override
        default Class<TestInterface> implementedInterface() {
            return TestInterface.class;
        }
    }
}
