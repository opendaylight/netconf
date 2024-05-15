/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.nettyutil.handler.exi;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.shaded.exificient.core.CodingMode;
import org.opendaylight.netconf.shaded.exificient.core.FidelityOptions;

class EXIParametersTest {

    @ParameterizedTest
    @MethodSource("getData")
    void testFromXmlElement(final String sourceXml, final CodingMode coding, final FidelityOptions fidelity)
            throws Exception {
        final var opts =
            EXIParameters.fromXmlElement(
                XmlElement.fromDomElement(
                    XmlUtil.readXmlToElement(sourceXml)));

        final var factory = opts.getFactory();
        assertEquals(fidelity, factory.getFidelityOptions());
        assertEquals(coding, factory.getCodingMode());
    }

    static Stream<Arguments> getData() throws Exception {
        final var noChangeXml =
            "<start-exi xmlns=\"urn:ietf:params:xml:ns:netconf:exi:1.0\">\n"
            + "<alignment>bit-packed</alignment>\n"
            + "</start-exi>\n";


        final var fullOptionsXml =
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

        final var fullOptions = FidelityOptions.createDefault();
        fullOptions.setFidelity(FidelityOptions.FEATURE_LEXICAL_VALUE, true);
        fullOptions.setFidelity(FidelityOptions.FEATURE_DTD, true);
        fullOptions.setFidelity(FidelityOptions.FEATURE_COMMENT, true);
        fullOptions.setFidelity(FidelityOptions.FEATURE_PREFIX, true);
        fullOptions.setFidelity(FidelityOptions.FEATURE_PI, true);

        return Stream.of(
            Arguments.of(noChangeXml, CodingMode.BIT_PACKED, FidelityOptions.createDefault()),
            Arguments.of(fullOptionsXml, CodingMode.BYTE_PACKED, fullOptions)
        );
    }
}
