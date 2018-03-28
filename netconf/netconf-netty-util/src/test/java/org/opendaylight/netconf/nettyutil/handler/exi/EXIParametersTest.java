/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.nettyutil.handler.exi;

import static org.junit.Assert.assertEquals;

import com.siemens.ct.exi.CodingMode;
import com.siemens.ct.exi.EXIFactory;
import com.siemens.ct.exi.FidelityOptions;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.opendaylight.controller.config.util.xml.XmlElement;
import org.opendaylight.controller.config.util.xml.XmlUtil;

@RunWith(Parameterized.class)
public class EXIParametersTest {

    @Parameterized.Parameters
    public static Iterable<Object[]> data() throws Exception {
        final String noChangeXml =
                "<start-exi xmlns=\"urn:ietf:params:xml:ns:netconf:exi:1.0\">\n"
                + "<alignment>bit-packed</alignment>\n"
                + "</start-exi>\n";


        final String fullOptionsXml =
                "<start-exi xmlns=\"urn:ietf:params:xml:ns:netconf:exi:1.0\">\n"
                + "<alignment>byte-aligned</alignment>\n"
                + "<fidelity>\n"
                + "<comments/>\n"
                + "<dtd/>\n"
                + "<lexical-values/>\n"
                + "<pis/>\n"
                + "<prefixes/>\n"
                + "</fidelity>\n"
                + "</start-exi>\n";

        final FidelityOptions fullOptions = FidelityOptions.createDefault();
        fullOptions.setFidelity(FidelityOptions.FEATURE_LEXICAL_VALUE, true);
        fullOptions.setFidelity(FidelityOptions.FEATURE_DTD, true);
        fullOptions.setFidelity(FidelityOptions.FEATURE_COMMENT, true);
        fullOptions.setFidelity(FidelityOptions.FEATURE_PREFIX, true);
        fullOptions.setFidelity(FidelityOptions.FEATURE_PI, true);

        return Arrays.asList(new Object[][]{
            {noChangeXml, CodingMode.BIT_PACKED, FidelityOptions.createDefault()},
            {fullOptionsXml, CodingMode.BYTE_PACKED, fullOptions},
        });
    }

    private final String sourceXml;
    private final CodingMode coding;
    private final FidelityOptions fidelity;

    public EXIParametersTest(final String sourceXml, final CodingMode coding, final FidelityOptions fidelity) {
        this.sourceXml = sourceXml;
        this.coding = coding;
        this.fidelity = fidelity;
    }

    @Test
    public void testFromXmlElement() throws Exception {
        final EXIParameters opts =
                EXIParameters.fromXmlElement(
                        XmlElement.fromDomElement(
                                XmlUtil.readXmlToElement(sourceXml)));

        final EXIFactory factory = opts.getFactory();
        assertEquals(fidelity, factory.getFidelityOptions());
        assertEquals(coding, factory.getCodingMode());
    }
}