/*
 * Copyright (c) 2017 Frinx s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.mdsal.connector.ops.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Test;
import org.opendaylight.controller.config.util.xml.DocumentedException;
import org.opendaylight.controller.config.util.xml.XmlElement;
import org.opendaylight.netconf.mdsal.connector.ops.Datastore;

public class MdsalNetconfOperationTest {
    private MdsalNetconfOperation operation;
    private static final String FAKE_XML_OPERATION = "<validate xmlns=\"something\">\n" +
            "        <source>\n" +
            "            placeholder\n" +
            "        </source>\n" +
            "    </validate>";

    @Before
    public void setup() {
        operation = new MdsalNetconfOperation("1");
    }

    @Test
    public void testValidDatastore() throws Exception {
        XmlElement xmlElement = prepareXmlElement("<candidate/>");

        MdsalNetconfParameter result = operation.extractSourceParameter(xmlElement);
        assertEquals(Datastore.candidate,result.getDatastore());
        assertEquals(MdsalNetconfParameterType.DATASTORE,result.getType());
    }

    @Test
    public void testValidConfigElement() throws Exception {
        XmlElement xmlElement = prepareXmlElement("<config><top></top></config>");

        MdsalNetconfParameter result = operation.extractSourceParameter(xmlElement);
        assertNotNull(result.getConfigElement());
        assertEquals(MdsalNetconfParameterType.CONFIG,result.getType());
    }

    @Test
    public void testValidFile() throws Exception {
        XmlElement xmlElement = prepareXmlElement("<url>/path/to/file</url>");

        MdsalNetconfParameter result = operation.extractSourceParameter(xmlElement);
        assertEquals("/path/to/file",result.getFile().getAbsolutePath());
        assertEquals(MdsalNetconfParameterType.FILE,result.getType());
    }

    @Test
    public void testIncorrect() {
        try {
            XmlElement xmlElement = prepareXmlElement("");
            operation.extractSourceParameter(xmlElement);
            fail("Should have failed, this is an incorrect request");
        } catch (DocumentedException e) {
            assertTrue(e.getErrorSeverity() == DocumentedException.ErrorSeverity.error);
            assertTrue(e.getErrorTag() == DocumentedException.ErrorTag.invalid_value);
            assertTrue(e.getErrorType() == DocumentedException.ErrorType.application);
        }

        try {
            XmlElement xmlElement = prepareXmlElement("<not-exist></not-exist>");
            operation.extractSourceParameter(xmlElement);
            fail("Should have failed, this is an incorrect request");
        } catch (DocumentedException e) {
            assertTrue(e.getErrorSeverity() == DocumentedException.ErrorSeverity.error);
            assertTrue(e.getErrorTag() == DocumentedException.ErrorTag.missing_element);
            assertTrue(e.getErrorType() == DocumentedException.ErrorType.application);
        }

    }

    public XmlElement prepareXmlElement(String substitution) throws DocumentedException {
        return XmlElement.fromString(FAKE_XML_OPERATION.replaceAll("placeholder",substitution));
    }


}
