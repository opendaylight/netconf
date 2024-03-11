/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.impl;

import io.netty.util.AsciiString;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.netconf.transport.http.AuthHandlerFactory;
import org.opendaylight.netconf.transport.http.ConfigUtils;
import org.opendaylight.restconf.api.MediaTypes;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.HttpServerStackGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.http.server.stack.grouping.Transport;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@Component(immediate = true,  configurationPid = "org.opendaylight.restconf.server")
@Designate(ocd = RestconfServerNorthbound.Configuration.class)
public final class RestconfServerNorthbound implements AutoCloseable {

    @ObjectClassDefinition
    public @interface Configuration {
        @AttributeDefinition
        String bindingAddress() default "0.0.0.0";

        @AttributeDefinition(min = "1", max = "65535")
        int portNumber() default 8182;

        @AttributeDefinition
        String topLevelResource() default "rests";

        @AttributeDefinition
        String defaultAcceptType() default MediaTypes.APPLICATION_YANG_DATA_JSON;

        @AttributeDefinition
        boolean defaultPrettyPrint() default false;
    }

    private static final Logger LOG = LoggerFactory.getLogger(RestconfServerNorthbound.class);
    private final NettyRestconfServer server;

    @Activate
    @Inject
    public RestconfServerNorthbound(@Reference final RestconfServer service,
            @Reference final AuthHandlerFactory authHandlerFactory, final Configuration configuration) {
        final var listenAddress = configuration.bindingAddress();
        final var listenPort = configuration.portNumber();

        final var listenConfig = new HttpServerStackGrouping() {
            @Override
            public Class<? extends HttpServerStackGrouping> implementedInterface() {
                return HttpServerStackGrouping.class;
            }

            @Override
            public Transport getTransport() {
                return ConfigUtils.serverTransportTcp(listenAddress, listenPort);
            }
        };

        final var dispatcher = new RestconfRequestDispatcher(service, configuration.topLevelResource(),
            AsciiString.of(configuration.defaultAcceptType()),
            PrettyPrintParam.of(configuration.defaultPrettyPrint()));
        server = new NettyRestconfServer(authHandlerFactory, dispatcher, listenConfig);
        LOG.info("RESTCONF server started at {}:{}", listenAddress, listenPort);
    }

    @Deactivate
    @Override
    public void close() throws Exception {
        server.shutdown();
    }
}
