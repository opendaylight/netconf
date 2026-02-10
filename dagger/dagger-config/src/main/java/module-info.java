/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
/**
 * Configuration loader module for components that are not initialized or managed by the OSGi.
 */
module org.opendaylight.netconf.dagger.config {
    exports org.opendaylight.netconf.dagger.config;

    opens org.opendaylight.netconf.dagger.config to spring.core, spring.beans;

    requires spring.boot;
    requires spring.core;
    requires spring.beans;

    requires static transitive com.google.errorprone.annotations;

    requires static dagger;
    requires static org.eclipse.jdt.annotation;
    requires static jakarta.inject;
    requires static java.compiler;
}
