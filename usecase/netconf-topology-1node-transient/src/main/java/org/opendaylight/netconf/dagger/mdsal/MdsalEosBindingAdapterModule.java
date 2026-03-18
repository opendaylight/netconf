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
import org.opendaylight.mdsal.binding.dom.adapter.AdapterContext;
import org.opendaylight.mdsal.eos.binding.api.EntityOwnershipService;
import org.opendaylight.mdsal.eos.binding.dom.adapter.DefaultEntityOwnershipService;
import org.opendaylight.mdsal.eos.dom.api.DOMEntityOwnershipService;

/**
 * A Dagger module providing {@code mdsal-eos-binding-adapter} services.
 */
@Module
@DoNotMock
@NonNullByDefault
public interface MdsalEosBindingAdapterModule {

    @Provides
    @Singleton
    static EntityOwnershipService entityOwnershipService(final DOMEntityOwnershipService domService,
            final AdapterContext adapterContext) {
        return new DefaultEntityOwnershipService(domService, adapterContext);
    }
}
