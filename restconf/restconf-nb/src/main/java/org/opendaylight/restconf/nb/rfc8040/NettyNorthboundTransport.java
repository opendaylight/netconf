/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import com.google.common.annotations.Beta;
import java.util.concurrent.ExecutionException;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.netconf.transport.http.ConfigUtils;
import org.opendaylight.netconf.transport.http.HTTPServer;
import org.opendaylight.netconf.transport.tcp.BootstrapFactory;
import org.opendaylight.restconf.nb.netty.NettyRestconf;
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

@Beta
@Component(service = {}, configurationPid = "org.opendaylight.restconf.nb.rfc8040.netty")
@Designate(ocd = NettyNorthboundTransport.Configuration.class)
public final class NettyNorthboundTransport implements AutoCloseable {
    @ObjectClassDefinition
    public @interface Configuration {
        @AttributeDefinition
        String bindingAddress() default "0.0.0.0";

        @AttributeDefinition(min = "1", max = "65535")
        int portNumber() default 8182;
    }

    private static final Logger LOG = LoggerFactory.getLogger(NettyNorthboundTransport.class);

    private final HTTPServer tcpServer;

    @Activate
    public NettyNorthboundTransport(@Reference RestconfServer restconfServer,
            @Reference final OSGiRestconfServer backend, final Configuration configuration) {
        final var localAddr = configuration.bindingAddress();
        final var localPort = configuration.portNumber();
        final var factory = new BootstrapFactory("odl-restconf-nb", 0);

        final var serverParameters = new HttpServerStackGrouping() {
            @Override
            public Class<? extends HttpServerStackGrouping> implementedInterface() {
                return HttpServerStackGrouping.class;
            }

            @Override
            public Transport getTransport() {
                return ConfigUtils.serverTransportTcp(configuration.bindingAddress(), configuration.portNumber());
            }
        };

        final var dispatcher = new NettyRestconf(restconfServer);

        try {
            tcpServer = HTTPServer.listen(backend.initializer(), factory.newServerBootstrap(),
                serverParameters, dispatcher).get();
        } catch (UnsupportedConfigurationException | ExecutionException | InterruptedException e) {
            LOG.warn("Could not start TCP RESTCONF server at {}:{}", localAddr, localPort, e);
            throw new IllegalStateException("Could not start TCP RESTCONF server", e);
        }

        LOG.info("TCP RESTCONF server at {}:{} started", localAddr, localPort);
    }

    @Deactivate
    @Override
    public void close() throws Exception {
        tcpServer.shutdown();
    }
}
