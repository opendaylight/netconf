/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client;

import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class HardcodedNamespaceResolverTest {
    @Test
    public void testResolver() throws Exception {
        final HardcodedNamespaceResolver hardcodedNamespaceResolver =
                new HardcodedNamespaceResolver("prefix", "namespace");

        assertEquals("namespace", hardcodedNamespaceResolver.getNamespaceURI("prefix"));
        final IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> hardcodedNamespaceResolver.getNamespaceURI("unknown"));
        assertThat(ex.getMessage(), startsWith("Prefix mapping not found for "));

        assertNull(hardcodedNamespaceResolver.getPrefix("any"));
        assertNull(hardcodedNamespaceResolver.getPrefixes("any"));
    }
}