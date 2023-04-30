/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.northbound;

import static com.google.common.base.Verify.verifyNotNull;
import static java.util.Objects.requireNonNull;

import io.netty.channel.EventLoopGroup;
import io.netty.util.Timer;
import java.util.Map;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.netconf.server.NetconfServerSessionNegotiatorFactory;
import org.opendaylight.netconf.server.ServerChannelInitializer;
import org.opendaylight.netconf.server.api.SessionIdProvider;
import org.opendaylight.netconf.server.api.operations.NetconfOperationServiceFactory;
import org.opendaylight.netconf.server.osgi.AggregatedNetconfOperationServiceFactory;
import org.osgi.framework.FrameworkUtil;
import org.osgi.service.component.ComponentFactory;
import org.osgi.service.component.ComponentInstance;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@Component(service = { }, configurationPid = "org.opendaylight.netconf.impl")
@Designate(ocd = OSGiNetconfServer.Configuration.class)
public final class OSGiNetconfServer {
    @ObjectClassDefinition
    public @interface Configuration {
        @AttributeDefinition(min = "0")
        long connection$_$timeout$_$millis() default 20000;
        @AttributeDefinition
        long monitoring$_$update$_$interval() default 6;
    }

    private final AggregatedNetconfOperationServiceFactory mappers = new AggregatedNetconfOperationServiceFactory();
    private final ComponentInstance<DefaultNetconfMonitoringService> monitoring;
    private final ComponentInstance<DefaultNetconfServerDispatcher> dispatcher;

    @Activate
    public OSGiNetconfServer(
            @Reference(target = "(component.factory=" + DefaultNetconfMonitoringService.FACTORY_NAME + ")")
            final ComponentFactory<DefaultNetconfMonitoringService> monitoringFactory,
            @Reference(target = "(component.factory=" + DefaultNetconfServerDispatcher.FACTORY_NAME + ")")
            final ComponentFactory<DefaultNetconfServerDispatcher> dispatcherFactory,
            @Reference(target = "(type=mapper-aggregator-registry)")
            final NetconfOperationServiceFactory mapperAggregatorRegistry,
            @Reference(target = "(type=global-netconf-ssh-scheduled-executor)")
            final ScheduledThreadPool sshScheduledExecutor,
            @Reference(target = "(type=global-boss-group)") final EventLoopGroup bossGroup,
            @Reference(target = "(type=global-boss-group)") final EventLoopGroup workerGroup,
            @Reference(target = "(type=global-timer)") final Timer timer,
            @Reference final SessionIdProvider sessionIdProvider,
            final Configuration configuration) {
        mappers.onAddNetconfOperationServiceFactory(mapperAggregatorRegistry);
        monitoring = monitoringFactory.newInstance(FrameworkUtil.asDictionary(DefaultNetconfMonitoringService.props(
            mapperAggregatorRegistry, sshScheduledExecutor, configuration.monitoring$_$update$_$interval())));
        dispatcher = dispatcherFactory.newInstance(FrameworkUtil.asDictionary(DefaultNetconfServerDispatcher.props(
            bossGroup, workerGroup, new ServerChannelInitializer(new NetconfServerSessionNegotiatorFactory(timer,
                mappers, sessionIdProvider, configuration.connection$_$timeout$_$millis(),
                monitoring.getInstance())))));
    }

    @Deactivate
    public void deactivate() {
        dispatcher.dispose();
        monitoring.dispose();
        mappers.close();
    }

    static <T> T extractProp(final Map<String, ?> properties, final String key, final Class<T> valueType) {
        return valueType.cast(verifyNotNull(properties.get(requireNonNull(key))));
    }
}
