/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
/**
 * Definition of what constitutes a NETCONF server when looking at the concept via IETF's netconf Working Group, where
 * "NETCONF" is a protocol defined over SSH+XML or TLS+XML and "RESTCONF" is an equivalent protocol defined over HTTP.
 */
module org.opendaylight.netconf.wg.server.api {
    exports org.opendaylight.netconf.wg.server.api;

    // Annotation-only dependencies
    requires static transitive org.eclipse.jdt.annotation;
    requires static org.osgi.annotation.bundle;
}
