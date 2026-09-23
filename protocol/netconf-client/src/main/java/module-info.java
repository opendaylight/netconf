/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
module org.opendaylight.netconf.client {
    exports org.opendaylight.netconf.client;

    requires transitive org.opendaylight.netconf.api;
    requires transitive org.opendaylight.netconf.common;
    requires transitive org.opendaylight.netconf.transport.ssh;
    requires transitive org.opendaylight.netconf.transport.tls;
    requires transitive org.opendaylight.yang.gen.ietf.inet.types.rfc6991;
    requires transitive org.opendaylight.yang.gen.ietf.ssh.client.rfc9644;

    requires org.bouncycastle.pkix;
    requires org.bouncycastle.provider;
    requires org.opendaylight.netconf.shaded.exificient;
    requires org.opendaylight.netconf.codec;
    requires org.opendaylight.yangtools.binding.spec;
    requires org.slf4j;

    // Annotation-only dependencies
    requires static transitive org.eclipse.jdt.annotation;
    requires static com.github.spotbugs.annotations;
    requires static com.google.errorprone.annotations;
    requires static org.osgi.annotation.bundle;
    requires static org.osgi.service.component.annotations;
}
