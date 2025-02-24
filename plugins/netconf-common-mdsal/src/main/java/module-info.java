/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
/**
 * Common utilities to deal with <a href="https://datatracker.ietf.org/wg/netconf/">NETCONF</a> constructs and MD-SAL.
 */
module org.opendaylight.netconf.common.mdsal {
    exports org.opendaylight.netconf.common.mdsal;

    requires transitive java.xml;
    requires transitive org.opendaylight.mdsal.dom.api;
    requires transitive org.opendaylight.netconf.databind;
    requires transitive org.opendaylight.yangtools.yang.data.api;
    requires transitive org.opendaylight.yangtools.yang.data.impl;
    requires com.google.common;
    requires org.opendaylight.netconf.api;
    requires org.opendaylight.yangtools.yang.data.codec.xml;
    requires org.slf4j;

    // Annotation-only dependencies
    requires static transitive org.eclipse.jdt.annotation;
    requires static org.osgi.annotation.bundle;
}
