/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import static java.util.Objects.requireNonNull;

import java.util.Map;
import org.opendaylight.restconf.nb.rfc8040.streams.DefaultPingExecutor;
import org.opendaylight.restconf.nb.rfc8040.streams.DefaultRestconfStreamServletFactory;
import org.opendaylight.restconf.nb.rfc8040.streams.StreamsConfiguration;
import org.opendaylight.restconf.server.mdsal.MdsalRestconfStreamRegistry;
import org.osgi.framework.FrameworkUtil;
import org.osgi.service.component.ComponentFactory;
import org.osgi.service.component.ComponentInstance;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Component managing global RESTCONF northbound configuration.
 */
@Component(service = { }, configurationPid = "org.opendaylight.restconf.nb.rfc8040")
@Designate(ocd = OSGiNorthbound.Configuration.class)
public final class OSGiNorthbound {
    @ObjectClassDefinition
    public @interface Configuration {
        @AttributeDefinition(min = "0", max = "" + StreamsConfiguration.MAXIMUM_FRAGMENT_LENGTH_LIMIT)
        int maximum$_$fragment$_$length() default 0;
        @AttributeDefinition(min = "0")
        int heartbeat$_$interval() default 10000;
        @AttributeDefinition(min = "1")
        int idle$_$timeout() default 30000;
        @AttributeDefinition(min = "1")
        String ping$_$executor$_$name$_$prefix() default DefaultPingExecutor.DEFAULT_NAME_PREFIX;
        // FIXME: this is a misnomer: it specifies the core pool size, i.e. minimum thread count, the maximum is set to
        //        Integer.MAX_VALUE, which is not what we want
        @AttributeDefinition(min = "0")
        int max$_$thread$_$count() default DefaultPingExecutor.DEFAULT_CORE_POOL_SIZE;
        @Deprecated(since = "7.0.0", forRemoval = true)
        @AttributeDefinition
        boolean use$_$sse() default true;
        @AttributeDefinition(name = "{+restconf}", description = """
            The value of RFC8040 {+restconf} URI template, poiting to the root resource. Must not end with '/'.""")
        String restconf() default "rests";
    }

    private static final Logger LOG = LoggerFactory.getLogger(OSGiNorthbound.class);

    private final ComponentFactory<MdsalRestconfStreamRegistry> registryFactory;
    private final ComponentFactory<DefaultRestconfStreamServletFactory> servletFactoryFactory;

    private ComponentInstance<MdsalRestconfStreamRegistry> registry;
    @Deprecated(since = "7.0.0", forRemoval = true)
    private boolean useSSE;

    private ComponentInstance<DefaultRestconfStreamServletFactory> servletFactory;
    private Map<String, ?> servletProps;

    @Activate
    public OSGiNorthbound(
            @Reference(target = "(component.factory=" + DefaultRestconfStreamServletFactory.FACTORY_NAME + ")")
            final ComponentFactory<DefaultRestconfStreamServletFactory> servletFactoryFactory,
            @Reference(target = "(component.factory=" + MdsalRestconfStreamRegistry.FACTORY_NAME + ")")
            final ComponentFactory<MdsalRestconfStreamRegistry> registryFactory, final Configuration configuration) {
        this.registryFactory = requireNonNull(registryFactory);
        this.servletFactoryFactory = requireNonNull(servletFactoryFactory);

        useSSE = configuration.use$_$sse();
        registry = registryFactory.newInstance(FrameworkUtil.asDictionary(MdsalRestconfStreamRegistry.props(useSSE)));

        servletProps = DefaultRestconfStreamServletFactory.props(configuration.restconf(), registry.getInstance(),
            useSSE,
            new StreamsConfiguration(configuration.maximum$_$fragment$_$length(),
                configuration.idle$_$timeout(), configuration.heartbeat$_$interval()),
            configuration.ping$_$executor$_$name$_$prefix(), configuration.max$_$thread$_$count());
        servletFactory = servletFactoryFactory.newInstance(FrameworkUtil.asDictionary(servletProps));

        LOG.info("Global RESTCONF northbound pools started");
    }

    @Modified
    void modified(final Configuration configuration) {
        final var newUseSSE = configuration.use$_$sse();
        if (newUseSSE != useSSE) {
            useSSE = newUseSSE;
            registry.dispose();
            registry = registryFactory.newInstance(FrameworkUtil.asDictionary(
                MdsalRestconfStreamRegistry.props(useSSE)));
            LOG.debug("ListenersBroker restarted with {}", newUseSSE ? "SSE" : "Websockets");
        }

        final var newServletProps = DefaultRestconfStreamServletFactory.props(configuration.restconf(),
            registry.getInstance(), useSSE,
            new StreamsConfiguration(configuration.maximum$_$fragment$_$length(),
                configuration.idle$_$timeout(), configuration.heartbeat$_$interval()),
            configuration.ping$_$executor$_$name$_$prefix(), configuration.max$_$thread$_$count());
        if (!newServletProps.equals(servletProps)) {
            servletProps = newServletProps;
            servletFactory.dispose();
            servletFactory = servletFactoryFactory.newInstance(FrameworkUtil.asDictionary(servletProps));
            LOG.debug("RestconfStreamServletFactory restarted with {}", servletProps);
        }

        LOG.debug("Applied {}", configuration);
    }

    @Deactivate
    void deactivate() {
        servletFactory.dispose();
        servletFactory = null;
        registry.dispose();
        registry = null;
        LOG.info("Global RESTCONF northbound pools stopped");
    }
}
