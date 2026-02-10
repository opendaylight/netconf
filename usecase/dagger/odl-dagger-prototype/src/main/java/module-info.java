/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
/**
 * Dagger prototype with modules providing daggerized ODL components.
 */
module org.opendaylight.netconf.dagger {
    exports org.opendaylight.netconf.dagger.springboot.config;

    opens org.opendaylight.netconf.dagger.springboot.config to spring.core, spring.beans;

    requires spring.beans;
    requires spring.boot;
    requires spring.core;

    requires static transitive com.google.errorprone.annotations;
    requires static dagger;
    requires static jakarta.inject;
    requires static java.compiler;
    requires static org.eclipse.jdt.annotation;
}
