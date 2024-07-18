/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import java.util.Map;
import java.util.Set;
import javax.servlet.ServletException;
import javax.ws.rs.core.Application;
import org.opendaylight.aaa.filterchain.configuration.CustomFilterAdapterConfiguration;
import org.opendaylight.aaa.filterchain.filters.CustomFilterAdapter;
import org.opendaylight.aaa.web.FilterDetails;
import org.opendaylight.aaa.web.ServletDetails;
import org.opendaylight.aaa.web.WebContext;
import org.opendaylight.aaa.web.WebContextSecurer;
import org.opendaylight.aaa.web.WebServer;
import org.opendaylight.aaa.web.servlet.ServletSupport;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.nb.jaxrs.JaxRsRestconf;
import org.opendaylight.restconf.nb.jaxrs.JaxRsWebHostMetadata;
import org.opendaylight.restconf.nb.jaxrs.JsonJaxRsFormattableBodyWriter;
import org.opendaylight.restconf.nb.jaxrs.ServerExceptionMapper;
import org.opendaylight.restconf.nb.jaxrs.XmlJaxRsFormattableBodyWriter;
import org.opendaylight.restconf.nb.rfc8040.streams.RestconfStreamServletFactory;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.opendaylight.yangtools.concepts.Registration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

/**
 * Main entrypoint into RFC8040 northbound. Take care of wiring up all applications activating them through JAX-RS.
 */
@Component(factory = JaxRsNorthbound.FACTORY_NAME, service = { })
public final class JaxRsNorthbound implements AutoCloseable {
    public static final String FACTORY_NAME = "org.opendaylight.restconf.nb.rfc8040.JaxRsNorthbound";

    private static final String PROP_ERROR_TAG_MAPPING = ".errorTagMapping";
    private static final String PROP_PRETTY_PRINT = ".prettyPrint";

    private final Registration discoveryReg;
    private final Registration restconfReg;

    public JaxRsNorthbound(final WebServer webServer, final WebContextSecurer webContextSecurer,
            final ServletSupport servletSupport, final CustomFilterAdapterConfiguration filterAdapterConfiguration,
            final RestconfServer server, final RestconfStreamServletFactory servletFactory,
            final ErrorTagMapping errorTagMapping, final PrettyPrintParam prettyPrint) throws ServletException {
        final var restconfBuilder = WebContext.builder()
            .name("RFC8040 RESTCONF")
            .contextPath("/" + servletFactory.restconf())
            .supportsSessions(false)
            .addServlet(ServletDetails.builder()
                .addUrlPattern("/*")
                .servlet(servletSupport.createHttpServletBuilder(
                    new Application() {
                        @Override
                        public Set<Object> getSingletons() {
                            return Set.of(
                                new JsonJaxRsFormattableBodyWriter(), new XmlJaxRsFormattableBodyWriter(),
                                new ServerExceptionMapper(errorTagMapping),
                                new JaxRsRestconf(server, errorTagMapping, prettyPrint));
                        }
                    }).build())
                .asyncSupported(true)
                .build())
            .addServlet(ServletDetails.builder()
                .addUrlPattern("/" + URLConstants.STREAMS_SUBPATH + "/*")
                .servlet(servletFactory.newStreamServlet())
                .name("notificationServlet")
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
            .name("RFC6415 Web Host Metadata")
            .contextPath("/.well-known")
            .supportsSessions(false)
            .addServlet(ServletDetails.builder()
                .addUrlPattern("/*")
                .servlet(servletSupport.createHttpServletBuilder(
                    new Application() {
                        @Override
                        public Set<Object> getSingletons() {
                            return Set.of(new JaxRsWebHostMetadata(servletFactory.restconf()));
                        }
                    }).build())
                .name("Rootfound")
                .build());

        webContextSecurer.requireAuthentication(discoveryBuilder, true, "/*");

        discoveryReg = webServer.registerWebContext(discoveryBuilder.build());
    }

    @Activate
    public JaxRsNorthbound(@Reference final WebServer webServer, @Reference final WebContextSecurer webContextSecurer,
            @Reference final ServletSupport servletSupport,
            @Reference final CustomFilterAdapterConfiguration filterAdapterConfiguration,
            @Reference final RestconfServer server, @Reference final RestconfStreamServletFactory servletFactory,
            final Map<String, ?> props) throws ServletException {
        this(webServer, webContextSecurer, servletSupport, filterAdapterConfiguration, server, servletFactory,
            (ErrorTagMapping) props.get(PROP_ERROR_TAG_MAPPING), (PrettyPrintParam) props.get(PROP_PRETTY_PRINT));
    }

    @Deactivate
    @Override
    public void close() {
        discoveryReg.close();
        restconfReg.close();
    }

    public static Map<String, ?> props(final ErrorTagMapping errorTagMapping, final PrettyPrintParam prettyPrint) {
        return Map.of(PROP_ERROR_TAG_MAPPING, errorTagMapping, PROP_PRETTY_PRINT, prettyPrint);
    }
}
