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
import org.opendaylight.netconf.transport.http.HTTPServerOverTcp;
import org.opendaylight.netconf.transport.http.HTTPServerOverTls;
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
import org.opendaylight.yangtools.yang.common.Uint64;
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

        @AttributeDefinition(
            name = "HTTP outbound data object (chunk) size (bytes)",
            description = """
                Maximum size of a data chunk emitted during response streaming. Must be >= 1.

                This parameter is used in all HTTP implementations we provide including HTTP/1.1, HTTP/2 and HTTP/3.
                It represents the size of HTTP object we are pushing forward to Netty pipeline. This object is then
                formatted by appropriate Netty's H1.1, H2 or H3 codec to proper HttpObject(s)
                or Http2Frame(s)/Http3Frame(s).

                Its main purpose is to avoid out-of-memory issues when sending large response.
                """,
                min = "1")
        int http$_$chunk$_$size() default 262144; // 256 KiB

        @AttributeDefinition(
            name = "HTTP/2 max frame size (bytes)",
            description = """
                Maximum HTTP/2 DATA frame payload size (SETTINGS_MAX_FRAME_SIZE) this server
                is willing to accept from clients. If not overridden, the RFC7540 default
                16384 is used. Valid range 16384–16777215.
                """,
            min = "16384", max = "16777215")
        int http2$_$max$_$frame$_$size() default 16384; // 16 KiB

        @AttributeDefinition(
            name = "HTTP write buffer low watermark (bytes)",
            description = """
                Netty channel write buffer low watermark used for outbound backpressure.
                A channel becomes writable again when queued outbound bytes fall below this value.
                """,
            min = "0")
        int http$_$write$_$buffer$_$low$_$watermark() default 32768; // 32 KiB

        @AttributeDefinition(
            name = "HTTP write buffer high watermark (bytes)",
            description = """
                Netty channel write buffer high watermark used for outbound backpressure.
                A channel becomes unwritable when queued outbound bytes exceed this value.
                """,
            min = "0")
        int http$_$write$_$buffer$_$high$_$watermark() default 65536; // 64 KiB

        @AttributeDefinition(
            name = "HTTP/3 Alt-Svc max-age (seconds)",
            description = """
                Max-Age (ma) value advertised in Alt-Svc header for HTTP/3 (h3).
                The advertised port is always bind-port.
                Set to 0 to disable Alt-Svc advertisement.
                """,
            min = "0", max = "2147483647")
        long http3$_$alt$_$svc$_$max$_$age() default 3600;

        @AttributeDefinition(
            name = "HTTP/3 initial max data (bytes)",
            description = "QUIC connection-level initial max data limit for HTTP/3.",
            min = "1", max = "4611686018427387903")
        long http3$_$initial$_$max$_$data() default 4L * 1024 * 1024;

        @AttributeDefinition(
            name = "HTTP/3 initial max stream data bidirectional remote (bytes)",
            description = """
                QUIC initial max stream data limit for remotely-initiated bidirectional streams.
                Locally-initiated bidirectional stream limit is fixed to 262144 bytes.
                """,
            min = "0", max = "4611686018427387903")
        long http3$_$initial$_$max$_$stream$_$data$_$bidirectional$_$remote() default 256L * 1024;

        @AttributeDefinition(
            name = "HTTP/3 initial max bidirectional streams",
            description = "QUIC initial max number of bidirectional streams.",
            min = "0", max = "1152921504606846976")
        long http3$_$initial$_$max$_$streams$_$bidirectional() default 100;
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
            ? HTTPServerOverTls.of(configuration.bind$_$address(), configuration.bind$_$port(),
                tlsCertKey.certificate(), tlsCertKey.privateKey())
            : HTTPServerOverTcp.of(configuration.bind$_$address(), configuration.bind$_$port());

        // advertise non-zero h3 support only when we have TLS (h3 requirement)
        final var altSvc = tlsCertKey != null
            ? buildAltSvcHeader(configuration.bind$_$port(), configuration.http3$_$alt$_$svc$_$max$_$age())
            : buildAltSvcHeader(configuration.bind$_$port(), 0);

        return OSGiNettyEndpoint.props(bootstrapFactory, new NettyEndpointConfiguration(
            configuration.data$_$missing$_$is$_$404() ? ErrorTagMapping.ERRATA_5565 : ErrorTagMapping.RFC8040,
            PrettyPrintParam.of(configuration.pretty$_$print()),
            Uint16.valueOf(configuration.maximum$_$fragment$_$length()),
            Uint32.valueOf(configuration.heartbeat$_$interval()), configuration.api$_$root$_$path(),
            parseDefaultEncoding(configuration.default$_$encoding()), new HttpServerStackConfiguration(transport),
            Uint32.valueOf(configuration.http$_$chunk$_$size()),
            Uint32.valueOf(configuration.http2$_$max$_$frame$_$size()),
            configuration.http$_$write$_$buffer$_$low$_$watermark(),
            configuration.http$_$write$_$buffer$_$high$_$watermark(),
            altSvc, configuration.bind$_$address(), configuration.bind$_$port(),
            tlsCertKey != null ? tlsCertKey.certificate() : null,
            tlsCertKey != null ? tlsCertKey.privateKey() : null,
            Uint32.valueOf(configuration.http3$_$alt$_$svc$_$max$_$age()),
            Uint64.valueOf(configuration.http3$_$initial$_$max$_$data()),
            Uint64.valueOf(configuration.http3$_$initial$_$max$_$stream$_$data$_$bidirectional$_$remote()),
            Uint32.valueOf(configuration.http3$_$initial$_$max$_$streams$_$bidirectional()))
        );
    }

    private static @NonNull MessageEncoding parseDefaultEncoding(final String str) {
        if ("json".equalsIgnoreCase(str)) {
            return MessageEncoding.JSON;
        }
        if ("xml".equalsIgnoreCase(str)) {
            return MessageEncoding.XML;
        }
        throw new IllegalArgumentException("Invalid default-encoding '" + str + "'");
    }

    private static @NonNull String buildAltSvcHeader(final int bindPort, final long maxAgeSeconds) {
        if (maxAgeSeconds < 0 || maxAgeSeconds > 2147483647) {
            throw new IllegalArgumentException("Invalid max-age " + maxAgeSeconds);
        }
        if (bindPort <= 0 || bindPort > 65535) {
            throw new IllegalArgumentException("Invalid bind port " + bindPort);
        }
        return "h3=\":%d\"; ma=%d".formatted(bindPort, maxAgeSeconds);
    }
}
