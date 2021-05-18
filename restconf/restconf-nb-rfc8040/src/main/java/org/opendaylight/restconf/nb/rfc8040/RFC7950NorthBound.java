/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import javax.inject.Inject;
import javax.inject.Singleton;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@Singleton
@Component(service = {}, configurationPid = "org.opendaylight.restconf.nb.rfc8040")
@Designate(ocd = RFC7950NorthBound.Configuration.class)
public final class RFC7950NorthBound {
    @ObjectClassDefinition
    public @interface Configuration {

//      <cm:property name="maximum-fragment-length" value="0"/>
        long maximumFragmentLength() default 0;
//      <cm:property name="heartbeat-interval" value="10000"/>
        int heartbeatInterval() default 10000;

//      <cm:property name="idle-timeout" value="30000"/>
//      <cm:property name="ping-executor-name-prefix" value="ping-executor"/>
//      <cm:property name="max-thread-count" value="1"/>
//      <cm:property name="use-sse" value="true"/>
    }

    @Inject
    public RFC7950NorthBound() {
        // FIXME: invoke with defaults
    }

    @Activate
    public RFC7950NorthBound(final Configuration configuration) {

    }

//    <bean id="threadPoolFactory"
//          class="org.opendaylight.controller.config.threadpool.util.NamingThreadPoolFactory">
//      <argument value="${ping-executor-name-prefix}"/>
//    </bean>
//
//    <bean id="scheduledThreadPool"
//          class="org.opendaylight.controller.config.threadpool.util.ScheduledThreadPoolWrapper">
//      <argument value="${max-thread-count}"/>
//      <argument ref="threadPoolFactory"/>
//    </bean>
//
//    <bean id="configuration"
//          class="org.opendaylight.restconf.nb.rfc8040.streams.Configuration">
//      <argument value="${maximum-fragment-length}"/>
//      <argument value="${idle-timeout}"/>
//      <argument value="${heartbeat-interval}"/>
//      <argument value="${use-sse}" />
//    </bean>

}
