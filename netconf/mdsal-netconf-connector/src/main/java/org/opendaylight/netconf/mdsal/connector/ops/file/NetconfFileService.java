/*
 * Copyright (c) 2017 Frinx s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.mdsal.connector.ops.file;

import java.io.File;
import java.io.IOException;
import javax.xml.transform.TransformerException;
import org.opendaylight.controller.config.util.xml.XmlElement;
import org.w3c.dom.Document;

public interface NetconfFileService {
    boolean canRead(File file);
    boolean canWrite(File file);
    XmlElement readContent(File file) throws Exception;
    void writeContentToFile(File file, Document document) throws TransformerException, IOException;
    boolean deleteFile(File file);
}
