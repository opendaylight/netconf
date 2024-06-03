/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil.handler.exi;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import org.opendaylight.netconf.shaded.exificient.core.CodingMode;
import org.opendaylight.netconf.shaded.exificient.core.FidelityOptions;
import org.xmlunit.builder.DiffBuilder;

class NetconfStartExiMessageTest {
    @Test
    void testCreateEmpty() {
        assertCreate("""
              <rpc message-id="id" xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
              <start-exi xmlns="urn:ietf:params:xml:ns:netconf:exi:1.0">
                <alignment>bit-packed</alignment>
              </start-exi>
            </rpc>""", EXIParameters.empty());
    }

    @Test
    void testCreateFull() throws Exception {
        final var fullOptions = FidelityOptions.createDefault();
        fullOptions.setFidelity(FidelityOptions.FEATURE_LEXICAL_VALUE, true);
        fullOptions.setFidelity(FidelityOptions.FEATURE_DTD, true);
        fullOptions.setFidelity(FidelityOptions.FEATURE_COMMENT, true);
        fullOptions.setFidelity(FidelityOptions.FEATURE_PREFIX, true);
        fullOptions.setFidelity(FidelityOptions.FEATURE_PI, true);

        assertCreate("""
              <rpc message-id="id" xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
                <start-exi xmlns="urn:ietf:params:xml:ns:netconf:exi:1.0">
                  <alignment>byte-aligned</alignment>
                  <fidelity>
                    <comments/>
                    <dtd/>
                    <lexical-values/>
                    <pis/>
                    <prefixes/>
                  </fidelity>
                </start-exi>
              </rpc>""", new EXIParameters(CodingMode.BYTE_PACKED, fullOptions));
    }

    private static void assertCreate(final String control, final EXIParameters exiOptions) {
        final var startExiMessage = NetconfStartExiMessageProvider.create(exiOptions, "id");

        final var diff = DiffBuilder.compare(control)
            .withTest(startExiMessage.getDocument())
            .ignoreWhitespace()
            .checkForIdentical()
            .build();
        assertFalse(diff.hasDifferences(), diff.toString());
    }

}
