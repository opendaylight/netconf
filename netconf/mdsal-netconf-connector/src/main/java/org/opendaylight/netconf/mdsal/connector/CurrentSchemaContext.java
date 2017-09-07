/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.mdsal.connector;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.opendaylight.controller.config.util.capability.Capability;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.netconf.api.monitoring.CapabilityListener;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaContextListener;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceProvider;

public class CurrentSchemaContext implements SchemaContextListener, AutoCloseable {
    private final AtomicReference<SchemaContext> currentContext = new AtomicReference<>();
    private final ListenerRegistration<SchemaContextListener> schemaContextListenerListenerRegistration;
    private final Set<CapabilityListener> listeners1 = Collections.synchronizedSet(Sets.newHashSet());
    private final SchemaSourceProvider<YangTextSchemaSource> rootSchemaSourceProvider;

    public SchemaContext getCurrentContext() {
        Preconditions.checkState(currentContext.get() != null, "Current context not received");
        return currentContext.get();
    }

    public CurrentSchemaContext(final DOMSchemaService schemaService,
                                final SchemaSourceProvider<YangTextSchemaSource> rootSchemaSourceProvider) {
        this.rootSchemaSourceProvider = rootSchemaSourceProvider;
        schemaContextListenerListenerRegistration = schemaService.registerSchemaContextListener(this);
    }

    @Override
    public void onGlobalContextUpdated(final SchemaContext schemaContext) {
        currentContext.set(schemaContext);
        // FIXME is notifying all the listeners from this callback wise ?
        final Set<Capability> addedCaps = MdsalNetconfOperationServiceFactory.transformCapabilities(
                currentContext.get(), rootSchemaSourceProvider);
        for (final CapabilityListener listener : listeners1) {
            listener.onCapabilitiesChanged(addedCaps, Collections.emptySet());
        }
    }

    @Override
    public void close() throws Exception {
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