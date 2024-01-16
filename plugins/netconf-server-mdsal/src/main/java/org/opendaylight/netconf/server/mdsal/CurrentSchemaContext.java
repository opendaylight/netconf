/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.mdsal;

import static com.google.common.base.Preconditions.checkState;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService.YangTextSourceExtension;
import org.opendaylight.netconf.server.api.monitoring.CapabilityListener;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

// Non-final for mocking
@SuppressWarnings("checkstyle:FinalClass")
public class CurrentSchemaContext implements AutoCloseable {
    private final AtomicReference<EffectiveModelContext> currentContext = new AtomicReference<>();
    private final Set<CapabilityListener> listeners = Collections.synchronizedSet(new HashSet<>());
    private final YangTextSourceExtension yangTextSourceExtension;

    private Registration schemaContextListenerListenerRegistration;

    private CurrentSchemaContext(final YangTextSourceExtension yangTextSourceExtension) {
        this.yangTextSourceExtension = yangTextSourceExtension;
    }

    // keep spotbugs from complaining about overridable method in constructor
    public static CurrentSchemaContext create(final DOMSchemaService schemaService,
            final YangTextSourceExtension yangTextSourceExtension) {
        var context = new CurrentSchemaContext(yangTextSourceExtension);
        final var registration = schemaService.registerSchemaContextListener(context::onModelContextUpdated);
        context.setRegistration(registration);
        return context;
    }

    private void setRegistration(final Registration registration) {
        schemaContextListenerListenerRegistration = registration;
    }

    public @NonNull EffectiveModelContext getCurrentContext() {
        final var ret = currentContext.get();
        checkState(ret != null, "Current context not received");
        return ret;
    }

    private void onModelContextUpdated(final EffectiveModelContext schemaContext) {
        currentContext.set(schemaContext);
        // FIXME is notifying all the listeners from this callback wise ?
        final var addedCaps = MdsalNetconfOperationServiceFactory.transformCapabilities(schemaContext,
            yangTextSourceExtension);
        for (var listener : listeners) {
            listener.onCapabilitiesChanged(addedCaps, Set.of());
        }
    }

    @Override
    public void close() {
        listeners.clear();
        schemaContextListenerListenerRegistration.close();
        currentContext.set(null);
    }

    public Registration registerCapabilityListener(final CapabilityListener listener) {
        listener.onCapabilitiesChanged(MdsalNetconfOperationServiceFactory.transformCapabilities(currentContext.get(),
            yangTextSourceExtension), Set.of());
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }
}
