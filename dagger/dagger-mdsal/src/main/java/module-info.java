/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
/**
 * Dagger module providing daggerized MD-SAL features.
 */
module org.opendaylight.netconf.dagger.mdsal {
    exports org.opendaylight.netconf.dagger.mdsal;

    requires org.opendaylight.mdsal.dom.api;
    requires org.opendaylight.mdsal.dom.broker;
    requires org.opendaylight.netconf.dagger.config;

    requires static transitive com.google.errorprone.annotations;

    requires transitive org.opendaylight.odlparent.dagger;
    requires spring.boot;
}
