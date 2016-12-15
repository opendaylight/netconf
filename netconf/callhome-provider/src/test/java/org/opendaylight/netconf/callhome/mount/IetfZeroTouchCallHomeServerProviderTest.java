/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.callhome.mount;


import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;


public class IetfZeroTouchCallHomeServerProviderTest {

    DOMDataBroker mockDOMBroker;
    DataBroker mockDataBroker;
    CallHomeMountDispatcher mockDispacher;

    IetfZeroTouchCallHomeServerProvider instance;

    @Before
    public void setup() {
        mockDOMBroker = mock(DOMDataBroker.class);
        mockDataBroker = mock(DataBroker.class);
        mockDispacher = mock(CallHomeMountDispatcher.class);

        instance = new IetfZeroTouchCallHomeServerProvider(mockDataBroker, mockDispacher);

        Map mockExt = mock(Map.class);
        when(mockDOMBroker.getSupportedExtensions()).thenReturn(mockExt);

    }

    // Why error on command line for vanilla array initializer syntax? wft?

    boolean[] newBooleans(boolean... values) {
        return values;
    }


    @Test
    public void AssertionShouldNotThrowForNonNullObjects() {
        // when
        instance.assertValid(new Object(), "something");
        // then
        // notthing thrown
    }

    @Test(expected = RuntimeException.class)
    public void AssertionShouldThrowForNullObjects() {
        // when
        instance.assertValid(null, "something");
    }

    File createFile() throws IOException {
        File result = File.createTempFile("JUnitConfigurationTestTemp", ".cfg");
        result.deleteOnExit();

        BufferedWriter writer = new BufferedWriter(new FileWriter(result));
        writer.write("DefaultCallHomePort = 1111");
        writer.close();

        return result;
    }

    @Test
    public void coverage() throws Exception {
        instance.close();
    }

}
