/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.dagger.netconf;

import com.google.errorprone.annotations.DoNotMock;
import dagger.Module;
import dagger.Provides;
import jakarta.inject.Singleton;
import java.nio.file.Path;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.aaa.shiro.web.env.AAAShiroWebEnvironment;
import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.singleton.api.ClusterSingletonServiceProvider;
import org.opendaylight.netconf.dagger.springboot.config.ConfigLoader;
import org.opendaylight.netconf.transport.http.HTTPServerOverTcp;
import org.opendaylight.netconf.transport.http.HttpServerStackConfiguration;
import org.opendaylight.netconf.transport.tcp.BootstrapFactory;
import org.opendaylight.odlparent.dagger.ResourceSupport;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.server.AAAShiroPrincipalService;
import org.opendaylight.restconf.server.MessageEncoding;
import org.opendaylight.restconf.server.NettyEndpoint;
import org.opendaylight.restconf.server.NettyEndpointConfiguration;
import org.opendaylight.restconf.server.PrincipalService;
import org.opendaylight.restconf.server.SimpleNettyEndpoint;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.opendaylight.restconf.server.jaxrs.JaxRsEndpointConfiguration;
import org.opendaylight.restconf.server.jaxrs.JaxRsLocationProvider;
import org.opendaylight.restconf.server.mdsal.MdsalDatabindProvider;
import org.opendaylight.restconf.server.mdsal.MdsalRestconfServer;
import org.opendaylight.restconf.server.mdsal.MdsalRestconfStreamRegistry;
import org.opendaylight.restconf.server.spi.EndpointConfiguration;
import org.opendaylight.restconf.server.spi.ErrorTagMapping;
import org.opendaylight.restconf.server.spi.RestconfStream.LocationProvider;
import org.opendaylight.restconf.server.spi.RestconfStream.Registry;
import org.opendaylight.yangtools.databind.DatabindProvider;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@Module
@DoNotMock
@NonNullByDefault
public interface RestconfNettyEndpointModule {

    @Provides
    @Singleton
    static MdsalDatabindProvider mdsalDatabindProvider(final DOMSchemaService schemaService,
            final ResourceSupport resourceSupport) {
        final var mdsalDatabindProvider = new MdsalDatabindProvider(schemaService);
        resourceSupport.register(mdsalDatabindProvider);
        return mdsalDatabindProvider;
    }

    @Provides
    @Singleton
    static DatabindProvider databindProvider(final MdsalDatabindProvider implementation) {
        return implementation;
    }

    @Provides
    @Singleton
    static RestconfServer restconfServer(final MdsalDatabindProvider databindProvider, final DOMDataBroker dataBroker,
            final DOMRpcService rpcService, final DOMActionService actionService,
            final DOMMountPointService mountPointService, final ResourceSupport resourceSupport) {
        final var mdsalRestconfServer = new MdsalRestconfServer(databindProvider, dataBroker, rpcService, actionService,
            mountPointService);
        resourceSupport.register(mdsalRestconfServer);
        return mdsalRestconfServer;
    }

    @Provides
    @Singleton
    static LocationProvider locationProvider() {
        return new JaxRsLocationProvider();
    }

    @Provides
    @Singleton
    static Registry mdsalRestconfStreamRegistry(final DOMDataBroker dataBroker,
           final DOMNotificationService notificationService, final DOMSchemaService schemaService,
           final LocationProvider locationProvider, final DatabindProvider databindProvider,
           final ClusterSingletonServiceProvider cssProvider, final ResourceSupport resourceSupport) {
        final var registry = new MdsalRestconfStreamRegistry(dataBroker, notificationService, schemaService,
            locationProvider, databindProvider, cssProvider);
        resourceSupport.register(registry);
        return registry;
    }

    @Provides
    @Singleton
    static RFC8040Configuration rfcConfig(final ConfigLoader configLoader) {
        return configLoader.getConfig(RFC8040Configuration.class, "rfc8040-configuration",
            Path.of("application.yaml"));
    }

