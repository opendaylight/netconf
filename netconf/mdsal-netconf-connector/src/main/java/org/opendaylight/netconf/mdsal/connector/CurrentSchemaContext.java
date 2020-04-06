/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.mdsal.connector;

import static com.google.common.base.Preconditions.checkState;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.netconf.api.capability.Capability;
import org.opendaylight.netconf.api.monitoring.CapabilityListener;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContextListener;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceProvider;

public class CurrentSchemaContext implements EffectiveModelContextListener, AutoCloseable {
    private final AtomicReference<EffectiveModelContext> currentContext = new AtomicReference<>();
    private final ListenerRegistration<?> schemaContextListenerListenerRegistration;
    private final Set<CapabilityListener> listeners1 = Collections.synchronizedSet(new HashSet<>());
    private final SchemaSourceProvider<YangTextSchemaSource> rootSchemaSourceProvider;

    public EffectiveModelContext getCurrentContext() {
        checkState(currentContext.get() != null, "Current context not received");
        return currentContext.get();
    }

    public CurrentSchemaContext(final DOMSchemaService schemaService,
                                final SchemaSourceProvider<YangTextSchemaSource> rootSchemaSourceProvider) {
        this.rootSchemaSourceProvider = rootSchemaSourceProvider;
        schemaContextListenerListenerRegistration = schemaService.registerSchemaContextListener(this);
    }

    @Override
    public void onModelContextUpdated(final EffectiveModelContext schemaContext) {
        currentContext.set(schemaContext);
        // FIXME is notifying all the listeners from this callback wise ?
        final Set<Capability> addedCaps = MdsalNetconfOperationServiceFactory.transformCapabilities(
                currentContext.get(), rootSchemaSourceProvider);
        for (final CapabilityListener listener : listeners1) {
            listener.onCapabilitiesChanged(addedCaps, Collections.emptySet());
        }
    }

    @Override
    public void close() {
        listeners1.clear();
        schemaContextListenerListenerRegistration.close();
        currentContext.set(null);
    }

    public AutoCloseable registerCapabilityListener(final CapabilityListener listener) {
        listener.onCapabilitiesChanged(MdsalNetconfOperationServiceFactory.transformCapabilities(currentContext.get(),
                rootSchemaSourceProvider), Collections.emptySet());
        listeners1.add(listener);
        return () -> listeners1.remove(listener);
    }
}
