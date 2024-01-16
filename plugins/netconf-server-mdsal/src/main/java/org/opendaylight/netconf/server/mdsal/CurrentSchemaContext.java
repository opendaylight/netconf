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
import org.opendaylight.netconf.server.api.monitoring.CapabilityListener;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.source.YangTextSource;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceProvider;

// Non-final for mocking
@SuppressWarnings("checkstyle:FinalClass")
public class CurrentSchemaContext implements AutoCloseable {
    private final AtomicReference<EffectiveModelContext> currentContext = new AtomicReference<>();
    private final Set<CapabilityListener> listeners = Collections.synchronizedSet(new HashSet<>());
    private final SchemaSourceProvider<YangTextSource> rootSchemaSourceProvider;

    private Registration schemaContextListenerListenerRegistration;

    private CurrentSchemaContext(final SchemaSourceProvider<YangTextSource> rootSchemaSourceProvider) {
        this.rootSchemaSourceProvider = rootSchemaSourceProvider;
    }

    // keep spotbugs from complaining about overridable method in constructor
    public static CurrentSchemaContext create(final DOMSchemaService schemaService,
            final SchemaSourceProvider<YangTextSource> rootSchemaSourceProvider) {
        var context = new CurrentSchemaContext(rootSchemaSourceProvider);
        final Registration registration = schemaService.registerSchemaContextListener(context::onModelContextUpdated);
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
            rootSchemaSourceProvider);
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
                rootSchemaSourceProvider), Set.of());
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }
}