    @Provides
    @Singleton
    static NettyEndpointConfiguration nettyEndpointConfiguration(final RFC8040Configuration config) {
        final var httpOverTcp = HTTPServerOverTcp.of(config.bindAddress(), config.bindPort());
        final var svcHeader = buildAltSvcHeader(config.bindPort, 0);

        return new NettyEndpointConfiguration(
            config.dataMissingIs404() ? ErrorTagMapping.ERRATA_5565 : ErrorTagMapping.RFC8040,
            PrettyPrintParam.of(config.prettyPrint), Uint16.valueOf(config.maximumFragmentLength),
            Uint32.valueOf(config.heartbeatInterval), config.apiRootPath, parseDefaultEncoding(config.defaultEncoding),
            new HttpServerStackConfiguration(httpOverTcp), Uint32.valueOf(config.http3AltSvcMaxAge()),
            Uint32.valueOf(config.httpChunkSize), svcHeader, Uint32.valueOf(config.http3AltSvcMaxAge()),
            Uint64.valueOf(config.http3InitialMaxData()),
            Uint64.valueOf(config.http3InitialMaxStreamDataBidirectionalRemote()),
            Uint32.valueOf(config.http3InitialMaxStreamsBidirectional()));
    }


    @Provides
    @Singleton
    static PrincipalService principalService(final AAAShiroWebEnvironment securityManager) {
        return new AAAShiroPrincipalService(securityManager);
    }

    @Provides
    @Singleton
    static BootstrapFactory bootstrapFactory(final RFC8040Configuration configuration,
            final ResourceSupport resourceSupport) {
        final var factory = new BootstrapFactory("odl-restconf-nb-worker", configuration.workerThreads,
            "odl-restconf-nb-boss", configuration.bossThreads);
        resourceSupport.register(factory);
        return factory;
    }


    @Provides
    @Singleton
    static NettyEndpoint nettyEndpoint(final RestconfServer server, final PrincipalService principalService,
            final Registry streamRegistry, final BootstrapFactory bootstrapFactory,
            final NettyEndpointConfiguration configuration, final ResourceSupport resourceSupport) {
        final var nettyEndpoint = new SimpleNettyEndpoint(server, principalService, streamRegistry, bootstrapFactory,
            configuration);
        resourceSupport.register(nettyEndpoint);
        return nettyEndpoint;
    }

    private static MessageEncoding parseDefaultEncoding(final String str) {
        if ("json".equalsIgnoreCase(str)) {
            return MessageEncoding.JSON;
        } else if ("xml".equalsIgnoreCase(str)) {
            return MessageEncoding.XML;
        } else {
            throw new IllegalArgumentException("Invalid default-encoding '" + str + "'");
        }
    }

    private static String buildAltSvcHeader(final int bindPort, final long maxAgeSeconds) {
        if (maxAgeSeconds < 0 || maxAgeSeconds > 2147483647) {
            throw new IllegalArgumentException("Invalid max-age " + maxAgeSeconds);
        }
        if (bindPort <= 0 || bindPort > 65535) {
            throw new IllegalArgumentException("Invalid bind port " + bindPort);
        }
        return "h3=\":%d\"; ma=%d".formatted(bindPort, maxAgeSeconds);
    }

