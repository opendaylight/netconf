/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.services.wrapper;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class RRDSImplTest {
    @Test
    public void testHostMeta() {
        assertEquals("<?xml version='1.0' encoding='UTF-8'?>\n"
            + "<XRD xmlns='http://docs.oasis-open.org/ns/xri/xrd-1.0'>\n"
            + "     <Link rel='restconf' href='/rests'/>\n"
            + "</XRD>", new RootResourceDiscoveryServiceImpl().readXrdData().getEntity());
    }
}
