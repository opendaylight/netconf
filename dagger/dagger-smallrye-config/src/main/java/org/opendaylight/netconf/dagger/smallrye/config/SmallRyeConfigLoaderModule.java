/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.dagger.smallrye.config;

import com.google.errorprone.annotations.DoNotMock;
import dagger.Module;
import dagger.Provides;
import jakarta.inject.Singleton;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.dagger.config.ConfigLoader;

@Module
@DoNotMock
@NonNullByDefault
public interface SmallRyeConfigLoaderModule {

    @Provides
    @Singleton
    static ConfigLoader configLoader() {
        return new SmallRyeConfigLoader();
    }
}
