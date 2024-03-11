/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import static java.util.Objects.requireNonNull;

import java.util.Map;
import org.opendaylight.netconf.transport.http.ConfigUtils;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.server.NettyEndpoint;
import org.opendaylight.restconf.server.NettyEndpointConfiguration;
import org.opendaylight.restconf.server.spi.EndpointConfiguration;
import org.opendaylight.restconf.server.spi.ErrorTagMapping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.HttpServerStackGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.http.server.stack.grouping.Transport;
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
 * Component managing global RESTCONF northbound endpoint.
 * FIXME: replace OSGiNorthbound with this class once restconf-server implementation is completed.
 */
@Component(service = {}, configurationPid = "org.opendaylight.restconf.nb.rfc8040.netty")
@Designate(ocd = OSGiNorthboundNetty.Configuration.class)
public final class OSGiNorthboundNetty {
    @ObjectClassDefinition
    public @interface Configuration {

        // below are used in EndpointConfiguration

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

        // Below are used in NettyEndpointConfiguration

        @AttributeDefinition(name = "{+restconf}", description = """
            The value of RFC8040 {+restconf} URI template, pointing to the root resource.
            Must not start or end with '/'.""")
        String restconf() default "rests";

        @AttributeDefinition
        String bind$_$address() default "0.0.0.0";

        @AttributeDefinition(min = "1", max = "65535")
        int bind$_$port() default 8182;

        @AttributeDefinition(description = "Thread name prefix to be used by Netty's thread executor")
        String group$_$name() default "restconf-server";

        @AttributeDefinition(min = "0", description = "Netty's thread limit. 0 means no limits.")
        int group$_$threads() default 0;

        @AttributeDefinition(description = """
            Default encoding for outgoing messages. Only "xml" or "json" values are allowed.""")
        String default$_$encoding() default "json";
    }

    private static final Logger LOG = LoggerFactory.getLogger(OSGiNorthboundNetty.class);

    private final ComponentFactory<NettyEndpoint> endpointFactory;

    private ComponentInstance<NettyEndpoint> endpoint;
    private Map<String, ?> props;

    @Activate
    public OSGiNorthboundNetty(
            @Reference(target = "(component.factory=" + NettyEndpoint.FACTORY_NAME + ")")
            final ComponentFactory<NettyEndpoint> endpointFactory, final Configuration configuration) {
        this.endpointFactory = requireNonNull(endpointFactory);
        props = buildProps(configuration);
        endpoint = endpointFactory.newInstance(FrameworkUtil.asDictionary(props));
        LOG.info("Global RESTCONF northbound endpoint started");
    }

    @Modified
    void modified(final Configuration configuration) {
        final var newProps = buildProps(configuration);
        if (!newProps.equals(props)) {
            endpoint.dispose();
            props = newProps;
            endpoint = endpointFactory.newInstance(FrameworkUtil.asDictionary(props));
            LOG.debug("Global RESTCONF northbound endpoint restarted with {}", props);
        }
        LOG.debug("Applied {}", configuration);
    }

    @Deactivate
    void deactivate() {
        endpoint.dispose();
        endpoint = null;
        LOG.info("Global RESTCONF northbound endpoint stopped");
    }

    private static Map<String, ?> buildProps(final Configuration config) {
        return NettyEndpoint.props(
            new NettyEndpointConfiguration(
                config.data$_$missing$_$is$_$404() ? ErrorTagMapping.ERRATA_5565 : ErrorTagMapping.RFC8040,
                PrettyPrintParam.of(config.pretty$_$print()),
                Uint16.valueOf(config.maximum$_$fragment$_$length()),
                Uint32.valueOf(config.heartbeat$_$interval()),
                config.restconf(),
                config.group$_$name(),
                config.group$_$threads(),
                config.default$_$encoding(),
                buildTransportConfiguration(config))
        );
    }

    private static HttpServerStackGrouping buildTransportConfiguration(final Configuration config) {
        // TODO TLS support
        final var transport = ConfigUtils.serverTransportTcp(config.bind$_$address(), config.bind$_$port());
        return new HttpServerStackGrouping() {
            @Override
            public Class<? extends HttpServerStackGrouping> implementedInterface() {
                return HttpServerStackGrouping.class;
            }

            @Override
            public Transport getTransport() {
                return transport;
            }
        };
    }
}
