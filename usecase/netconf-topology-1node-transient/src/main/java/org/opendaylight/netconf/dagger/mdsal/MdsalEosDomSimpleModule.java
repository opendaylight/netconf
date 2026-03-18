/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.dagger.mdsal;

import com.google.errorprone.annotations.DoNotMock;
import dagger.Module;
import dagger.Provides;
import jakarta.inject.Singleton;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.mdsal.eos.dom.api.DOMEntityOwnershipService;
import org.opendaylight.mdsal.eos.dom.simple.di.LocalDOMEntityOwnershipService;

/**
 * A Dagger module providing {@code mdsal-singleton-impl} services.
 */
@Module
@DoNotMock
@NonNullByDefault
public interface MdsalEosDomSimpleModule {

    @Provides
    @Singleton
    static DOMEntityOwnershipService domEntityOwnershipService() {
        return new LocalDOMEntityOwnershipService();
    }
}
