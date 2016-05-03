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
import org.opendaylight.yanglib.impl.YangLibProvider;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.util.FilesystemSchemaSourceCache;
import org.opendaylight.yangtools.yang.parser.repo.SharedSchemaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class YanglibModule extends org.opendaylight.controller.config.yang.yanglib.impl.AbstractYanglibModule {
    private static final Logger LOG = LoggerFactory.getLogger(YanglibModule.class);

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
        // Start cache and Text to AST transformer
        final SharedSchemaRepository repository = new SharedSchemaRepository("yang-library");
        YangLibProvider provider = new YangLibProvider(repository, getBindingAddr(), getBindingPort());

        final FilesystemSchemaSourceCache<YangTextSchemaSource> cache =
                new FilesystemSchemaSourceCache<>(repository, YangTextSchemaSource.class, new File(getCacheFolder()));
        repository.registerSchemaSourceListener(cache);
        LOG.info("Starting yang library with sources from {}", getCacheFolder());
        getBrokerDependency().registerProvider(provider);
        return provider;
    }
}
