/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.nettyutil.handler.exi;

import static org.junit.Assert.assertFalse;

import com.siemens.ct.exi.core.CodingMode;
import com.siemens.ct.exi.core.FidelityOptions;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.diff.DefaultNodeMatcher;
import org.xmlunit.diff.Diff;
import org.xmlunit.diff.ElementSelectors;

@RunWith(Parameterized.class)
public class NetconfStartExiMessageTest {

    @Parameterized.Parameters
    public static Iterable<Object[]> data() throws Exception {
        final String noChangeXml = "<rpc xmlns:ns0=\"urn:ietf:params:xml:ns:netconf:base:1.0\" "
                + "ns0:message-id=\"id\" xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n"
                + "<start-exi xmlns=\"urn:ietf:params:xml:ns:netconf:exi:1.0\">\n"
                + "<alignment>bit-packed</alignment>\n"
                + "</start-exi>\n"
                + "</rpc>";


        final String fullOptionsXml = "<rpc xmlns:ns0=\"urn:ietf:params:xml:ns:netconf:base:1.0\" "
                + "ns0:message-id=\"id\" xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n"
                + "<start-exi xmlns=\"urn:ietf:params:xml:ns:netconf:exi:1.0\">\n"
                + "<alignment>byte-aligned</alignment>\n"
                + "<fidelity>\n"
                + "<comments/>\n"
                + "<dtd/>\n"
                + "<lexical-values/>\n"
                + "<pis/>\n"
                + "<prefixes/>\n"
                + "</fidelity>\n"
                + "</start-exi>\n"
                + "</rpc>";

        final FidelityOptions fullOptions = FidelityOptions.createDefault();
        fullOptions.setFidelity(FidelityOptions.FEATURE_LEXICAL_VALUE, true);
        fullOptions.setFidelity(FidelityOptions.FEATURE_DTD, true);
        fullOptions.setFidelity(FidelityOptions.FEATURE_COMMENT, true);
        fullOptions.setFidelity(FidelityOptions.FEATURE_PREFIX, true);
        fullOptions.setFidelity(FidelityOptions.FEATURE_PI, true);

        return Arrays.asList(new Object[][]{
            {noChangeXml, EXIParameters.empty()},
            {fullOptionsXml, new EXIParameters(CodingMode.BYTE_PACKED, fullOptions)},
        });
    }

    private final String controlXml;
    private final EXIParameters exiOptions;

    public NetconfStartExiMessageTest(final String controlXml, final EXIParameters exiOptions) {
        this.controlXml = controlXml;
        this.exiOptions = exiOptions;
    }

    @Test
    public void testCreate() {
        final NetconfStartExiMessage startExiMessage = NetconfStartExiMessage.create(exiOptions, "id");

        final Diff diff = DiffBuilder.compare(controlXml)
                .withTest(startExiMessage.getDocument())
                .ignoreWhitespace()
                .withNodeMatcher(new DefaultNodeMatcher(ElementSelectors.byName))
                .checkForSimilar()
                .build();
        assertFalse(diff.toString(), diff.hasDifferences());
    }
}