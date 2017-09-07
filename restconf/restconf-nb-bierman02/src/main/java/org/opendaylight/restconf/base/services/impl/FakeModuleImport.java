/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.base.services.impl;

import com.google.common.base.Preconditions;
import java.util.Date;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.ModuleImport;

/**
 * Fake {@link ModuleImport} implementation used to attach corrent prefix mapping to fake RPCs.
 *
 * @deprecated move to splitted module restconf-nb-rfc8040
 * @author Robert Varga
 */
@Deprecated
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
    public Date getRevision() {
        return module.getRevision();
    }

    @Override
    public String getPrefix() {
        return module.getName();
    }
}
