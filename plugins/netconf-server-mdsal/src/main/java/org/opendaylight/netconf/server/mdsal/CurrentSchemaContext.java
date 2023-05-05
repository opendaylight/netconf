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
import org.opendaylight.netconf.server.api.monitoring.Capability;
import org.opendaylight.netconf.server.api.monitoring.CapabilityListener;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContextListener;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceProvider;

// Non-final for mocking
@SuppressWarnings("checkstyle:FinalClass")
public class CurrentSchemaContext implements EffectiveModelContextListener, AutoCloseable {
    private final AtomicReference<EffectiveModelContext> currentContext = new AtomicReference<>();
    private final Set<CapabilityListener> listeners1 = Collections.synchronizedSet(new HashSet<>());
    private final SchemaSourceProvider<YangTextSchemaSource> rootSchemaSourceProvider;

    private Registration schemaContextListenerListenerRegistration;

    private CurrentSchemaContext(final SchemaSourceProvider<YangTextSchemaSource> rootSchemaSourceProvider) {
        this.rootSchemaSourceProvider = rootSchemaSourceProvider;
    }

    // keep spotbugs from complaining about overridable method in constructor
    public static CurrentSchemaContext create(final DOMSchemaService schemaService,
                         final SchemaSourceProvider<YangTextSchemaSource> rootSchemaSourceProvider) {
        var context = new CurrentSchemaContext(rootSchemaSourceProvider);
        final Registration registration = schemaService.registerSchemaContextListener(context);
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

    @Override
    public void onModelContextUpdated(final EffectiveModelContext schemaContext) {
        currentContext.set(schemaContext);
        // FIXME is notifying all the listeners from this callback wise ?
        final Set<Capability> addedCaps = MdsalNetconfOperationServiceFactory.transformCapabilities(
                currentContext.get(), rootSchemaSourceProvider);
        for (final CapabilityListener listener : listeners1) {
            listener.onCapabilitiesChanged(addedCaps, Set.of());
        }
    }

    @Override
    public void close() {
        listeners1.clear();
        schemaContextListenerListenerRegistration.close();
        currentContext.set(null);
    }

    public Registration registerCapabilityListener(final CapabilityListener listener) {
        listener.onCapabilitiesChanged(MdsalNetconfOperationServiceFactory.transformCapabilities(currentContext.get(),
                rootSchemaSourceProvider), Set.of());
        listeners1.add(listener);
        return () -> listeners1.remove(listener);
    }
}
