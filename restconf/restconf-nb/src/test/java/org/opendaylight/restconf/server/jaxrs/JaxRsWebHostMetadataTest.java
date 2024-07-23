/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.jaxrs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class JaxRsWebHostMetadataTest {
    private final JaxRsWebHostMetadata svc = new JaxRsWebHostMetadata("fooBarBaz");

    @Test
    void testJsonData() {
        final var response = svc.readJsonData();
        assertEquals(200, response.getStatus());
        assertEquals("""
            {
              "links" : {
                "rel" : "restconf",
                "href" : "/fooBarBaz"
              }
            }""", response.getEntity());
    }

    @Test
    void testXrdData() {
        final var response = svc.readXrdData();
        assertEquals(200, response.getStatus());
        assertEquals("""
            <?xml version='1.0' encoding='UTF-8'?>
            <XRD xmlns='http://docs.oasis-open.org/ns/xri/xrd-1.0'>
              <Link rel='restconf' href='/fooBarBaz'/>
            </XRD>""", response.getEntity());
    }
}
