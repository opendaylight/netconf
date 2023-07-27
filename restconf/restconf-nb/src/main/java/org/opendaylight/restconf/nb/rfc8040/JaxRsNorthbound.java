/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import static org.opendaylight.restconf.nb.rfc8040.URLConstants.BASE_PATH;
import static org.opendaylight.restconf.nb.rfc8040.URLConstants.SSE_SUBPATH;
import static org.opendaylight.restconf.nb.rfc8040.rests.utils.RestconfStreamsConstants.DATA_SUBSCRIPTION;
import static org.opendaylight.restconf.nb.rfc8040.rests.utils.RestconfStreamsConstants.NOTIFICATION_STREAM;

import com.google.common.annotations.Beta;
import javax.servlet.ServletException;
import org.opendaylight.aaa.filterchain.configuration.CustomFilterAdapterConfiguration;
import org.opendaylight.aaa.filterchain.filters.CustomFilterAdapter;
import org.opendaylight.aaa.web.FilterDetails;
import org.opendaylight.aaa.web.ServletDetails;
import org.opendaylight.aaa.web.WebContext;
import org.opendaylight.aaa.web.WebContextSecurer;
import org.opendaylight.aaa.web.WebServer;
import org.opendaylight.aaa.web.servlet.ServletSupport;
import org.opendaylight.controller.config.threadpool.util.NamingThreadPoolFactory;
import org.opendaylight.controller.config.threadpool.util.ScheduledThreadPoolWrapper;
import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindProvider;
import org.opendaylight.restconf.nb.rfc8040.rests.services.impl.RestconfDataStreamServiceImpl;
import org.opendaylight.restconf.nb.rfc8040.streams.StreamsConfiguration;
import org.opendaylight.restconf.nb.rfc8040.streams.WebSocketInitializer;
import org.opendaylight.yangtools.concepts.Registration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * Main entrypoint into RFC8040 northbound. Take care of wiring up all applications activating them through JAX-RS.
 */
@Beta
@Component(service = { }, configurationPid = "org.opendaylight.restconf.nb.rfc8040")
@Designate(ocd = JaxRsNorthbound.Configuration.class)
public final class JaxRsNorthbound implements AutoCloseable {
    @ObjectClassDefinition
    public @interface Configuration {
        @AttributeDefinition(min = "0", max = "" + StreamsConfiguration.MAXIMUM_FRAGMENT_LENGTH_LIMIT)
        int maximum$_$fragment$_$length() default 0;
        @AttributeDefinition(min = "0")
        int heartbeat$_$interval() default 10000;
        @AttributeDefinition(min = "1")
        int idle$_$timeout() default 30000;
        @AttributeDefinition(min = "1")
        String ping$_$executor$_$name$_$prefix() default "ping-executor";
        // FIXME: this is a misnomer: it specifies the core pool size, i.e. minimum thread count, the maximum is set to
        //        Integer.MAX_VALUE, which is not what we want
        @AttributeDefinition(min = "0")
        int max$_$thread$_$count() default 1;
        @AttributeDefinition
        boolean use$_$sse() default true;
    }

    private final Registration discoveryReg;
    private final Registration restconfReg;

    @Activate
    public JaxRsNorthbound(@Reference final WebServer webServer, @Reference final WebContextSecurer webContextSecurer,
            @Reference final ServletSupport servletSupport,
            @Reference final CustomFilterAdapterConfiguration filterAdapterConfiguration,
            @Reference final DOMActionService actionService, @Reference final DOMDataBroker dataBroker,
            @Reference final DOMMountPointService mountPointService,
            @Reference final DOMNotificationService notificationService, @Reference final DOMRpcService rpcService,
            @Reference final DOMSchemaService schemaService, @Reference final DatabindProvider databindProvider,
            final Configuration configuration) throws ServletException {
        this(webServer, webContextSecurer, servletSupport, filterAdapterConfiguration, actionService, dataBroker,
            mountPointService, notificationService, rpcService, schemaService, databindProvider,
            configuration.ping$_$executor$_$name$_$prefix(), configuration.max$_$thread$_$count(),
            new StreamsConfiguration(configuration.maximum$_$fragment$_$length(), configuration.idle$_$timeout(),
                configuration.heartbeat$_$interval(), configuration.use$_$sse()));
    }

    public JaxRsNorthbound(final WebServer webServer, final WebContextSecurer webContextSecurer,
            final ServletSupport servletSupport, final CustomFilterAdapterConfiguration filterAdapterConfiguration,
            final DOMActionService actionService, final DOMDataBroker dataBroker,
            final DOMMountPointService mountPointService, final DOMNotificationService notificationService,
            final DOMRpcService rpcService, final DOMSchemaService schemaService,
            final DatabindProvider databindProvider,
            final String pingNamePrefix, final int pingMaxThreadCount,
            final StreamsConfiguration streamsConfiguration) throws ServletException {
        final var scheduledThreadPool = new ScheduledThreadPoolWrapper(pingMaxThreadCount,
            new NamingThreadPoolFactory(pingNamePrefix));

        final var restconfBuilder = WebContext.builder()
            .name("RFC8040 RESTCONF")
            .contextPath("/" + BASE_PATH)
            .supportsSessions(false)
            .addServlet(ServletDetails.builder()
                .addUrlPattern("/*")
                .servlet(servletSupport.createHttpServletBuilder(
                    new RestconfApplication(databindProvider, mountPointService, dataBroker, rpcService, actionService,
                        notificationService, schemaService, streamsConfiguration)).build())
                .asyncSupported(true)
                .build())
            .addServlet(ServletDetails.builder()
                .addUrlPattern("/" + SSE_SUBPATH + "/*")
                .servlet(servletSupport.createHttpServletBuilder(
                    new DataStreamApplication(databindProvider, mountPointService,
                        new RestconfDataStreamServiceImpl(scheduledThreadPool, streamsConfiguration))).build())
                .name("notificationServlet")
                .asyncSupported(true)
                .build())
            .addServlet(ServletDetails.builder()
                .addUrlPattern("/" + DATA_SUBSCRIPTION + "/*")
                .addUrlPattern("/" + NOTIFICATION_STREAM + "/*")
                .servlet(new WebSocketInitializer(scheduledThreadPool, streamsConfiguration))
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
                .servlet(servletSupport.createHttpServletBuilder(new RootFoundApplication(BASE_PATH)).build())
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
