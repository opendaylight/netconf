/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.dagger.config;

import dagger.Binds;
import dagger.Module;
import jakarta.inject.Singleton;

/**
 * A Dagger module providing {@link ConfigLoader}.
 */
@Module
public abstract class SpringbootConfigLoaderModule {

    @Binds
    @Singleton
    public abstract ConfigLoader configLoader(SpringbootConfigLoader springbootConfigLoader);
}
