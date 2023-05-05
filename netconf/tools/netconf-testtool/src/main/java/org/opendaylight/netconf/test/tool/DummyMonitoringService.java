/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.test.tool;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableSet;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.opendaylight.netconf.server.api.monitoring.Capability;
import org.opendaylight.netconf.server.api.monitoring.NetconfManagementSession;
import org.opendaylight.netconf.server.api.monitoring.NetconfMonitoringService;
import org.opendaylight.netconf.server.api.monitoring.SessionEvent;
import org.opendaylight.netconf.server.api.monitoring.SessionListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.Yang;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Capabilities;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.CapabilitiesBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Schemas;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.SchemasBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Sessions;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.SessionsBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.schemas.Schema.Location;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.schemas.Schema.Location.Enumeration;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.schemas.SchemaBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.schemas.SchemaKey;
import org.opendaylight.yangtools.concepts.NoOpObjectRegistration;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.binding.util.BindingMap;

public class DummyMonitoringService implements NetconfMonitoringService {
    private static final Sessions EMPTY_SESSIONS = new SessionsBuilder().build();

    private final Capabilities capabilities;
    private final ArrayListMultimap<String, Capability> capabilityMultiMap;
    private final Schemas schemas;

    public DummyMonitoringService(final Set<Capability> capabilities) {
        this.capabilities = new CapabilitiesBuilder()
            .setCapability(capabilities.stream()
                .map(capability -> new Uri(capability.getCapabilityUri()))
                .collect(ImmutableSet.toImmutableSet()))
            .build();

        final var moduleCapabilities = new HashSet<Capability>();
        capabilityMultiMap = ArrayListMultimap.create();
        for (var cap : capabilities) {
            cap.getModuleName().ifPresent(moduleName -> {
                capabilityMultiMap.put(moduleName, cap);
                moduleCapabilities.add(cap);
            });
        }

        schemas = new SchemasBuilder()
            .setSchema(BindingMap.of(moduleCapabilities.stream()
                .map(capability -> new SchemaBuilder()
                    .setIdentifier(capability.getModuleName().orElseThrow())
                    .setNamespace(new Uri(capability.getModuleNamespace().orElseThrow()))
                    .setFormat(Yang.VALUE)
                    .setVersion(capability.getRevision().orElse(""))
                    .setLocation(Set.of(new Location(Enumeration.NETCONF)))
                    .withKey(new SchemaKey(Yang.VALUE, capability.getModuleName().orElseThrow(),
                        capability.getRevision().orElse("")))
                    .build())
                .collect(Collectors.toList())))
            .build();
    }

    @Override
    public Sessions getSessions() {
        return EMPTY_SESSIONS;
    }

    @Override
    public SessionListener getSessionListener() {
        return new SessionListener() {
            @Override
            public void onSessionUp(final NetconfManagementSession session) {
                //no op
            }

            @Override
            public void onSessionDown(final NetconfManagementSession session) {
                //no op
            }

            @Override
            public void onSessionEvent(final SessionEvent event) {
                //no op
            }
        };
    }

    @Override
    public Schemas getSchemas() {
        return schemas;
    }

    @Override
    public String getSchemaForCapability(final String moduleName, final Optional<String> revision) {
        final var capabilityList = capabilityMultiMap.get(moduleName);
        if (revision.isPresent()) {
            for (var capability : capabilityList) {
                if (capability.getRevision().orElseThrow().equals(revision.orElseThrow())) {
                    return capability.getCapabilitySchema().orElseThrow();
                }
            }
        } else {
            checkState(capabilityList.size() == 1,
                "Expected 1 capability for module %s, available revisions : %s", moduleName, capabilityList);
            //Only one revision is present, so return it
            return capabilityList.iterator().next().getCapabilitySchema().orElseThrow();
        }

        throw new IllegalArgumentException(
            "Module with name: " + moduleName + " and revision: " + revision + " does not exist");
    }

    @Override
    public Capabilities getCapabilities() {
        return capabilities;
    }

    @Override
    public Registration registerCapabilitiesListener(final CapabilitiesListener listener) {
        return NoOpObjectRegistration.of(listener);
    }

    @Override
    public Registration registerSessionsListener(final SessionsListener listener) {
        return NoOpObjectRegistration.of(listener);
    }
}
