/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client;

import static java.util.Objects.requireNonNull;

import io.netty.channel.EventLoopGroup;
import io.netty.util.Timer;
import io.netty.util.concurrent.Future;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.netconf.client.conf.NetconfClientConfiguration;
import org.opendaylight.netconf.nettyutil.AbstractNetconfDispatcher;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@Component(immediate = true, service = NetconfClientDispatcher.class, property = "type=netconf-client-dispatcher")
public class NetconfClientDispatcherImpl
        extends AbstractNetconfDispatcher<NetconfClientSession, NetconfClientSessionListener>
        implements NetconfClientDispatcher {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfClientDispatcherImpl.class);

    private final Timer timer;

    @Inject
    @Activate
    public NetconfClientDispatcherImpl(@Reference(target = "(type=global-boss-group)") final EventLoopGroup bossGroup,
            @Reference(target = "(type=global-worker-group)") final EventLoopGroup workerGroup,
            @Reference(target = "(type=global-timer)") final Timer timer) {
        super(bossGroup, workerGroup);
        this.timer = requireNonNull(timer);
    }

    protected final Timer getTimer() {
        return timer;
    }

    @Override
    public Future<NetconfClientSession> createClient(final NetconfClientConfiguration clientConfiguration) {
        return switch (clientConfiguration.getProtocol()) {
            case TCP -> createTcpClient(clientConfiguration);
            case SSH -> createSshClient(clientConfiguration);
            case TLS -> createTlsClient(clientConfiguration);
        };
    }

    private Future<NetconfClientSession> createTcpClient(final NetconfClientConfiguration currentConfiguration) {
        LOG.debug("Creating TCP client with configuration: {}", currentConfiguration);
        return super.createClient(currentConfiguration.getAddress(),
            (ch, promise) -> new TcpClientChannelInitializer(getNegotiatorFactory(currentConfiguration),
                        currentConfiguration.getSessionListener()).initialize(ch, promise));
    }

    private Future<NetconfClientSession> createSshClient(final NetconfClientConfiguration currentConfiguration) {
        LOG.debug("Creating SSH client with configuration: {}", currentConfiguration);
        return super.createClient(currentConfiguration.getAddress(),
            (ch, sessionPromise) -> new SshClientChannelInitializer(currentConfiguration.getAuthHandler(),
                        getNegotiatorFactory(currentConfiguration), currentConfiguration.getSessionListener(),
                        currentConfiguration.getSshClient(), currentConfiguration.getName())
                    .initialize(ch, sessionPromise));
    }

    private Future<NetconfClientSession> createTlsClient(final NetconfClientConfiguration currentConfiguration) {
        LOG.debug("Creating TLS client with configuration: {}", currentConfiguration);
        return super.createClient(currentConfiguration.getAddress(),
            (ch, sessionPromise) -> new TlsClientChannelInitializer(currentConfiguration.getSslHandlerFactory(),
                    getNegotiatorFactory(currentConfiguration), currentConfiguration.getSessionListener())
                    .initialize(ch, sessionPromise));
    }

    protected NetconfClientSessionNegotiatorFactory getNegotiatorFactory(final NetconfClientConfiguration cfg) {
        final List<Uri> odlHelloCapabilities = cfg.getOdlHelloCapabilities();
        if (odlHelloCapabilities == null || odlHelloCapabilities.isEmpty()) {
            return new NetconfClientSessionNegotiatorFactory(timer, cfg.getAdditionalHeader(),
                    cfg.getConnectionTimeoutMillis(), cfg.getMaximumIncomingChunkSize());
        }

        // LinkedHashSet since perhaps the device cares about order of hello message capabilities.
        // This allows user control of the order while complying with the existing interface.
        final Set<String> stringCapabilities = new LinkedHashSet<>();
        for (final Uri uri : odlHelloCapabilities) {
            stringCapabilities.add(uri.getValue());
        }
        return new NetconfClientSessionNegotiatorFactory(timer, cfg.getAdditionalHeader(),
            cfg.getConnectionTimeoutMillis(), stringCapabilities);
    }
}