    /**
     * Implementation of OSGi RFC8040Configuration configuration used for components that are not initialized
     * or managed by the OSGi.
     *
     * @param prettyPrint Control the default value of the odl-pretty-print query parameter.
     * @param dataMissingIs404 Control the HTTP status code reporting of conditions corresponding to "data-missing".
     *                         When set to true, the server will violate RFC8040 and report "404" instead of "409".
     * @param maximumFragmentLength Maximum SSE fragment length in number of Unicode code units (characters).
     * @param heartbeatInterval Interval in milliseconds between sending of ping control frames.
     * @param restconf The value of RFC8040 {+restconf} URI template, pointing to the root resource. Must not end with
     *                '/'.
     * @param pingExecutorNamePrefix Name of thread group Ping Executor will be run with.
     * @param maxThreadCount Number of threads Ping Executor will be run with.
     * @param bindAddress Server bind address.
     * @param bindPort Server bind port.
     * @param bossThreads The number of Netty boss threads. 0 means the Netty default, which is usually twice the number
     *                    of CPU cores.
     * @param workerThreads The number of Netty worker threads. 0 means the Netty default, which is usually twice
     *                      the number of CPU cores.
     * @param defaultEncoding Default encoding for outgoing messages. Expected values are 'xml' or 'json'.
     * @param tlsCertificate Path to certificate file.
     * @param tlsPrivateKey Path to private key file.
     * @param apiRootPath The URI path of the RESTCONF API root resource. This value will be used as the result of
     *                    {+restconf} URI Template. The format of this string must be conform to the "path-rootless"
     *                    ABNF rule of RFC3986 and is subject to decoding of any percent-encoded octets. Valid examples
     *                    include, without the double quotes:
     *                    <ul>
     *                    <li>"restconf", which is the default value and results in the same expansion as RFC8040,
     *                      i.e. the RESTCONF data resource being located at /restconf/data</li>
     *                    <li>"foo/bar/baz", which results in the RESTCONF data resource being located at
     *                       /foo/bar/baz/data</li>
     *                    <li>"foo%2f", which results in the RESTCONF data resource being located at /foo//data, where
     *                       "foo/" is a single segment, i.e. must be correctly percent-encoded by clients attempting
     *                       to access it..</li>
     *                    </ul>
     * @param httpChunkSize Maximum size of a data chunk emitted during response streaming. Must be >= 1.
     *                      This parameter is used in all HTTP implementations we provide including HTTP/1.1, HTTP/2
     *                      and HTTP/3. It represents the size of HTTP object we are pushing forward to Netty pipeline.
     *                      This object is then formatted by appropriate Netty's H1.1, H2 or H3 codec to proper
     *                      HttpObject(s) or Http2Frame(s)/Http3Frame(s).
     *                      Its main purpose is to avoid out-of-memory issues when sending large response.
     * @param http2MaxFrameSize Maximum HTTP/2 DATA frame payload size (SETTINGS_MAX_FRAME_SIZE) this server
     *                          is willing to accept from clients. If not overridden, the RFC7540 default
     *                          16384 is used. Valid range 16384–16777215.
     * @param http3AltSvcMaxAge Max-Age (ma) value advertised in Alt-Svc header for HTTP/3 (h3). The advertised port
     *                          is always bind-port. Set to 0 to disable Alt-Svc advertisement.
     * @param http3InitialMaxData QUIC connection-level initial max data limit in bytes for HTTP/3.
     * @param http3InitialMaxStreamDataBidirectionalRemote QUIC initial max stream data limit in bytes for
     *                                                     remotely-initiated bidirectional streams. Locally-initiated
     *                                                     stream limit is fixed to 262144 bytes.
     * @param http3InitialMaxStreamsBidirectional QUIC initial max number of bidirectional streams.
     */
    @ConfigurationProperties("rfc8040-configuration")
    record RFC8040Configuration(
        @DefaultValue("false")
        boolean prettyPrint,

        @DefaultValue("false")
        boolean dataMissingIs404,

        @DefaultValue("0")
        @Min(0)
        @Max(EndpointConfiguration.SSE_MAXIMUM_FRAGMENT_LENGTH_MAX)
        int maximumFragmentLength,

        @DefaultValue("10000")
        @Min(0)
        int heartbeatInterval,

        // Note: these are mirrored in JaxRsEndpointConfiguration
        @DefaultValue("rests")
        String restconf,

        @DefaultValue(JaxRsEndpointConfiguration.DEFAULT_NAME_PREFIX)
        String pingExecutorNamePrefix,

        @DefaultValue("" + JaxRsEndpointConfiguration.DEFAULT_CORE_POOL_SIZE)
        @Min(0)
        int maxThreadCount,

        @DefaultValue("0.0.0.0")
        String bindAddress,

        @DefaultValue("8182")
        @Min(1)
        @Max(65535)
        int bindPort,

        @DefaultValue("0")
        @Min(0)
        int bossThreads,

        @DefaultValue("0")
        @Min(0)
        int workerThreads,

        @DefaultValue("json")
        String defaultEncoding,

        String tlsCertificate,

        String tlsPrivateKey,

        @DefaultValue("restconf")
        String apiRootPath,

        @DefaultValue("262144") // 256 KiB
        @Min(1)
        int httpChunkSize,

        @DefaultValue("16384")
        @Min(16384)
        @Max(16777215)
        int http2MaxFrameSize,

        @DefaultValue("3600")
        @Min(0)
        @Max(2147483647)
        long http3AltSvcMaxAge,

        @DefaultValue("4194304")
        @Min(1)
        @Max(4611686018427387903L)
        long http3InitialMaxData,

        @DefaultValue("262144")
        @Min(0)
        @Max(4611686018427387903L)
        long http3InitialMaxStreamDataBidirectionalRemote,

        @DefaultValue("100")
        @Min(0)
        @Max(1152921504606846976L)
        long http3InitialMaxStreamsBidirectional
    ) {}
}
