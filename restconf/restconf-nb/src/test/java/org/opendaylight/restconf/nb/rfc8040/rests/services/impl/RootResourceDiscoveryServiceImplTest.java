/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class RootResourceDiscoveryServiceImplTest {
    private final RootResourceDiscoveryServiceImpl svc = new RootResourceDiscoveryServiceImpl("fooBarBaz");

    @Test
    public void testJsonData() {
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
    public void testXrdData() {
        final var response = svc.readXrdData();
        assertEquals(200, response.getStatus());
        assertEquals("""
            <?xml version='1.0' encoding='UTF-8'?>
            <XRD xmlns='http://docs.oasis-open.org/ns/xri/xrd-1.0'>
              <Link rel='restconf' href='/fooBarBaz'/>
            </XRD>""", response.getEntity());
    }
}
