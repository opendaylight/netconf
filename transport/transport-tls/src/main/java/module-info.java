/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
module org.opendaylight.netconf.transport.tls {
    exports org.opendaylight.netconf.transport.tls;

    provides org.opendaylight.yangtools.binding.meta.YangFeatureProvider with
        org.opendaylight.netconf.transport.tls.impl.IetfTlsClientFeatureProvider,
        org.opendaylight.netconf.transport.tls.impl.IetfTlsCommonFeatureProvider,
        org.opendaylight.netconf.transport.tls.impl.IetfTlsServerFeatureProvider;

    requires transitive io.netty.handler;
    requires transitive org.opendaylight.netconf.transport.api;
    requires transitive org.opendaylight.yang.gen.ietf.tcp.client.rfc9643;
    requires transitive org.opendaylight.yang.gen.ietf.tcp.server.rfc9643;
    requires transitive org.opendaylight.yang.gen.ietf.tls.client.rfc9645;
    requires transitive org.opendaylight.yang.gen.ietf.tls.server.rfc9645;
    requires com.google.common;
    requires io.netty.buffer;
    requires io.netty.transport;
    requires org.bouncycastle.provider;
    requires org.opendaylight.netconf.transport.tcp;
    requires org.opendaylight.yang.gen.iana.tls.cipher.suite.algs;
    requires org.opendaylight.yang.gen.ietf.crypto.types.rfc9640;
    requires org.opendaylight.yang.gen.ietf.keystore.rfc9642;
    requires org.opendaylight.yang.gen.ietf.tls.common.rfc9645;
    requires org.opendaylight.yang.gen.ietf.truststore.rfc9641;
    requires org.opendaylight.yangtools.binding.spec;
    requires org.slf4j;

    // Testing only
    requires static org.bouncycastle.pkix;

    // Annotation-only dependencies
    requires static transitive org.eclipse.jdt.annotation;
    requires static org.kohsuke.metainf_services;
    requires static org.osgi.annotation.bundle;
}
