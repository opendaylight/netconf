/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.osgi;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.opendaylight.netconf.server.api.monitoring.Capability;
import org.opendaylight.netconf.server.api.monitoring.CapabilityListener;
import org.opendaylight.netconf.server.api.operations.NetconfOperation;
import org.opendaylight.netconf.server.api.operations.NetconfOperationService;
import org.opendaylight.netconf.server.api.operations.NetconfOperationServiceFactory;
import org.opendaylight.netconf.server.api.operations.NetconfOperationServiceFactoryListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.opendaylight.yangtools.concepts.AbstractRegistration;
import org.opendaylight.yangtools.concepts.Registration;

/**
 * NetconfOperationService aggregator. Makes a collection of operation services accessible as one.
 */
// Non-final for OSGi DS
// FIXME: split into abstract class for the multiple uses we have, which really is about which construct is being
//        invoked
public class AggregatedNetconfOperationServiceFactory
        implements NetconfOperationServiceFactory, NetconfOperationServiceFactoryListener, AutoCloseable {
    private final Set<NetconfOperationServiceFactory> factories = ConcurrentHashMap.newKeySet();
    private final Multimap<NetconfOperationServiceFactory, Registration> registrations =
            Multimaps.synchronizedMultimap(HashMultimap.create());
    private final Set<CapabilityListener> listeners = ConcurrentHashMap.newKeySet();

    public AggregatedNetconfOperationServiceFactory() {
    }

    public AggregatedNetconfOperationServiceFactory(final List<NetconfOperationServiceFactory> mappers) {
        mappers.forEach(this::onAddNetconfOperationServiceFactory);
    }

    @Override
    public final synchronized void onAddNetconfOperationServiceFactory(final NetconfOperationServiceFactory service) {
        factories.add(service);

        for (final CapabilityListener listener : listeners) {
            registrations.put(service, service.registerCapabilityListener(listener));
        }
    }

    @Override
    public final synchronized void onRemoveNetconfOperationServiceFactory(
            final NetconfOperationServiceFactory service) {
        factories.remove(service);
        registrations.removeAll(service).forEach(Registration::close);
    }

    @Override
    public final Set<Capability> getCapabilities() {
        final Set<Capability> capabilities = new HashSet<>();
        for (final NetconfOperationServiceFactory factory : factories) {
            capabilities.addAll(factory.getCapabilities());
        }
        return capabilities;
    }

    @Override
    public final synchronized Registration registerCapabilityListener(final CapabilityListener listener) {
        final Map<NetconfOperationServiceFactory, Registration> regs = new HashMap<>();

        for (final NetconfOperationServiceFactory factory : factories) {
            regs.put(factory, factory.registerCapabilityListener(listener));
        }
        listeners.add(listener);

        return new AbstractRegistration() {

            @Override
            protected void removeRegistration() {
                synchronized (AggregatedNetconfOperationServiceFactory.this) {
                    listeners.remove(listener);
                    regs.values().forEach(Registration::close);
                    for (var reg : regs.entrySet()) {
                        registrations.remove(reg.getKey(), reg.getValue());
                    }
                }
            }
        };
    }

    @Override
    public final synchronized NetconfOperationService createService(final SessionIdType sessionId) {
        return new AggregatedNetconfOperation(factories, sessionId);
    }

    @Override
    public synchronized void close() {
        factories.clear();
        registrations.values().forEach(Registration::close);
        registrations.clear();
        listeners.clear();
    }

    private static final class AggregatedNetconfOperation implements NetconfOperationService {
        private final ImmutableSet<NetconfOperationService> services;

        AggregatedNetconfOperation(final Set<NetconfOperationServiceFactory> factories, final SessionIdType sessionId) {
            services = factories.stream()
                .map(factory -> factory.createService(sessionId))
                .collect(ImmutableSet.toImmutableSet());
        }

        @Override
        public Set<NetconfOperation> getNetconfOperations() {
            return services.stream()
                .flatMap(service -> service.getNetconfOperations().stream())
                .collect(ImmutableSet.toImmutableSet());
        }

        @Override
        public void close() {
            services.forEach(NetconfOperationService::close);
        }
    }
}
