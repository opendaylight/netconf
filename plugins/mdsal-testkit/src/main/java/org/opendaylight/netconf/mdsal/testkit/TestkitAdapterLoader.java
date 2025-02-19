/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.mdsal.testkit;

import static java.util.Objects.requireNonNull;

import java.util.List;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.binding.api.BindingService;
import org.opendaylight.mdsal.binding.dom.adapter.AdapterContext;
import org.opendaylight.mdsal.binding.dom.adapter.BindingDOMAdapterLoader;
import org.opendaylight.mdsal.dom.api.DOMService;

final class TestkitAdapterLoader extends BindingDOMAdapterLoader {
    private final List<DOMService<?, ?>> services;

    TestkitAdapterLoader(final AdapterContext codec, final List<DOMService<?, ?>> services) {
        super(codec);
        this.services = requireNonNull(services);
    }

    @Override
    protected @Nullable DOMService<?, ?> getDelegate(final Class<? extends DOMService<?, ?>> reqDeleg) {
        for (var service : services) {
            if (reqDeleg.isInstance(service)) {
                return service;
            }
        }
        return null;
    }

    <T extends BindingService> T getService(final Class<? extends T> bindingService) {
        return bindingService.cast(load(bindingService).orElseThrow());
    }
}
