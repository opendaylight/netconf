/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.dagger.aaa;

import com.google.errorprone.annotations.DoNotMock;
import dagger.Module;
import org.eclipse.jdt.annotation.NonNullByDefault;


/**
 * A Dagger module aggregator providing basic AAA services to provide insecure connection without encryption.
 */
@Module(includes = {
    JerseyServletSupportModule.class,
    JettyWebServerModule.class,
    CustomFilterAdapterConfigurationModule.class,
    AAAShiroWebEnvironmentModule.class,
    NoEncryptionServiceModule.class,
    NoWebContextSecurerModule.class
})
@DoNotMock
@NonNullByDefault
public interface InsecureAAAModule {}
