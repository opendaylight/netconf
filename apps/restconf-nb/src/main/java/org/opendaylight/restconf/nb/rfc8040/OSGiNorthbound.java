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
import java.util.Set;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.server.jaxrs.JaxRsEndpoint;
import org.opendaylight.restconf.server.jaxrs.JaxRsEndpointConfiguration;
import org.opendaylight.restconf.server.spi.EndpointConfiguration;
import org.opendaylight.restconf.server.spi.ErrorTagMapping;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.opendaylight.yangtools.yang.common.Uint32;
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
        // Note: these are mirrored in EndpointConfiguration

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

        @AttributeDefinition(min = "0", max = "" + EndpointConfiguration.SSE_MAXIMUM_FRAGMENT_LENGTH_MAX)
        int maximum$_$fragment$_$length() default 0;

        @AttributeDefinition(min = "0")
        int heartbeat$_$interval() default 10000;

        // Note: these are mirrored in JaxRsEndpointConfiguration

        @AttributeDefinition(name = "{+restconf}", description = """
            The value of RFC8040 {+restconf} URI template, pointing to the root resource. Must not end with '/'.""")
        String restconf() default "rests";

        @AttributeDefinition(min = "1")
        String ping$_$executor$_$name$_$prefix() default JaxRsEndpointConfiguration.DEFAULT_NAME_PREFIX;

        // FIXME: this is a misnomer: it specifies the core pool size, i.e. minimum thread count, the maximum is set to
        //        Integer.MAX_VALUE, which is not what we want
        @AttributeDefinition(min = "0")
        int max$_$thread$_$count() default JaxRsEndpointConfiguration.DEFAULT_CORE_POOL_SIZE;

        @AttributeDefinition
        boolean restconf$_$logging$_$enabled() default false;

        @AttributeDefinition
        boolean logging$_$eaders$_$enabled() default true;

        @AttributeDefinition
        boolean logging$_$query$_$parameters$_$enabled() default true;

        @AttributeDefinition
        boolean logging$_$body$_$enabled() default true;

        @AttributeDefinition
        String[] hidden$_$http$_$headers() default { };
    }

    private static final Logger LOG = LoggerFactory.getLogger(OSGiNorthbound.class);

    private final ComponentFactory<JaxRsEndpoint> jaxrsFactory;

    private ComponentInstance<JaxRsEndpoint> jaxrs;
    private Map<String, ?> jaxrsProps;

    @Activate
    public OSGiNorthbound(
            @Reference(target = "(component.factory=" + JaxRsEndpoint.FACTORY_NAME + ")")
            final ComponentFactory<JaxRsEndpoint> jaxrsFactory, final Configuration configuration) {
        this.jaxrsFactory = requireNonNull(jaxrsFactory);

        jaxrsProps = newJaxrsProps(configuration);
        jaxrs = jaxrsFactory.newInstance(FrameworkUtil.asDictionary(jaxrsProps));

        LOG.info("Global RESTCONF northbound pools started");
    }

    @Modified
    void modified(final Configuration configuration) {
        final var newJaxRsProps = newJaxrsProps(configuration);
        if (!newJaxRsProps.equals(jaxrsProps)) {
            jaxrs.dispose();
            jaxrsProps = newJaxRsProps;
            jaxrs = jaxrsFactory.newInstance(FrameworkUtil.asDictionary(jaxrsProps));
            LOG.debug("JAX-RS northbound restarted with {}", jaxrsProps);
        }
        LOG.debug("Applied {}", configuration);
    }

    @Deactivate
    void deactivate() {
        jaxrs.dispose();
        jaxrs = null;
        LOG.info("Global RESTCONF northbound pools stopped");
    }

    private static Map<String, ?> newJaxrsProps(final Configuration configuration) {
        return JaxRsEndpoint.props(new JaxRsEndpointConfiguration(
            configuration.data$_$missing$_$is$_$404() ? ErrorTagMapping.ERRATA_5565 : ErrorTagMapping.RFC8040,
            PrettyPrintParam.of(configuration.pretty$_$print()),
            Uint16.valueOf(configuration.maximum$_$fragment$_$length()),
            Uint32.valueOf(configuration.heartbeat$_$interval()), configuration.restconf(),
            configuration.ping$_$executor$_$name$_$prefix(), configuration.max$_$thread$_$count(),
            configuration.restconf$_$logging$_$enabled(), configuration.logging$_$eaders$_$enabled(),
            configuration.logging$_$query$_$parameters$_$enabled(), configuration.logging$_$body$_$enabled(),
            Set.of(configuration.hidden$_$http$_$headers())));
    }
}
