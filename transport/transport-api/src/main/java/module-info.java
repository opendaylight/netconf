/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
module org.opendaylight.netconf.transport.api {
    exports org.opendaylight.netconf.transport.api;
    exports org.opendaylight.netconf.transport.spi;

    requires transitive com.google.common;
    requires transitive io.netty.buffer;
    requires transitive io.netty.common;
    requires transitive io.netty.transport;
    requires transitive org.opendaylight.yangtools.yang.common;
    requires io.netty.transport.classes.epoll;
    requires jdk.net;
    requires org.slf4j;

    // Annotation-only dependencies
    requires static transitive org.eclipse.jdt.annotation;
    requires static org.osgi.annotation.bundle;
}
