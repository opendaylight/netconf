/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.test.tool;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Function;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Collections2;
import com.google.common.collect.Maps;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.opendaylight.netconf.api.capability.Capability;
import org.opendaylight.netconf.api.monitoring.NetconfManagementSession;
import org.opendaylight.netconf.api.monitoring.NetconfMonitoringService;
import org.opendaylight.netconf.api.monitoring.SessionEvent;
import org.opendaylight.netconf.api.monitoring.SessionListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.Yang;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Capabilities;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.CapabilitiesBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Schemas;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.SchemasBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Sessions;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.SessionsBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.schemas.Schema;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.schemas.Schema.Location;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.schemas.Schema.Location.Enumeration;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.schemas.SchemaBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.schemas.SchemaKey;

public class DummyMonitoringService implements NetconfMonitoringService {

    private static final Sessions EMPTY_SESSIONS = new SessionsBuilder().build();
    private static final Function<Capability, Uri> CAPABILITY_URI_FUNCTION =
        capability -> new Uri(capability.getCapabilityUri());

    private static final Function<Capability, Schema> CAPABILITY_SCHEMA_FUNCTION = capability -> new SchemaBuilder()
            .setIdentifier(capability.getModuleName().get())
            .setNamespace(new Uri(capability.getModuleNamespace().get()))
            .setFormat(Yang.class)
            .setVersion(capability.getRevision().orElse(""))
            .setLocation(Collections.singletonList(new Location(Enumeration.NETCONF)))
            .withKey(new SchemaKey(Yang.class, capability.getModuleName().get(),
                capability.getRevision().orElse("")))
            .build();

    private final Capabilities capabilities;
    private final ArrayListMultimap<String, Capability> capabilityMultiMap;
    private final Schemas schemas;

    public DummyMonitoringService(final Set<Capability> capabilities) {

        this.capabilities = new CapabilitiesBuilder().setCapability(
                new ArrayList<>(Collections2.transform(capabilities, CAPABILITY_URI_FUNCTION))).build();

        Set<Capability> moduleCapabilities = new HashSet<>();
        this.capabilityMultiMap = ArrayListMultimap.create();
        for (Capability cap : capabilities) {
            if (cap.getModuleName().isPresent()) {
                capabilityMultiMap.put(cap.getModuleName().get(), cap);
                moduleCapabilities.add(cap);
            }
        }

        this.schemas = new SchemasBuilder()
                .setSchema(Maps.uniqueIndex(Collections2.transform(moduleCapabilities, CAPABILITY_SCHEMA_FUNCTION),
                    Schema::key))
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
        final List<Capability> capabilityList = capabilityMultiMap.get(moduleName);
        if (revision.isPresent()) {
            for (Capability capability : capabilityList) {
                if (capability.getRevision().orElseThrow().equals(revision.get())) {
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
    public AutoCloseable registerCapabilitiesListener(final CapabilitiesListener listener) {
        return null;
    }

    @Override
    public AutoCloseable registerSessionsListener(final SessionsListener listener) {
        return null;
    }

}
