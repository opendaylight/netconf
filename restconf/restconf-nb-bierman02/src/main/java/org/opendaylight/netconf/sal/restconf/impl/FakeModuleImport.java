/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.restconf.impl;

import com.google.common.base.Preconditions;
import java.util.Optional;
import org.opendaylight.yangtools.concepts.SemVer;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.ModuleImport;

/**
 * Fake {@link ModuleImport} implementation used to attach current prefix mapping to fake RPCs.
 *
 * @author Robert Varga
 */
final class FakeModuleImport implements ModuleImport {
    private final Module module;

    FakeModuleImport(final Module module) {
        this.module = Preconditions.checkNotNull(module);
    }

    @Override
    public String getModuleName() {
        return module.getName();
    }

    @Override
    public Optional<Revision> getRevision() {
        return module.getRevision();
    }

    @Override
    public String getPrefix() {
        return module.getName();
    }

    @Override
    public Optional<String> getDescription() {
        return module.getDescription();
    }

    @Override
    public Optional<String> getReference() {
        return module.getReference();
    }

    @Override
    public Optional<SemVer> getSemanticVersion() {
        return module.getSemanticVersion();
    }
}
