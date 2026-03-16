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
import org.springframework.boot.context.properties.bind.Name;
import org.springframework.context.annotation.Description;

@Module
@DoNotMock
@NonNullByDefault
public interface RestconfModule {

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
        return configLoader.getConfig(RFC8040Configuration.class, "", Path.of(
           "org.opendaylight.restconf.nb.rfc8040.cfg"));
    }

    @Provides
    @Singleton
    static NettyEndpointConfiguration nettyEndpointConfiguration(final RFC8040Configuration config) {
        final var httpOverTcp = HTTPServerOverTcp.of(config.bindAddress(), config.bindPort());
        final var svcHeader = buildAltSvcHeader(config.bindPort, 0);

        return new NettyEndpointConfiguration(
            config.dataMissingIs404() ? ErrorTagMapping.ERRATA_5565 : ErrorTagMapping.RFC8040,
            PrettyPrintParam.of(config.prettyPrint), Uint16.valueOf(config.maxFragmentLength),
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
    static BootstrapFactory bootstrapFactory(final RFC8040Configuration configuration) {
        return new BootstrapFactory("odl-restconf-nb-worker", configuration.workerThreads,
            "odl-restconf-nb-boss", configuration.bossThreads);
    }


    @Provides
    @Singleton
    static NettyEndpoint nettyEndpoint(final RestconfServer server, final PrincipalService principalService,
            final Registry streamRegistry, final BootstrapFactory bootstrapFactory,
            final NettyEndpointConfiguration configuration) {
        return new SimpleNettyEndpoint(server, principalService, streamRegistry, bootstrapFactory, configuration);
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
     * Implementation of OSGi DOMNotificationRouter configuration used for components that are not initialized
     * or managed by the OSGi.
     */
    @ConfigurationProperties
    record RFC8040Configuration(
        @Name("pretty-print")
        @DefaultValue("false")
        boolean prettyPrint,

        @Name("data-missing-is-404")
        @DefaultValue("false")
        boolean dataMissingIs404,

        @Name("maximum-fragment-length")
        @DefaultValue("0")
        @Min(0)
        @Max(EndpointConfiguration.SSE_MAXIMUM_FRAGMENT_LENGTH_MAX)
        int maxFragmentLength,

        @Name("heartbeat-interval")
        @DefaultValue("10000")
        @Min(0)
        int heartbeatInterval,

        // Note: these are mirrored in JaxRsEndpointConfiguration
        @Name("restconf")
        @DefaultValue("rests")
        String restconf,

        @Name("ping-executor-name-prefix")
        @DefaultValue(JaxRsEndpointConfiguration.DEFAULT_NAME_PREFIX)
        String pingExecutorNamePrefix,

        @Name("max-thread-count")
        @DefaultValue("" + JaxRsEndpointConfiguration.DEFAULT_CORE_POOL_SIZE)
        @Min(0)
        int maxThreadCount,

        @Name("bind-address")
        @DefaultValue("0.0.0.0")
        String bindAddress,

        @Name("bind-port")
        @DefaultValue("8182")
        @Min(1)
        @Max(65535)
        int bindPort,

        @Name("boss-threads")
        @Description("Number of Netty boss threads.")
        @DefaultValue("0")
        @Min(0)
        int bossThreads,

        @Name("worker-threads")
        @Description("Number of Netty worker threads.")
        @DefaultValue("0")
        @Min(0)
        int workerThreads,

        @Name("default-encoding")
        @Description("Default encoding for outgoing messages. Expected 'xml' or 'json'.")
        @DefaultValue("json")
        @Min(0)
        String defaultEncoding,

        @Name("tls-certificate")
        @Description("Path to certificate file")
        String tlsCertificate,


        @Name("tls-private-key")
        @Description("Path to private key file")
        String tlsPrivateKey,

        @Name("api-root-path")
        @Description("""
            The URI path of the RESTCONF API root resource. This value will be used as the result of {+restconf} URI
            Template. The format of this string must be conform to the "path-rootless" ABNF rule of RFC3986 and is
            subject to decoding of any percent-encoded octets. Valid examples include, without the double quotes:

              - "restconf", which is the default value and results in the same expansion as RFC8040, i.e. the RESTCONF
                data resource being located at /restconf/data
              - "foo/bar/baz", which results in the RESTCONF data resource being located at /foo/bar/baz/data
              - "foo%2f", which results in the RESTCONF data resource being located at /foo//data, where "foo/" is a
                single segment, i.e. must be correctly percent-encoded by clients attempting to access it.
            """)
        @DefaultValue("restconf")
        String apiRootPath,

        @Name("http-chunk-size")
        @Description("""
            Maximum size of a data chunk emitted during response streaming. Must be >= 1.

            This parameter is used in all HTTP implementations we provide including HTTP/1.1, HTTP/2 and HTTP/3.
            It represents the size of HTTP object we are pushing forward to Netty pipeline. This object is then
            formatted by appropriate Netty's H1.1, H2 or H3 codec to proper HttpObject(s)
            or Http2Frame(s)/Http3Frame(s).

            Its main purpose is to avoid out-of-memory issues when sending large response.
            """)
        @DefaultValue("262144") // 256 KiB
        @Min(1)
        int httpChunkSize,

        @Name("http2-max-frame-size")
        @Description("""
            Maximum HTTP/2 DATA frame payload size (SETTINGS_MAX_FRAME_SIZE) this server
            is willing to accept from clients. If not overridden, the RFC7540 default
            16384 is used. Valid range 16384–16777215.
            """)
        @DefaultValue("16384")
        @Min(16384)
        @Max(16777215)
        int http2MaxFrameSize,

        @Name("http3-alt-svc-max-age")
        @Description("""
            Max-Age (ma) value advertised in Alt-Svc header for HTTP/3 (h3).
            The advertised port is always bind-port.
            Set to 0 to disable Alt-Svc advertisement.
            """)
        @DefaultValue("3600")
        @Min(0)
        @Max(2147483647)
        long http3AltSvcMaxAge,

        @Name("http3-initial-max-data")
        @Description("QUIC connection-level initial max data limit for HTTP/3.")
        @DefaultValue("4194304")
        @Min(1)
        @Max(4611686018427387903L)
        long http3InitialMaxData,

        @Name("http3-initial-max-stream-data-bidirectional-remote")
        @Description("""
            QUIC initial max stream data limit for remotely-initiated bidirectional streams.
            Locally-initiated bidirectional stream limit is fixed to 262144 bytes.
            """)
        @DefaultValue("262144")
        @Min(0)
        @Max(4611686018427387903L)
        long http3InitialMaxStreamDataBidirectionalRemote,

        @Name("http3-initial-max-streams-bidirectional")
        @Description("QUIC initial max number of bidirectional streams.")
        @DefaultValue("100")
        @Min(0)
        @Max(1152921504606846976L)
        long http3InitialMaxStreamsBidirectional
    ) {}
}
