/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
module org.opendaylight.netconf.api {
    exports org.opendaylight.netconf.api;
    exports org.opendaylight.netconf.api.messages;
    exports org.opendaylight.netconf.api.xml;

    requires transitive io.netty.transport;
    requires transitive java.xml;
    requires transitive org.opendaylight.yangtools.yang.common;
    requires transitive org.opendaylight.yang.gen.ietf.netconf.rfc6241;
    requires com.google.common;
    requires org.opendaylight.yangtools.util;
    requires org.opendaylight.yangtools.yang.binding;
    requires org.slf4j;

    // Annotation-only dependencies
    requires static transitive org.eclipse.jdt.annotation;
    requires static com.github.spotbugs.annotations;
}
