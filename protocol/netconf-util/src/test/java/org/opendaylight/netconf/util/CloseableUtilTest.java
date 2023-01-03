/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;

import java.util.List;
import org.junit.Test;

public class CloseableUtilTest {
    @Test
    public void testCloseAllFail() {
        final AutoCloseable failingCloseable = () -> {
            throw new RuntimeException("testing failing close");
        };

        final RuntimeException ex = assertThrows(RuntimeException.class,
            () -> CloseableUtil.closeAll(List.of(failingCloseable, failingCloseable)));
        assertEquals(1, ex.getSuppressed().length);
    }

    @Test
    public void testCloseAll() throws Exception {
        final AutoCloseable failingCloseable = mock(AutoCloseable.class);
        doNothing().when(failingCloseable).close();
        CloseableUtil.closeAll(List.of(failingCloseable, failingCloseable));
    }
}