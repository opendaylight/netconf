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
import javax.inject.Singleton;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.ws.rs.core.Application;
import org.opendaylight.aaa.web.ServletDetails;
import org.opendaylight.aaa.web.WebContext;
import org.opendaylight.aaa.web.WebContextRegistration;
import org.opendaylight.aaa.web.WebServer;
import org.opendaylight.aaa.web.servlet.ServletSupport;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.netconf.sal.rest.impl.RestconfApplication;
import org.opendaylight.netconf.sal.restconf.api.RestConfConfig;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddressBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Standalone wiring for RESTCONF.
 *
 * <p>ACK: Some lines here were originally inspired by the RestConfWiring class in
 * opendaylight-simple which in turn was inspired by the CommunityRestConf class
 * from lighty.io. The differences include (1) that this class is "pure Java"
 * without depending on any binding framework utility classes; (2) we do not mix
 * bierman02 and rfc8040 for proper modularity.
 *
 * @author Michael Vorburger.ch (see ACK note for history)
 */
@Singleton
public class Bierman02RestConfWiring {

    private static final Logger LOG = LoggerFactory.getLogger(Bierman02RestConfWiring.class);

    private final WebServer webServer;
    private final WebContext webContext;
    private final RestconfProviderImpl webSocketServer;
    private WebContextRegistration webContextRegistration;

    @Inject
    public Bierman02RestConfWiring(RestConfConfig config, WebServer webServer, ServletSupport jaxRS,
            DOMSchemaService domSchemaService, DOMMountPointService domMountPointService, DOMRpcService domRpcService,
            DOMDataBroker domDataBroker, DOMNotificationService domNotificationService) {
        this.webServer = webServer;

        // WebSocket
        ControllerContext controllerContext = ControllerContext.newInstance(domSchemaService, domMountPointService,
                domSchemaService);
        BrokerFacade broker = BrokerFacade.newInstance(domRpcService, domDataBroker, domNotificationService,
                controllerContext);
        RestconfImpl restconf = RestconfImpl.newInstance(broker, controllerContext);
        StatisticsRestconfServiceWrapper stats = StatisticsRestconfServiceWrapper.newInstance(restconf);
        LOG.info("webSocketAddress = {}, webSocketPort = {}", config.webSocketAddress(), config.webSocketPort());
        IpAddress wsIpAddress = IpAddressBuilder.getDefaultInstance(config.webSocketAddress().getHostAddress());
        this.webSocketServer = new RestconfProviderImpl(stats, wsIpAddress, new PortNumber(config.webSocketPort()));
        Application application = new RestconfApplication(controllerContext, stats);

        // TODO remove this and use Bierman02WebRegistrarImpl instead
        HttpServlet servlet = jaxRS.createHttpServletBuilder(application).build();
        this.webContext = WebContext.builder().contextPath(config.contextPath())
                .addServlet(ServletDetails.builder().addUrlPattern("/*").servlet(servlet).build())
                .build();

        // TODO secure it, using web API
    }

    @PostConstruct
    public void start() throws ServletException {
        this.webContextRegistration = this.webServer.registerWebContext(webContext);
        this.webSocketServer.start();
    }

    @PreDestroy
    public void stop() {
        this.webSocketServer.close();
        if (webContextRegistration != null) {
            this.webContextRegistration.close();
        }
    }
}
