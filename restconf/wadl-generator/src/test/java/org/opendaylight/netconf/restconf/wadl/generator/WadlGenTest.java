/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.restconf.wadl.generator;

import static org.junit.Assert.assertEquals;

import com.google.common.collect.Table;
import java.util.Optional;
import java.util.Set;
import org.junit.Test;
import org.opendaylight.yangtools.plugin.generator.api.GeneratedFile;
import org.opendaylight.yangtools.plugin.generator.api.GeneratedFilePath;
import org.opendaylight.yangtools.plugin.generator.api.GeneratedFileType;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public class WadlGenTest {
    @Test
    public void testListGeneration() {
        final WadlGenerator generator = new WadlGenerator();
        final EffectiveModelContext context = YangParserTestUtils.parseYangResourceDirectory("/wadl-gen");
        Table<GeneratedFileType, GeneratedFilePath, GeneratedFile> generatedWadlFiles = generator.generateFiles(context,
            Set.copyOf(context.getModules()), (module, representation) -> Optional.empty());
        assertEquals(3, generatedWadlFiles.size());
        // TODO: more asserts
    }
}
