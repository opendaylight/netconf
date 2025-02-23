/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
module org.opendaylight.netconf.common {
    exports org.opendaylight.netconf.common;
    exports org.opendaylight.netconf.common.impl;
    exports org.opendaylight.netconf.common.util;

    requires transitive io.netty.common;
    requires transitive org.opendaylight.yangtools.yang.common;
    requires transitive org.opendaylight.yangtools.yang.data.codec.gson;
    requires transitive org.opendaylight.yangtools.yang.data.codec.xml;
    requires transitive org.opendaylight.yangtools.yang.data.util;
    requires transitive org.opendaylight.yangtools.yang.model.api;
    requires transitive org.opendaylight.netconf.api;
    requires com.google.common;
    requires org.slf4j;

    // Annotation-only dependencies
    requires static transitive java.annotation;
    requires static transitive javax.inject;
    requires static transitive org.eclipse.jdt.annotation;
    requires static com.github.spotbugs.annotations;
    requires static org.osgi.service.component.annotations;
    requires static org.osgi.service.metatype.annotations;
}
