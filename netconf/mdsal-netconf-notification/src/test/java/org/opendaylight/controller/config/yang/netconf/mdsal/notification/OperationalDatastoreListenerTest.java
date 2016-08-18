/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.controller.config.yang.netconf.mdsal.notification;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import java.util.Collection;
import javax.annotation.Nonnull;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public class OperationalDatastoreListenerTest {

    @Mock
    private DataBroker dataBroker;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testDataStoreListener(){
        final InstanceIdentifier<DataObject> instanceIdentifier = InstanceIdentifier.create(DataObject.class);
        final DataTreeIdentifier<DataObject> testId =
                new DataTreeIdentifier<>(LogicalDatastoreType.OPERATIONAL, instanceIdentifier);

        final OperationalDatastoreListener<DataObject> op = new OperationalDatastoreListener<DataObject>(instanceIdentifier) {
            @Override
            public void onDataTreeChanged(@Nonnull Collection collection) {
            }
        };
        doReturn(null).when(dataBroker).registerDataTreeChangeListener(any(), any());

        ArgumentCaptor<DataTreeIdentifier> argumentId = ArgumentCaptor.forClass(DataTreeIdentifier.class);
        op.registerOnChanges(dataBroker);
        verify(dataBroker).registerDataTreeChangeListener(argumentId.capture(), any());

        assertEquals(testId, argumentId.getValue());

    }

}
