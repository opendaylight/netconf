/*
 * Copyright (c) 2017 Frinx s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.mdsal.connector.ops;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.StringWriter;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.custommonkey.xmlunit.DetailedDiff;
import org.custommonkey.xmlunit.Diff;
import org.opendaylight.netconf.util.test.NetconfXmlUnitRecursiveQualifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

public abstract class AbstractNetconfMDSalTest {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfMDSalMappingTest.class);

    protected void verifyResponse(Document response, Document template) throws IOException, TransformerException {
        DetailedDiff dd = new DetailedDiff(new Diff(response, template));
        dd.overrideElementQualifier(new NetconfXmlUnitRecursiveQualifier());

        printDocument(response);
        printDocument(template);

        assertTrue(dd.toString(), dd.similar());
    }

    protected String printDocument(Document doc) throws IOException, TransformerException {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.METHOD, "xml");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc),
                new StreamResult(writer));
        LOG.warn(writer.getBuffer().toString());
        return writer.getBuffer().toString();
    }
}
