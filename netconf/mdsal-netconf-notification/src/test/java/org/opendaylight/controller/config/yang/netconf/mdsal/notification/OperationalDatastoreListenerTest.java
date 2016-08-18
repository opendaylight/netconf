/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.controller.config.yang.netconf.mdsal.notification;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import javax.annotation.Nonnull;
import java.util.Collection;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class OperationalDatastoreListenerTest {

    @Mock
    private DataBroker dataBroker;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testDataStoreListener(){
        InstanceIdentifier instanceIdentifier = mock(InstanceIdentifier.class);

        OperationalDatastoreListener op = new OperationalDatastoreListener(instanceIdentifier) {
            @Override
            public void onDataTreeChanged(@Nonnull Collection collection) {

            }
        };
        doReturn(null).when(dataBroker).registerDataTreeChangeListener(any(DataTreeIdentifier.class), Mockito.eq(op));

        op.registerOnChanges(dataBroker);
        verify(dataBroker).registerDataTreeChangeListener(any(DataTreeIdentifier.class), Mockito.eq(op));
    }
}
