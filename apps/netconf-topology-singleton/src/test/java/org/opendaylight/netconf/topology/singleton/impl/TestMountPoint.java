/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class TestMountPoint extends AbstractBaseSchemasTest {
    private static final Logger LOG = LoggerFactory.getLogger(TestMountPoint.class);

    @Test
    public void testEndToEnd() throws Exception {
        for (int i = 0; i < 100; i++) {
            LOG.info("");
            LOG.info("-------------------- Iteration {} ----------------------", i);
            LOG.info("");
            final var mountPointEndToEndTest = new MountPointEndToEndTest();
            mountPointEndToEndTest.setUp();
            mountPointEndToEndTest.test();
            mountPointEndToEndTest.tearDown();
        }
    }
}
