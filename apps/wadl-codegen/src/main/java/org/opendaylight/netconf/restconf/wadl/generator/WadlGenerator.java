/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.restconf.wadl.generator;

import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Table;
import java.util.Set;
import org.opendaylight.yangtools.plugin.generator.api.FileGenerator;
import org.opendaylight.yangtools.plugin.generator.api.GeneratedFile;
import org.opendaylight.yangtools.plugin.generator.api.GeneratedFileLifecycle;
import org.opendaylight.yangtools.plugin.generator.api.GeneratedFilePath;
import org.opendaylight.yangtools.plugin.generator.api.GeneratedFileType;
import org.opendaylight.yangtools.plugin.generator.api.ModuleResourceResolver;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.Module;

final class WadlGenerator implements FileGenerator {
    @Override
    public Table<GeneratedFileType, GeneratedFilePath, GeneratedFile> generateFiles(final EffectiveModelContext context,
            final Set<Module> localModules, final ModuleResourceResolver moduleResourcePathResolver) {
        final var result = ImmutableTable.<GeneratedFileType, GeneratedFilePath, GeneratedFile>builder();

        for (Module module : localModules) {
            final CharSequence body = new WadlTemplate(context, module).body();
            if (body != null) {
                result.put(GeneratedFileType.RESOURCE, GeneratedFilePath.ofPath(module.getName() + ".wadl"),
                    GeneratedFile.of(GeneratedFileLifecycle.TRANSIENT, body));
            }
        }
        return result.build();
    }
}
