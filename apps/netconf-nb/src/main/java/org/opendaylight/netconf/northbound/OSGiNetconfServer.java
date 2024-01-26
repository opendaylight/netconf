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

import io.netty.util.Timer;
import java.util.Map;
import org.opendaylight.netconf.server.NetconfServerSessionNegotiatorFactory;
import org.opendaylight.netconf.server.ServerTransportInitializer;
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

@Component(service = { OSGiNetconfServer.class }, configurationPid = "org.opendaylight.netconf.impl")
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
    private final ServerTransportInitializer serverTransportInitializer;

    @Activate
    public OSGiNetconfServer(
            @Reference(target = "(component.factory=" + DefaultNetconfMonitoringService.FACTORY_NAME + ")")
            final ComponentFactory<DefaultNetconfMonitoringService> monitoringFactory,
            @Reference(target = "(type=mapper-aggregator-registry)")
            final NetconfOperationServiceFactory mapperAggregatorRegistry,
            @Reference(target = "(type=global-timer)") final Timer timer,
            @Reference final SessionIdProvider sessionIdProvider,
            final Configuration configuration) {
        mappers.onAddNetconfOperationServiceFactory(mapperAggregatorRegistry);
        monitoring = monitoringFactory.newInstance(FrameworkUtil.asDictionary(DefaultNetconfMonitoringService.props(
            mapperAggregatorRegistry, configuration.monitoring$_$update$_$interval())));
        serverTransportInitializer = new ServerTransportInitializer(NetconfServerSessionNegotiatorFactory.builder()
            .setTimer(timer)
            .setAggregatedOpService(mappers)
            .setIdProvider(sessionIdProvider)
            .setConnectionTimeoutMillis(configuration.connection$_$timeout$_$millis())
            .setMonitoringService(monitoring.getInstance())
            .build());
    }

    @Deactivate
    public void deactivate() {
        monitoring.dispose();
        mappers.close();
    }

    ServerTransportInitializer serverTransportInitializer() {
        return serverTransportInitializer;
    }

    static <T> T extractProp(final Map<String, ?> properties, final String key, final Class<T> valueType) {
        return valueType.cast(verifyNotNull(properties.get(requireNonNull(key))));
    }
}
