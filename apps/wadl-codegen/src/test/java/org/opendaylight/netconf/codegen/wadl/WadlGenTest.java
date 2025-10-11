/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.codegen.wadl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.opendaylight.netconf.codegen.wadl.WadlGenerator;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

class WadlGenTest {
    @Test
    void testListGeneration() {
        final var generator = new WadlGenerator();
        final var context = YangParserTestUtils.parseYangResourceDirectory("/wadl-gen");
        final var generatedWadlFiles = generator.generateFiles(context,
            Set.copyOf(context.getModules()), (module, representation) -> Optional.empty());
        assertEquals(3, generatedWadlFiles.size());
        // TODO: more asserts
    }
}
