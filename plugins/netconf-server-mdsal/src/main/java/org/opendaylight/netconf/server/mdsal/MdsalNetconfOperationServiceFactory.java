/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.mdsal;

import static java.util.Objects.requireNonNull;

import java.util.Set;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.netconf.api.CapabilityURN;
import org.opendaylight.netconf.server.api.monitoring.BasicCapability;
import org.opendaylight.netconf.server.api.monitoring.Capability;
import org.opendaylight.netconf.server.api.monitoring.CapabilityListener;
import org.opendaylight.netconf.server.api.operations.NetconfOperationService;
import org.opendaylight.netconf.server.api.operations.NetconfOperationServiceFactory;
import org.opendaylight.netconf.server.api.operations.NetconfOperationServiceFactoryListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.opendaylight.yangtools.concepts.Registration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

@Component(service = NetconfOperationServiceFactory.class, immediate = true, property = "type=mdsal-netconf-connector")
public final class MdsalNetconfOperationServiceFactory implements NetconfOperationServiceFactory, AutoCloseable {
    private static final BasicCapability VALIDATE_CAPABILITY = new BasicCapability(CapabilityURN.VALIDATE);

    private final DOMDataBroker dataBroker;
    private final DOMRpcService rpcService;

    private final CurrentSchemaContext currentSchemaContext;
    private final NetconfOperationServiceFactoryListener netconfOperationServiceFactoryListener;

    @Activate
    public MdsalNetconfOperationServiceFactory(@Reference final DOMSchemaService schemaService,
            @Reference final DOMDataBroker dataBroker, @Reference final DOMRpcService rpcService,
            @Reference(target = "(type=mapper-aggregator-registry)")
            final NetconfOperationServiceFactoryListener netconfOperationServiceFactoryListener) {
        this.dataBroker = requireNonNull(dataBroker);
        this.rpcService = requireNonNull(rpcService);
        this.netconfOperationServiceFactoryListener = requireNonNull(netconfOperationServiceFactoryListener);

        currentSchemaContext = new CurrentSchemaContext(schemaService);
        netconfOperationServiceFactoryListener.onAddNetconfOperationServiceFactory(this);
    }

    @Deactivate
    @Override
    public void close() {
        netconfOperationServiceFactoryListener.onRemoveNetconfOperationServiceFactory(this);
        currentSchemaContext.close();
    }

    @Override
    public NetconfOperationService createService(final SessionIdType sessionId) {
        return new MdsalNetconfOperationService(currentSchemaContext, sessionId, dataBroker, rpcService);
    }

    @Override
    public Set<Capability> getCapabilities() {
        return currentSchemaContext.currentStrategy().capabilities();
    }

    @Override
    public Registration registerCapabilityListener(final CapabilityListener listener) {
        // Advertise validate capability only if DOMDataBroker provides DOMDataTransactionValidator
        if (dataBroker.extension(DOMDataTransactionValidator.class) != null) {
            // FIXME: support VALIDATE_1_1 as well!
            listener.onCapabilitiesChanged(Set.of(VALIDATE_CAPABILITY), Set.of());
        }
        // Advertise namespaces of supported YANG models as NETCONF capabilities
        return currentSchemaContext.registerCapabilityListener(listener);
    }
}
