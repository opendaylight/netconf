/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
/**
 * Dagger prototype for the RNC Pekko and RNC standalone applications, built from daggerized ODL components.
 */
module org.opendaylight.netconf.dagger {
    exports org.opendaylight.netconf.dagger.springboot.config;
    exports org.opendaylight.netconf.dagger.mdsal;
    exports org.opendaylight.netconf.dagger.controller;

    uses org.opendaylight.yangtools.binding.meta.YangModelBindingProvider;

    opens org.opendaylight.netconf.dagger.springboot.config to spring.core, spring.beans;

    requires transitive org.opendaylight.odlparent.dagger;
    requires aaa.encrypt.service;
    requires aaa.filterchain;
    requires javax.servlet.api;
    requires mdsal.dom.inmemory.datastore;
    requires sal.distributed.datastore;
    requires spring.beans;
    requires spring.boot;
    requires spring.context;
    requires spring.core;
    requires org.opendaylight.mdsal.binding.dom.adapter;
    requires org.opendaylight.mdsal.dom.api;
    requires org.opendaylight.mdsal.dom.broker;
    requires org.opendaylight.mdsal.eos.binding.dom.adapter;
    requires org.opendaylight.mdsal.eos.dom.api;
    requires org.opendaylight.mdsal.eos.dom.simple;
    requires org.opendaylight.mdsal.singleton.api;
    requires org.opendaylight.mdsal.singleton.impl;
    requires org.opendaylight.yangtools.binding.generator;
    requires org.opendaylight.yangtools.binding.runtime.spi;
    requires org.opendaylight.yangtools.odlext.parser.support;
    requires org.opendaylight.yangtools.openconfig.parser.support;
    requires org.opendaylight.yangtools.rfc6241.parser.support;
    requires org.opendaylight.yangtools.rfc6536.parser.support;
    requires org.opendaylight.yangtools.rfc6643.parser.support;
    requires org.opendaylight.yangtools.rfc7952.parser.support;
    requires org.opendaylight.yangtools.rfc8040.parser.support;
    requires org.opendaylight.yangtools.rfc8528.parser.support;
    requires org.opendaylight.yangtools.rfc8639.parser.support;
    requires org.opendaylight.yangtools.rfc8819.parser.support;
    requires org.opendaylight.yangtools.yang.parser.api;
    requires org.opendaylight.yangtools.yang.parser.rfc7950;
    requires org.opendaylight.yangtools.yang.xpath.impl;
    requires servlet.api;
    requires servlet.jersey2;
    requires web.api;
    requires web.jetty.impl;

    requires static transitive com.google.errorprone.annotations;
    requires static dagger;
    requires static jakarta.inject;
    requires static java.compiler;
    requires static org.eclipse.jdt.annotation;
}
