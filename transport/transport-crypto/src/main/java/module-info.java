/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
module org.opendaylight.netconf.transport.crypto {
    exports org.opendaylight.netconf.transport.crypto;

    requires transitive org.opendaylight.netconf.transport.api;
    requires transitive org.opendaylight.yang.gen.ietf.crypto.types.rfc9640;
    requires transitive org.bouncycastle.provider;

    requires com.google.common;
//    requires transitive io.netty.buffer;
//    requires transitive io.netty.common;
//    requires transitive io.netty.transport;
//    requires transitive org.opendaylight.yangtools.yang.common;
//    requires io.netty.transport.classes.epoll;
//    requires jdk.net;
    requires org.slf4j;

    // Annotation-only dependencies
    requires static transitive org.eclipse.jdt.annotation;
    requires static transitive javax.inject;
    requires static org.osgi.annotation.bundle;
    requires static org.osgi.service.component.annotations;
}
