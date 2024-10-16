/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
module org.opendaylight.netconf.transport.ssh {
    exports org.opendaylight.netconf.transport.ssh;

    provides org.opendaylight.yangtools.binding.meta.YangFeatureProvider with
        org.opendaylight.netconf.transport.ssh.impl.IetfSshClientFeatureProvider,
        org.opendaylight.netconf.transport.ssh.impl.IetfSshCommonFeatureProvider,
        org.opendaylight.netconf.transport.ssh.impl.IetfSshServerFeatureProvider;

    requires transitive org.opendaylight.netconf.keystore.api;
    requires transitive org.opendaylight.netconf.transport.api;
    requires transitive org.opendaylight.netconf.transport.tcp;
    requires transitive org.opendaylight.yang.gen.ietf.ssh.client.rfc9644;
    requires transitive org.opendaylight.yang.gen.ietf.ssh.server.rfc9644;
    requires com.google.common;
    requires io.netty.buffer;
    requires io.netty.transport;
    requires org.apache.commons.codec;
    requires org.bouncycastle.provider;
    requires org.opendaylight.netconf.shaded.sshd;
    requires org.opendaylight.yang.gen.iana.crypt.hash;
    requires org.opendaylight.yang.gen.iana.ssh._public.key.algs;
    requires org.opendaylight.yang.gen.iana.ssh.encryption.algs;
    requires org.opendaylight.yang.gen.iana.ssh.key.exchange.algs;
    requires org.opendaylight.yang.gen.iana.ssh.mac.algs;
    requires org.opendaylight.yang.gen.ietf.crypto.types.rfc9640;
    requires org.opendaylight.yang.gen.ietf.keystore.rfc9642;
    requires org.opendaylight.yang.gen.ietf.ssh.common.rfc9644;
    requires org.opendaylight.yang.gen.ietf.truststore.rfc9641;
    requires org.opendaylight.yangtools.binding.spec;
    requires org.opendaylight.yangtools.yang.common;
    requires org.slf4j;

    // Annotation-only dependencies
    requires static transitive org.eclipse.jdt.annotation;
    requires static com.github.spotbugs.annotations;
    requires static com.google.errorprone.annotations;
    requires static org.kohsuke.metainf_services;
    requires static org.osgi.annotation.bundle;
}
