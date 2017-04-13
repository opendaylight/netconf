/*
 * Copyright (c) 2017 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.md.sal.rest.schema;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import org.junit.Test;
import org.opendaylight.yangtools.yang.model.api.Module;

public class SchemaExportContentYangBodyWriterTest {

    @Test
    public void secybwInitTest() {
        final SchemaExportContentYangBodyWriter secybw = new SchemaExportContentYangBodyWriter();
        assertNotNull(secybw);
    }

    @Test
    public void isWriteableTest() {
        final SchemaExportContentYangBodyWriter secybw = new SchemaExportContentYangBodyWriter();
        assertNotNull(secybw);
        Class<?> type = SchemaExportContext.class;
        boolean writeable = secybw.isWriteable(type, null, null, null);
        assertTrue(writeable);

        type = Class.class;
        writeable = secybw.isWriteable(type, null, null, null);
        assertTrue(!writeable);
    }

    @Test
    public void getSizeTest() {
        final SchemaExportContentYangBodyWriter secybw = new SchemaExportContentYangBodyWriter();
        assertNotNull(secybw);
        final long size = secybw.getSize(null, null, null, null, null);
        assertEquals(-1, size);
    }

    @Test
    public void writeToTest() throws Exception {
        final SchemaExportContentYangBodyWriter secybw = new SchemaExportContentYangBodyWriter();
        final SchemaExportContext schemaExportContext = mock(SchemaExportContext.class);
        final Module module = mock(Module.class);
        final String source = "source";
        when(module.getSource()).thenReturn(source);
        when(schemaExportContext.getModule()).thenReturn(module);
        final OutputStream stream = new ByteArrayOutputStream();
        secybw.writeTo(schemaExportContext, null, null, null, null, null, stream);
        assertNotNull(secybw);
    }

}
