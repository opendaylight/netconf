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
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.transport.http.ConfigUtils;
import org.opendaylight.netconf.transport.http.HttpServerStackConfiguration;
import org.opendaylight.netconf.transport.tcp.BootstrapFactory;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.server.MessageEncoding;
import org.opendaylight.restconf.server.NettyEndpoint;
import org.opendaylight.restconf.server.NettyEndpointConfiguration;
import org.opendaylight.restconf.server.OSGiNettyEndpoint;
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

        // Note: below (+restconf above) are used in NettyEndpointConfiguration

        @AttributeDefinition
        String bind$_$address() default "0.0.0.0";

        @AttributeDefinition(min = "1", max = "65535")
        int bind$_$port() default 8182;

        @AttributeDefinition(name = "Number of Netty boss threads.", min = "0")
        int boss$_$threads() default 0;

        @AttributeDefinition(name = "Number of Netty worker threads", min = "0")
        int worker$_$threads() default 0;

        @AttributeDefinition(description = "Default encoding for outgoing messages. Expected 'xml' or 'json'.")
        String default$_$encoding() default "json";

        @AttributeDefinition(description = "Path to certificate file")
        String tls$_$certificate() default "";

        @AttributeDefinition(description = "Path to private key file")
        String tls$_$private$_$key() default "";

        @AttributeDefinition(min = "1", description = """
            The URI path of the RESTCONF API root resource. This value will be used as the result of {+restconf} URI
            Template. The format of this string must be conform to the "path-rootless" ABNF rule of RFC3986 and is
            subject to decoding of any percent-encoded octets. Valid examples include, without the double quotes:

              - "restconf", which is the default value and results in the same expansion as RFC8040, i.e. the RESTCONF
                data resource being located at /restconf/data
              - "foo/bar/baz", which results in the RESTCONF data resource being located at /foo/bar/baz/data
              - "foo%2f", which results in the RESTCONF data resource being located at /foo//data, where "foo/" is a
                single segment, i.e. must be correctly percent-encoded by clients attempting to access it.
            """)
        String api$_$root$_$path() default "restconf";
    }

    private static final Logger LOG = LoggerFactory.getLogger(OSGiNorthbound.class);

    private final ComponentFactory<JaxRsEndpoint> jaxrsFactory;
    private final ComponentFactory<NettyEndpoint> nettyEndpointFactory;

    private ComponentInstance<JaxRsEndpoint> jaxrs;
    private ComponentInstance<NettyEndpoint> nettyEndpoint;
    private RestconfBootstrapFactory bootstrapFactory;
    private Map<String, ?> jaxrsProps;
    private Map<String, ?> nettyEndpointProps;
    private RestconfBootstrapFactory.Configuration bootstrapFactoryConfig;

    @Activate
    public OSGiNorthbound(
            @Reference(target = "(component.factory=" + JaxRsEndpoint.FACTORY_NAME + ")")
            final ComponentFactory<JaxRsEndpoint> jaxrsFactory,
            @Reference(target = "(component.factory=" + OSGiNettyEndpoint.FACTORY_NAME + ")")
            final ComponentFactory<NettyEndpoint> nettyEndpointFactory,
            final Configuration configuration) {
        this.jaxrsFactory = requireNonNull(jaxrsFactory);
        this.nettyEndpointFactory = requireNonNull(nettyEndpointFactory);

        jaxrsProps = newJaxrsProps(configuration);
        jaxrs = jaxrsFactory.newInstance(FrameworkUtil.asDictionary(jaxrsProps));

        bootstrapFactoryConfig = newBootstrapConfiguration(configuration);
        bootstrapFactory = new RestconfBootstrapFactory(bootstrapFactoryConfig);

        nettyEndpointProps = newNettyEndpointProps(bootstrapFactory, configuration);
        nettyEndpoint = nettyEndpointFactory.newInstance(FrameworkUtil.asDictionary(nettyEndpointProps));

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

        // allocate new bootstrap factory if needed
        final var newBoostrapFactoryConfig = newBootstrapConfiguration(configuration);
        final var bootstrap = newBoostrapFactoryConfig.equals(bootstrapFactoryConfig) ? bootstrapFactory
            : new RestconfBootstrapFactory(newBoostrapFactoryConfig);

        final var newNettyEndpointProps = newNettyEndpointProps(bootstrap, configuration);
        if (!newNettyEndpointProps.equals(nettyEndpointProps)) {
            nettyEndpoint.dispose();
            nettyEndpointProps = newNettyEndpointProps;
            nettyEndpoint = nettyEndpointFactory.newInstance(FrameworkUtil.asDictionary(nettyEndpointProps));
            LOG.debug("Netty northbound restarted with {}", nettyEndpointProps);
        }

        // close and replace old bootstrap factory if needed
        if (bootstrap != bootstrapFactory) {
            bootstrapFactory.close();
            bootstrapFactoryConfig = newBoostrapFactoryConfig;
            bootstrapFactory = bootstrap;
        }

        LOG.debug("Applied {}", configuration);
    }

    @Deactivate
    void deactivate() {
        jaxrs.dispose();
        jaxrs = null;
        nettyEndpoint.dispose();
        nettyEndpoint = null;
        bootstrapFactory.close();
        bootstrapFactory = null;
        LOG.info("Global RESTCONF northbound pools stopped");
    }

    private static RestconfBootstrapFactory.Configuration newBootstrapConfiguration(final Configuration configuration) {
        return new RestconfBootstrapFactory.Configuration(configuration.boss$_$threads(),
            configuration.worker$_$threads());
    }

    private static Map<String, ?> newJaxrsProps(final Configuration configuration) {
        return JaxRsEndpoint.props(new JaxRsEndpointConfiguration(
            configuration.data$_$missing$_$is$_$404() ? ErrorTagMapping.ERRATA_5565 : ErrorTagMapping.RFC8040,
            PrettyPrintParam.of(configuration.pretty$_$print()),
            Uint16.valueOf(configuration.maximum$_$fragment$_$length()),
            Uint32.valueOf(configuration.heartbeat$_$interval()), configuration.restconf(),
            configuration.ping$_$executor$_$name$_$prefix(), configuration.max$_$thread$_$count()));
    }

    private static Map<String, ?> newNettyEndpointProps(final BootstrapFactory bootstrapFactory,
            final Configuration configuration) {
        // FIXME: do not start the endpoint if we fail to read the files (i.e. secure-on-failure)!
        // TODO: why are we even using separate files here?
        final var tlsCertKey = TlsUtils.readCertificateKey(configuration.tls$_$certificate(),
            configuration.tls$_$private$_$key());

        final var transport = tlsCertKey != null
            ? ConfigUtils.serverTransportTls(configuration.bind$_$address(), configuration.bind$_$port(),
                tlsCertKey.certificate(), tlsCertKey.privateKey())
            : ConfigUtils.serverTransportTcp(configuration.bind$_$address(), configuration.bind$_$port());

        return OSGiNettyEndpoint.props(bootstrapFactory, new NettyEndpointConfiguration(
            configuration.data$_$missing$_$is$_$404() ? ErrorTagMapping.ERRATA_5565 : ErrorTagMapping.RFC8040,
            PrettyPrintParam.of(configuration.pretty$_$print()),
            Uint16.valueOf(configuration.maximum$_$fragment$_$length()),
            Uint32.valueOf(configuration.heartbeat$_$interval()), configuration.api$_$root$_$path(),
            parseDefaultEncoding(configuration.default$_$encoding()), new HttpServerStackConfiguration(transport))
        );
    }

    private static @NonNull MessageEncoding parseDefaultEncoding(final String str) {
        if ("json".equalsIgnoreCase(str)) {
            return MessageEncoding.JSON;
        } else if ("xml".equalsIgnoreCase(str)) {
            return MessageEncoding.XML;
        } else {
            throw new IllegalArgumentException("Invalid default-encoding '" + str + "'");
        }
    }
}
