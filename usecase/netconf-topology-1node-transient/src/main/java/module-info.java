/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
/**
 * Dagger prototype for the RNC standalone transient applications, built from daggerized ODL components.
 */
module org.opendaylight.netconf.dagger {
    exports org.opendaylight.netconf.dagger.aaa;
    exports org.opendaylight.netconf.dagger.controller;
    exports org.opendaylight.netconf.dagger.mdsal;
    exports org.opendaylight.netconf.dagger.netconf;
    exports org.opendaylight.netconf.dagger.springboot.config;

    uses org.opendaylight.yangtools.binding.meta.YangModelBindingProvider;

    opens org.opendaylight.netconf.dagger.springboot.config to spring.core, spring.beans;

    requires transitive org.opendaylight.mdsal.binding.dom.adapter;
    requires transitive org.opendaylight.mdsal.dom.api;
    requires transitive org.opendaylight.mdsal.dom.broker;
    requires transitive org.opendaylight.mdsal.eos.binding.dom.adapter;
    requires transitive org.opendaylight.mdsal.singleton.api;
    requires transitive org.opendaylight.odlparent.dagger;
    requires transitive org.opendaylight.yangtools.binding.generator;
    requires transitive org.opendaylight.yangtools.binding.runtime.spi;
    requires transitive org.opendaylight.yangtools.odlext.parser.support;
    requires transitive org.opendaylight.yangtools.openconfig.parser.support;
    requires transitive org.opendaylight.yangtools.rfc6241.parser.support;
    requires transitive org.opendaylight.yangtools.rfc6536.parser.support;
    requires transitive org.opendaylight.yangtools.rfc6643.parser.support;
    requires transitive org.opendaylight.yangtools.rfc7952.parser.support;
    requires transitive org.opendaylight.yangtools.rfc8040.parser.support;
    requires transitive org.opendaylight.yangtools.rfc8528.parser.support;
    requires transitive org.opendaylight.yangtools.rfc8639.parser.support;
    requires transitive org.opendaylight.yangtools.rfc8819.parser.support;
    requires transitive org.opendaylight.yangtools.yang.model.api;
    requires transitive org.opendaylight.yangtools.yang.parser.api;
    requires transitive org.opendaylight.yangtools.yang.parser.rfc7950;
    requires transitive org.opendaylight.yangtools.yang.source.ir;
    requires transitive org.opendaylight.yangtools.yang.xpath.impl;
    requires transitive org.opendaylight.yangtools.yin.source.dom;
    requires transitive spring.boot;
    requires aaa.authn.api;
    requires aaa.cert;
    requires aaa.encrypt.service;
    requires aaa.password.service.api;
    requires aaa.password.service.impl;
    requires aaa.shiro;
    requires aaa.tokenauthrealm;
    requires java.validation;
    requires javax.servlet.api;
    requires mdsal.dom.inmemory.datastore;
    requires org.opendaylight.mdsal.eos.dom.api;
    requires org.opendaylight.mdsal.eos.dom.simple;
    requires org.opendaylight.mdsal.singleton.impl;
    requires org.opendaylight.netconf.transport.tcp;
    requires org.opendaylight.restconf.server.api;
    requires org.opendaylight.restconf.server.mdsal;
    requires org.opendaylight.restconf.server.spi;
    requires repackaged.shiro;
    requires restconf.server.jaxrs;
    requires sal.distributed.datastore;
    requires servlet.api;
    requires servlet.jersey2;
    requires spring.beans;
    requires spring.context;
    requires spring.core;
    requires web.api;
    requires restconf.server;
//  IDEA expect automatic name based on folder (requires transport.http), however Maven expect automatic module name
//  from MANIFEST file.
    requires org.opendaylight.netconf.transport.http;

    requires java.net.http;

    requires static transitive com.google.errorprone.annotations;
    requires static jakarta.inject;
    requires static java.compiler;
    requires static org.eclipse.jdt.annotation;
}
