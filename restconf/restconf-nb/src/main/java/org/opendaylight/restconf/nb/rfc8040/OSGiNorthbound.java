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
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.nb.rfc8040.streams.DefaultPingExecutor;
import org.opendaylight.restconf.nb.rfc8040.streams.DefaultRestconfStreamServletFactory;
import org.opendaylight.restconf.nb.rfc8040.streams.StreamsConfiguration;
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

        @AttributeDefinition(name = "{+restconf}", description = """
            The value of RFC8040 {+restconf} URI template, pointing to the root resource. Must not end with '/'.""")
        String restconf() default "rests";

        @AttributeDefinition(
            name = "default pretty-print",
            description = "Control the default value of the '" + PrettyPrintParam.uriName + "' query parameter.")
        boolean pretty$_$print() default false;

        @AttributeDefinition(
            name = "Report 404 on data-missing",
            description = """
                Control the HTTP status code reporting of conditions corresponding to "data-missing". When this is set
                to true, the server will violate RFC8040 and report "404" instead of "409".

                For details and reasoning see https://www.rfc-editor.org/errata/eid5565 and
                https://mailarchive.ietf.org/arch/browse/netconf/?gbt=1&index=XcF9r3ek3LvZ4DjF-7_B8kxuiwA""")
        boolean data$_$missing$_$is$_$404() default false;
    }

    private static final Logger LOG = LoggerFactory.getLogger(OSGiNorthbound.class);

    private final ComponentFactory<DefaultRestconfStreamServletFactory> servletFactoryFactory;
    private final ComponentFactory<JaxRsNorthbound> northboundFactory;
    private final ComponentFactory<DefaultPingExecutor> pingExecutorFactory;

    private ComponentInstance<JaxRsNorthbound> northbound;
    private Map<String, ?> northboundProps;

    private ComponentInstance<DefaultPingExecutor> pingExecutor;
    private Map<String, ?> pingExecutorProps;

    private ComponentInstance<DefaultRestconfStreamServletFactory> servletFactory;
    private Map<String, ?> servletProps;

    @Activate
    public OSGiNorthbound(
            @Reference(target = "(component.factory=" + JaxRsNorthbound.FACTORY_NAME + ")")
            final ComponentFactory<JaxRsNorthbound> northboundFactory,
            @Reference(target = "(component.factory=" + DefaultPingExecutor.FACTORY_NAME + ")")
            final ComponentFactory<DefaultPingExecutor> pingExecutorFactory,
            @Reference(target = "(component.factory=" + DefaultRestconfStreamServletFactory.FACTORY_NAME + ")")
            final ComponentFactory<DefaultRestconfStreamServletFactory> servletFactoryFactory,
            final Configuration configuration) {
        this.northboundFactory = requireNonNull(northboundFactory);
        this.pingExecutorFactory = requireNonNull(pingExecutorFactory);
        this.servletFactoryFactory = requireNonNull(servletFactoryFactory);

        pingExecutorProps = DefaultPingExecutor.props(configuration.ping$_$executor$_$name$_$prefix(),
            configuration.max$_$thread$_$count());
        pingExecutor = pingExecutorFactory.newInstance(FrameworkUtil.asDictionary(pingExecutorProps));

        northboundProps = JaxRsNorthbound.props(
            configuration.data$_$missing$_$is$_$404() ? ErrorTagMapping.ERRATA_5565 : ErrorTagMapping.RFC8040,
            PrettyPrintParam.of(configuration.pretty$_$print()),
            new StreamsConfiguration(configuration.maximum$_$fragment$_$length(),
                configuration.idle$_$timeout(), configuration.heartbeat$_$interval()));
        northbound = northboundFactory.newInstance(FrameworkUtil.asDictionary(northboundProps));

        servletProps = DefaultRestconfStreamServletFactory.props(configuration.restconf());
        servletFactory = servletFactoryFactory.newInstance(FrameworkUtil.asDictionary(servletProps));

        LOG.info("Global RESTCONF northbound pools started");
    }

    @Modified
    void modified(final Configuration configuration) {
        final var newPingExecutorProps = DefaultPingExecutor.props(configuration.ping$_$executor$_$name$_$prefix(),
            configuration.max$_$thread$_$count());
        if (!newPingExecutorProps.equals(pingExecutorProps)) {
            pingExecutor.dispose();
            pingExecutor = null;
            pingExecutorProps = newPingExecutorProps;
            LOG.debug("PingExecutor stopped");
        }

        final var newNorthboundProps = JaxRsNorthbound.props(
            configuration.data$_$missing$_$is$_$404() ? ErrorTagMapping.ERRATA_5565 : ErrorTagMapping.RFC8040,
            PrettyPrintParam.of(configuration.pretty$_$print()),
            new StreamsConfiguration(configuration.maximum$_$fragment$_$length(),
                configuration.idle$_$timeout(), configuration.heartbeat$_$interval()));
        if (!newNorthboundProps.equals(northboundProps)) {
            northbound.dispose();
            northbound = null;
            northboundProps = newNorthboundProps;
            LOG.debug("Northbound stopped");
        }

        final var newServletProps = DefaultRestconfStreamServletFactory.props(configuration.restconf());
        if (!newServletProps.equals(servletProps)) {
            servletFactory.dispose();
            servletFactory = null;
            servletProps = newServletProps;
            LOG.debug("RestconfStreamServletFactory stopped");
        }

        if (pingExecutor == null) {
            pingExecutor = pingExecutorFactory.newInstance(FrameworkUtil.asDictionary(pingExecutorProps));
            LOG.debug("PingExecutor restarted with {}", pingExecutorProps);
        }
        if (northbound == null) {
            northbound = northboundFactory.newInstance(FrameworkUtil.asDictionary(northboundProps));
            LOG.debug("Northbound restarted with {}", northboundProps);
        }
        if (servletFactory == null) {
            servletFactory = servletFactoryFactory.newInstance(FrameworkUtil.asDictionary(servletProps));
            LOG.debug("RestconfStreamServletFactory restarted with {}", servletProps);
        }
        LOG.debug("Applied {}", configuration);
    }

    @Deactivate
    void deactivate() {
        northbound.dispose();
        northbound = null;
        pingExecutor.dispose();
        pingExecutor = null;
        servletFactory.dispose();
        servletFactory = null;
        LOG.info("Global RESTCONF northbound pools stopped");
    }
}
