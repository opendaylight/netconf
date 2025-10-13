/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.jaxrs;

import static java.util.Objects.requireNonNull;

import java.io.UnsupportedEncodingException;
import java.util.Map;
import java.util.Set;
import javax.servlet.ServletException;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Application;
import javax.ws.rs.sse.Sse;
import javax.ws.rs.sse.SseEventSink;
import javax.xml.xpath.XPathExpressionException;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.aaa.filterchain.configuration.CustomFilterAdapterConfiguration;
import org.opendaylight.aaa.filterchain.filters.CustomFilterAdapter;
import org.opendaylight.aaa.web.FilterDetails;
import org.opendaylight.aaa.web.ServletDetails;
import org.opendaylight.aaa.web.WebContext;
import org.opendaylight.aaa.web.WebContextSecurer;
import org.opendaylight.aaa.web.WebServer;
import org.opendaylight.aaa.web.servlet.ServletSupport;
import org.opendaylight.restconf.server.api.EventStreamGetParams;
import org.opendaylight.restconf.server.api.MonitoringEncoding;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.opendaylight.yangtools.concepts.Registration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

/**
 * A JAX-RS-based RESTCONF server endpoint. Sets up {@link JaxRsRestconf} and related components via
 * {@link ServletSupport}, {@link WebContextSecurer} and {@link WebServer} to a {@link RestconfServer} and its
 * {@link RestconfStream.Registry}.
 */
@Component(factory = JaxRsEndpoint.FACTORY_NAME, service = JaxRsEndpoint.class)
public final class JaxRsEndpoint implements SSESenderFactory, AutoCloseable {
    public static final String FACTORY_NAME = "org.opendaylight.restconf.server.jaxrs.JaxRsEndpoint";

    private static final String PROP_CONFIGURATION = ".configuration";

    private final @NonNull JaxRsEndpointConfiguration configuration;
    private final DefaultPingExecutor pingExecutor;
    private final Registration discoveryReg;
    private final Registration restconfReg;

    @Activate
    public JaxRsEndpoint(@Reference final WebServer webServer, @Reference final WebContextSecurer webContextSecurer,
            @Reference final ServletSupport servletSupport,
            @Reference final CustomFilterAdapterConfiguration filterAdapterConfiguration,
            @Reference final RestconfServer server, @Reference final RestconfStream.Registry streamRegistry,
            final Map<String, ?> props) throws ServletException {
        this(webServer, webContextSecurer, servletSupport, filterAdapterConfiguration, server, streamRegistry,
            (JaxRsEndpointConfiguration) props.get(PROP_CONFIGURATION));
    }

    public JaxRsEndpoint(final WebServer webServer, final WebContextSecurer webContextSecurer,
            final ServletSupport servletSupport, final CustomFilterAdapterConfiguration filterAdapterConfiguration,
            final RestconfServer server, final RestconfStream.Registry streamRegistry,
            final JaxRsEndpointConfiguration configuration) throws ServletException {
        this.configuration = requireNonNull(configuration);
        pingExecutor = new DefaultPingExecutor(configuration.pingNamePrefix(), configuration.pingCorePoolSize());

        final var restconf = configuration.restconf();
        final var sseSender = this;

        final var restconfBuilder = WebContext.builder()
            .name("RESTCONF")
            .contextPath("/" + restconf)
            .supportsSessions(false)
            .addServlet(ServletDetails.builder()
                .addUrlPattern("/*")
                .servlet(servletSupport.createHttpServletBuilder(
                    new Application() {
                        @Override
                        public Set<Object> getSingletons() {
                            return Set.of(
                                new JsonJaxRsFormattableBodyWriter(), new XmlJaxRsFormattableBodyWriter(),
                                new JaxRsRestconf(server, streamRegistry, sseSender, configuration.errorTagMapping(),
                                    configuration.prettyPrint()));
                        }
                    }).build())
                .asyncSupported(true)
                .build())

            // Allows user to add javax.servlet.Filter(s) in front of REST services
            .addFilter(FilterDetails.builder()
                .addUrlPattern("/*")
                .filter(new CustomFilterAdapter(filterAdapterConfiguration))
                .asyncSupported(true)
                .build());

        webContextSecurer.requireAuthentication(restconfBuilder, true, "/*");

        restconfReg = webServer.registerWebContext(restconfBuilder.build());

        final var discoveryBuilder = WebContext.builder()
            .name("WellKnownURIs")
            .contextPath("/.well-known")
            .supportsSessions(false)
            .addServlet(ServletDetails.builder()
                .addUrlPattern("/*")
                .servlet(servletSupport.createHttpServletBuilder(
                    new Application() {
                        @Override
                        public Set<Object> getSingletons() {
                            return Set.of(new JaxRsWebHostMetadata(restconf));
                        }
                    }).build())
                .name("Rootfound")
                .build());

        webContextSecurer.requireAuthentication(discoveryBuilder, true, "/*");

        discoveryReg = webServer.registerWebContext(discoveryBuilder.build());
    }

    public @NonNull JaxRsEndpointConfiguration configuration() {
        return configuration;
    }

    @Deactivate
    @Override
    public void close() {
        discoveryReg.close();
        restconfReg.close();
        pingExecutor.close();
    }

    @Override
    public void newSSESender(final SseEventSink sink, final Sse sse, final RestconfStream<?> stream,
            final MonitoringEncoding encoding, final EventStreamGetParams getParams) {
        // FIXME: invert control here: we should call 'listener.addSession()', which in turn should call
        //        handler.init()/handler.close()
        final var handler = new SSESender(pingExecutor, sink, sse, stream, encoding, getParams,
            configuration.sseMaximumFragmentLength().toJava(), configuration.sseHeartbeatIntervalMillis().toJava());

        try {
            handler.init();
        } catch (UnsupportedEncodingException e) {
            throw new NotFoundException("Unsupported encoding " + encoding.value(), e);
        } catch (IllegalArgumentException | XPathExpressionException e) {
            throw new BadRequestException(e.getMessage(), e);
        }
    }

    public static Map<String, ?> props(final JaxRsEndpointConfiguration configuration) {
        return Map.of(PROP_CONFIGURATION, configuration);
    }
}
