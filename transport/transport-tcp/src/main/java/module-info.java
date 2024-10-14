/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
/**
 * TCP transport layer. This does not constitute a {@code NETCONF Secure Transport}, but rather captures common aspects
 * of TCP-based secure transports, such as SSH and TLS. Configuration follows
 * <a href="https://www.rfc-editor.org/rfc/rfc9643">RFC9643</a>.
 *
 * <p>While this is strictly not a secure transport, it provides a TransportStack and thus can be used as is, for
 * example for testing purposes.
 *
 * <p>The three primary entry points into this package are
 * {@link org.opendaylight.netconf.transport.tcp.NettyTransportSupport},
 * {@link org.opendaylight.netconf.transport.tcp.TCPClient} and
 * {@link org.opendaylight.netconf.transport.tcp.TCPServer}.
 */
module org.opendaylight.netconf.transport.tcp {
    exports org.opendaylight.netconf.transport.tcp;

    provides org.opendaylight.yangtools.binding.meta.YangFeatureProvider with
        org.opendaylight.netconf.transport.tcp.impl.IetfTcpClientFeatureProvider,
        org.opendaylight.netconf.transport.tcp.impl.IetfTcpCommonFeatureProvider,
        org.opendaylight.netconf.transport.tcp.impl.IetfTcpServerFeatureProvider;

    requires transitive org.opendaylight.netconf.transport.api;
    requires transitive org.opendaylight.yang.gen.ietf.tcp.client.rfc9643;
    requires transitive org.opendaylight.yang.gen.ietf.tcp.server.rfc9643;
    requires com.google.common;
    requires io.netty.buffer;
    requires io.netty.transport;
    requires io.netty.transport.classes.epoll;
    requires jdk.net;
    requires org.opendaylight.yang.gen.ietf.inet.types.rfc6991;
    requires org.opendaylight.yang.gen.ietf.tcp.common.rfc9643;
    requires org.opendaylight.yangtools.binding.spec;
    requires org.opendaylight.yangtools.yang.common;
    requires org.slf4j;

    // Annotation-only dependencies
    requires static transitive org.eclipse.jdt.annotation;
    requires static org.kohsuke.metainf_services;
    requires static org.osgi.annotation.bundle;
}
