/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
/**
 * A Netty-based RESTCONF server endpoint.
 */
module org.opendaylight.restconf.server.endpoint {
    exports org.opendaylight.restconf.server;

    requires transitive io.netty.codec.http;
    requires transitive org.opendaylight.netconf.transport.http;
    requires transitive org.opendaylight.restconf.api;
    requires transitive org.opendaylight.restconf.server.api;
    requires transitive org.opendaylight.restconf.server.spi;
    requires transitive org.opendaylight.yangtools.concepts;
    requires transitive org.opendaylight.yangtools.yang.common;

    requires io.netty.buffer;
    requires io.netty.codec;
    requires io.netty.codec.classes.quic;
    requires io.netty.codec.http2;
    requires io.netty.codec.http3;
    requires io.netty.common;
    requires io.netty.handler;
    requires io.netty.transport;
    requires org.opendaylight.aaa.repackaged.shiro;
    requires org.opendaylight.aaa.shiro.impl;
    requires org.opendaylight.netconf.transport.tcp;
    requires org.opendaylight.yangtools.binding.spec;
    requires org.slf4j;
    requires draft.ietf.restconf.server;

    // Annotation-only dependencies
    requires static transitive org.eclipse.jdt.annotation;
    requires static javax.inject;
    requires static org.osgi.annotation.bundle;
    requires static org.osgi.service.component.annotations;
    requires static org.kohsuke.metainf_services;
}
