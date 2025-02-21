/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
/**
 * RESTCONF server API test library.
 */
module org.opendaylight.restconf.server.api.testlib {
    exports org.opendaylight.restconf.server.api.testlib;

    requires transitive org.opendaylight.restconf.server.api;
    requires transitive org.junit.jupiter.api;
    requires org.opendaylight.yang.gen.ietf.inet.types.rfc6991;
    requires org.opendaylight.yang.gen.ietf.restconf.monitoring.rfc8040;
    requires org.opendaylight.yang.gen.ietf.yang.types.rfc6991;
    requires org.opendaylight.yangtools.binding.spec;
    requires org.opendaylight.yangtools.yang.model.api;
    requires org.opendaylight.yangtools.yang.parser.api;
    requires org.opendaylight.yangtools.yang.data.spi;
    requires org.slf4j;

    // Annotation-only dependencies
    requires static transitive org.eclipse.jdt.annotation;
    requires org.opendaylight.yangtools.binding.runtime.spi;
}
