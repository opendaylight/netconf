/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@Singleton
@Component(service = {}, configurationPid = "org.opendaylight.restconf.nb.rfc8040")
@Designate(ocd = RFC7950NorthBound.Configuration.class)
public final class RFC7950NorthBound implements AutoCloseable {
    private static final int DEFAULT_MAXIMUM_FRAGMENT_LENGTH = 0;
    private static final int DEFAULT_HEARTBEAT_INTERVAL = 10000;
    private static final int DEFAULT_IDLE_TIMEOUT = 30000;
    private static final String DEFAULT_HEARTBEAT_THREAD_PREFIX = "ping-executor";
    private static final int DEAFAULT_HEARTBEAT_THREAD_COUNT_MAX = 1;
    private static final boolean DEFAULT_USE_SSE = true;

    @ObjectClassDefinition
    public @interface Configuration {

        @AttributeDefinition(name = "maximum-fragment-length", min = "0",
            description = "Maximum size of a fragment in bytes")
        int maximumFragmentLength() default DEFAULT_MAXIMUM_FRAGMENT_LENGTH;

        @AttributeDefinition(name = "heartbeat-interval", min = "0", description = "Heartbeat interval in milliseconds")
        int heartbeatInterval() default DEFAULT_HEARTBEAT_INTERVAL;

        @AttributeDefinition(name = "idle-timeout", min = "0", description = "Idle timeout in milliseconds")
        int idleTimeout() default DEFAULT_IDLE_TIMEOUT;

        @AttributeDefinition(name = "ping-executor-name-prefix",
            description = "Prefix for naming threads issuing heartbeats")
        String heartbeatThreadPrefix() default DEFAULT_HEARTBEAT_THREAD_PREFIX;

        @AttributeDefinition(name = "max-thread-count", min = "1",
            description = "Maximum number of threads issuing heartbeats")
        int heartbeatThreadCountMaximum() default DEAFAULT_HEARTBEAT_THREAD_COUNT_MAX;

        @AttributeDefinition(name = "use-sse", description = "Use Server Sent Event instead of WebSockets")
        boolean useSSE() default DEFAULT_USE_SSE;
    }

    public RFC7950NorthBound(final DOMSchemaService schemaService, final DOMDataBroker dataBroker,
            final DOMMountPointService mountPointService,
            final int maximumFragmentLength, final int heartbeatInterval, final int idleTimeout,
            final String heartbeatThreadPrefix, final int heartbeatThreadCountMaximum, final boolean useSSE) {


//      <bean id="threadPoolFactory"
//            class="org.opendaylight.controller.config.threadpool.util.NamingThreadPoolFactory">
//        <argument value="${ping-executor-name-prefix}"/>
//      </bean>

//      <bean id="scheduledThreadPool"
//            class="org.opendaylight.controller.config.threadpool.util.ScheduledThreadPoolWrapper">
//        <argument value="${max-thread-count}"/>
//        <argument ref="threadPoolFactory"/>
//      </bean>

//      <bean id="configuration"
//            class="org.opendaylight.restconf.nb.rfc8040.streams.Configuration">
//        <argument value="${maximum-fragment-length}"/>
//        <argument value="${idle-timeout}"/>
//        <argument value="${heartbeat-interval}"/>
//        <argument value="${use-sse}" />
//      </bean>

        // TODO Auto-generated constructor stub
    }

    @Inject
    public RFC7950NorthBound(final DOMSchemaService schemaService, final DOMDataBroker dataBroker,
            final DOMMountPointService mountPointService) {
        this(schemaService, dataBroker, mountPointService,
            DEFAULT_MAXIMUM_FRAGMENT_LENGTH, DEFAULT_HEARTBEAT_INTERVAL, DEFAULT_IDLE_TIMEOUT,
            DEFAULT_HEARTBEAT_THREAD_PREFIX, DEAFAULT_HEARTBEAT_THREAD_COUNT_MAX, DEFAULT_USE_SSE);
    }

    @Activate
    public RFC7950NorthBound(
            @Reference final DOMSchemaService schemaService,
            @Reference final DOMDataBroker dataBroker,
            @Reference final DOMMountPointService mountPointService,
            final Configuration configuration) {
        this(schemaService, dataBroker, mountPointService,
            configuration.maximumFragmentLength(), configuration.heartbeatInterval(), configuration.idleTimeout(),
            configuration.heartbeatThreadPrefix(), configuration.heartbeatThreadCountMaximum(), configuration.useSSE());
    }

    @Override
    @Deactivate
    @PreDestroy
    public void close() {

    }
}
