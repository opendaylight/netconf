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
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.DataTreeIdentifier;
import org.opendaylight.mdsal.binding.api.DataTreeModification;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class OperationalDatastoreListenerTest {

    @Mock
    private DataBroker dataBroker;

    @Test
    public void testDataStoreListener() {
        final InstanceIdentifier<DataObject> instanceIdentifier = InstanceIdentifier.create(DataObject.class);
        final DataTreeIdentifier<DataObject> testId =
                DataTreeIdentifier.create(LogicalDatastoreType.OPERATIONAL, instanceIdentifier);

        final OperationalDatastoreListener<DataObject> op =
                new OperationalDatastoreListener<DataObject>(instanceIdentifier) {
            @Override
            public void onDataTreeChanged(final Collection<DataTreeModification<DataObject>> collection) {
            }
        };
        doReturn(null).when(dataBroker).registerDataTreeChangeListener(any(), any());

        ArgumentCaptor<DataTreeIdentifier> argumentId = ArgumentCaptor.forClass(DataTreeIdentifier.class);
        op.registerOnChanges(dataBroker);
        verify(dataBroker).registerDataTreeChangeListener(argumentId.capture(), any());

        assertEquals(testId, argumentId.getValue());

    }
}
