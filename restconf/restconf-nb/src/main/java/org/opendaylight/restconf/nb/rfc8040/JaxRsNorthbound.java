/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import com.google.common.annotations.Beta;
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
import org.opendaylight.restconf.nb.jaxrs.JaxRsRestconf;
import org.opendaylight.restconf.nb.jaxrs.JaxRsWebHostMetadata;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.JsonNormalizedNodeBodyWriter;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.JsonPatchStatusBodyWriter;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.XmlNormalizedNodeBodyWriter;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.XmlPatchStatusBodyWriter;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.errors.RestconfDocumentedExceptionMapper;
import org.opendaylight.restconf.nb.rfc8040.streams.RestconfStreamServletFactory;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.opendaylight.restconf.server.spi.DatabindProvider;
import org.opendaylight.yangtools.concepts.Registration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;

/**
 * Main entrypoint into RFC8040 northbound. Take care of wiring up all applications activating them through JAX-RS.
 */
@Beta
@Component(service = { })
@Designate(ocd = OSGiNorthbound.Configuration.class)
public final class JaxRsNorthbound implements AutoCloseable {
    private final Registration discoveryReg;
    private final Registration restconfReg;

    private String basePath;

    @Activate
    public JaxRsNorthbound(@Reference final WebServer webServer, @Reference final WebContextSecurer webContextSecurer,
            @Reference final ServletSupport servletSupport,
            @Reference final CustomFilterAdapterConfiguration filterAdapterConfiguration,
            @Reference final DatabindProvider databindProvider, @Reference final RestconfServer server,
            @Reference final RestconfStreamServletFactory servletFactory,
            @Reference final OSGiNorthbound.Configuration configuration) throws ServletException {
        this.basePath = configuration.base$_$path();

        final var restconfBuilder = WebContext.builder()
            .name("RFC8040 RESTCONF")
            .contextPath("/" + basePath)
            .supportsSessions(false)
            .addServlet(ServletDetails.builder()
                .addUrlPattern("/*")
                .servlet(servletSupport.createHttpServletBuilder(
                    new Application() {
                        @Override
                        public Set<Class<?>> getClasses() {
                            return Set.of(
                                JsonNormalizedNodeBodyWriter.class, XmlNormalizedNodeBodyWriter.class,
                                JsonPatchStatusBodyWriter.class, XmlPatchStatusBodyWriter.class);
                        }

                        @Override
                        public Set<Object> getSingletons() {
                            return Set.of(
                                new RestconfDocumentedExceptionMapper(databindProvider),
                                new JaxRsRestconf(server));
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
                            return Set.of(new JaxRsWebHostMetadata(basePath));
                        }
                    }).build())
                .name("Rootfound")
                .build());

        webContextSecurer.requireAuthentication(discoveryBuilder, true, "/*");

        discoveryReg = webServer.registerWebContext(discoveryBuilder.build());
    }

    @Deactivate
    @Override
    public void close() {
        discoveryReg.close();
        restconfReg.close();
    }
}
