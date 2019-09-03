/*
 * Copyright (c) 2019 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.restconf.impl;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.servlet.ServletException;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.netconf.sal.rest.impl.RestconfApplication;
import org.opendaylight.netconf.sal.restconf.api.RestConfConfig;
import org.opendaylight.netconf.sal.restconf.web.WebInitializer;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddressBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Standalone wiring for RESTCONF.
 *
 * <p>ACK: Some lines here were originally inspired by the RestConfWiring class in
 * opendaylight-simple which in turn was inspired by the CommunityRestConf class
 * from lighty.io. The differences include (1) that this class is "pure Java"
 * without depending on any binding framework utility classes; (2) we do not mix
 * bierman02 and rfc8040 for proper modularity;  (3) we simply use {@literal @}Inject
 * instead of manual object wiring, where possible.
 *
 * @author Michael Vorburger.ch (see ACK note for history)
 */
@SuppressWarnings("deprecation")
// NOT @Singleton, to avoid  that the blueprint-maven-plugin generates <bean>, which we don't want for this
public class Bierman02RestConfWiring {

    private static final Logger LOG = LoggerFactory.getLogger(Bierman02RestConfWiring.class);

    private final RestconfProviderImpl webSocketServer;

    @Inject
    // The point of all the arguments here is simply to make your chosen Dependency Injection (DI) framework init. them
    public Bierman02RestConfWiring(final RestConfConfig config,
            final DOMSchemaService domSchemaService, final DOMMountPointService domMountPointService,
            final DOMRpcService domRpcService, final DOMDataBroker domDataBroker,
            final DOMNotificationService domNotificationService, final ControllerContext controllerContext,
            final RestconfApplication application, final BrokerFacade broker, final RestconfImpl restconf,
            final StatisticsRestconfServiceWrapper stats, final JSONRestconfServiceImpl jsonRestconfServiceImpl,
            final WebInitializer webInitializer) {

        // WebSocket
        LOG.info("webSocketAddress = {}, webSocketPort = {}", config.webSocketAddress(), config.webSocketPort());
        IpAddress wsIpAddress = IpAddressBuilder.getDefaultInstance(config.webSocketAddress().getHostAddress());
        this.webSocketServer = new RestconfProviderImpl(stats, wsIpAddress,
            new PortNumber(Uint16.valueOf(config.webSocketPort())));
    }

    @PostConstruct
    public void start() throws ServletException {
        this.webSocketServer.start();
    }

    @PreDestroy
    public void stop() {
        this.webSocketServer.close();
    }
}
