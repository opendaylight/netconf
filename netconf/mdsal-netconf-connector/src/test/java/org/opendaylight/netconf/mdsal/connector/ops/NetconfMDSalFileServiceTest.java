/*
 * Copyright (c) 2017 Frinx s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.mdsal.connector.ops;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.Lists;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.controller.config.util.xml.XmlElement;
import org.opendaylight.controller.config.yang.netconf.mdsal.mapper.FolderWhiteList;
import org.opendaylight.netconf.mdsal.connector.ops.file.MdsalNetconfFileService;
import org.xml.sax.SAXException;

public class NetconfMDSalFileServiceTest extends AbstractNetconfMDSalTest{
    private MdsalNetconfFileService service;

    @Before
    public void setup() {
        FolderWhiteList folderWhiteList = new FolderWhiteList();
        folderWhiteList.setReadOnlyList(Lists.newArrayList("/read/path1","/read/path2"));
        folderWhiteList.setReadWriteList(Lists.newArrayList("/write/path1","/write/path2"));

        service = new MdsalNetconfFileService(folderWhiteList);
    }

    @Test
    public void testCanRead() throws Exception {
        assertTrue(service.canRead(new File("/read/path1/files.xml")));
        assertTrue(service.canRead(new File("/read/path2/folder/files.xml")));
        assertTrue(service.canRead(new File("/write/path1/files.xml")));
        assertFalse(service.canRead(new File("/folder/files.xml")));
        assertFalse(service.canRead(new File("/folder/read/path1/files.xml")));
    }

    @Test
    public void testCanWrite() throws Exception {
        assertFalse(service.canWrite(new File("/read/path1/files.xml")));
        assertFalse(service.canWrite(new File("/read/path2/folder/files.xml")));
        assertTrue(service.canWrite(new File("/write/path1/files.xml")));
        assertTrue(service.canWrite(new File("/write/path2/nextfolder/files.xml")));
        assertFalse(service.canWrite(new File("/folder/files.xml")));
        assertFalse(service.canWrite(new File("/folder/write/path1/files.xml")));
    }

    @Test
    public void testReadContent() throws Exception {
        XmlElement xmlElement = readConfigElementExample();
        assertNotNull(xmlElement);
    }

    @Test
    public void testWriteContent() throws Exception {
        File tempFile = File.createTempFile("tmp", ".xml");
        tempFile.deleteOnExit();

        XmlElement origin = readConfigElementExample();
        service.writeContentToFile(tempFile,origin.getDomElement().getOwnerDocument());
        XmlElement copyOfOrigin = service.readContent(tempFile);

        verifyResponse(origin.getDomElement().getOwnerDocument(), copyOfOrigin.getDomElement().getOwnerDocument());
    }

    @Test
    public void testDeleteFile() throws Exception {
        File file = File.createTempFile("temporally","xml");
        assertTrue(file.exists());
        service.deleteFile(file);
        assertFalse(file.exists());
    }

    private XmlElement readConfigElementExample() throws Exception {
        URL url = NetconfMDSalFileServiceTest.class.getClassLoader().getResource("config-element.xml");
        return service.readContent(new File(url.getFile()));
    }
}
