/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.controller.config.yang.yanglib.impl;

import java.io.File;
import org.opendaylight.controller.config.api.JmxAttributeValidationException;

public class YanglibModule extends org.opendaylight.controller.config.yang.yanglib.impl.AbstractYanglibModule {
    public YanglibModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver) {
        super(identifier, dependencyResolver);
    }

    public YanglibModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver, org.opendaylight.controller.config.yang.yanglib.impl.YanglibModule oldModule, java.lang.AutoCloseable oldInstance) {
        super(identifier, dependencyResolver, oldModule, oldInstance);
    }

    @Override
    public void customValidation() {
        JmxAttributeValidationException.checkNotNull(getCacheFolder(), cacheFolderJmxAttribute);
        final File file = new File(getCacheFolder());
        JmxAttributeValidationException
                .checkCondition(file.exists(), "Non existing cache file", cacheFolderJmxAttribute);
        JmxAttributeValidationException
                .checkCondition(file.isDirectory(), "Non directory cache file", cacheFolderJmxAttribute);
    }

    @Override
    public java.lang.AutoCloseable createInstance() {
        // TODO:implement
        throw new java.lang.UnsupportedOperationException();
    }

}
