/*
 * Copyright (c) 2018 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.jaxrs;

import com.fasterxml.jackson.core.JsonFactoryBuilder;
import java.util.Set;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.ServletException;
import javax.ws.rs.core.Application;
import org.opendaylight.aaa.web.ResourceDetails;
import org.opendaylight.aaa.web.ServletDetails;
import org.opendaylight.aaa.web.WebContext;
import org.opendaylight.aaa.web.WebContextSecurer;
import org.opendaylight.aaa.web.WebServer;
import org.opendaylight.aaa.web.servlet.ServletSupport;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.openapi.impl.OpenApiServiceImpl;
import org.opendaylight.restconf.openapi.model.security.OpenApiOauth2Configuration;
import org.opendaylight.restconf.server.jaxrs.JaxRsEndpoint;
import org.opendaylight.yangtools.concepts.Registration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * Initializes the wep app.
 *
 * @author Thomas Pantelis
 */
@Singleton
@Component(service = { }, configurationPid = "org.opendaylight.restconf.nb.rfc8040.oauth2")
public final class WebInitializer implements AutoCloseable {
    @ObjectClassDefinition
    public @interface Configuration {
        @AttributeDefinition(description = """
            Authorization endpoint URL of the OAuth2/OIDC identity provider (e.g. Keycloak or Entra ID).
            Used in the OpenAPI security scheme for the authorization-code-with-PKCE flow.
            Leave empty to disable the OAuth2 security scheme in the generated OpenAPI document.
            """)
        String oauth2$_$authorization$_$url() default "";

        @AttributeDefinition(description = """
            Token endpoint URL of the OAuth2/OIDC identity provider.
            Used in the OpenAPI security scheme for the authorization-code-with-PKCE flow.
            Leave empty to disable the OAuth2 security scheme in the generated OpenAPI document.
            """)
        String oauth2$_$token$_$url() default "";

        @AttributeDefinition(description = """
            Refresh endpoint URL of the OAuth2/OIDC identity provider.
            Leave empty (default) to omit refreshUrl from the generated OpenAPI document.
            """)
        String oauth2$_$refresh$_$url() default "";
    }

    private final OpenApiServiceImpl openApiService;
    private final Registration registration;

    @Inject
    @Activate
    public WebInitializer(@Reference final WebServer webServer, @Reference final WebContextSecurer webContextSecurer,
            @Reference final ServletSupport servletSupport, @Reference final DOMSchemaService schemaService,
            @Reference final DOMMountPointService mountPointService, @Reference final JaxRsEndpoint endpoint,
            final Configuration configuration) throws ServletException {
        final var oauth2Config = oauth2Configuration(configuration);
        openApiService = new OpenApiServiceImpl(schemaService, mountPointService,
            endpoint.configuration().restconf(), oauth2Config);
        final var webContextBuilder = WebContext.builder()
            .name("OpenAPI")
            .contextPath("/openapi")
            .supportsSessions(true)
            .addServlet(ServletDetails.builder()
                .servlet(servletSupport.createHttpServletBuilder(new Application() {
                    @Override
                    public Set<Object> getSingletons() {
                        return Set.of(new JaxRsOpenApi(openApiService),
                            new OpenApiBodyWriter(new JsonFactoryBuilder().build()));
                    }
                }).build())
                .addUrlPattern("/api/v3/*")
                .build())
            .addResource(ResourceDetails.builder().name("/explorer").build());

        webContextSecurer.requireAuthentication(webContextBuilder, "/*");

        registration = webServer.registerWebContext(webContextBuilder.build());
    }

    @Override
    @Deactivate
    @PreDestroy
    public void close() {
        registration.close();
        openApiService.close();
    }

    private static OpenApiOauth2Configuration oauth2Configuration(final Configuration configuration) {
        final var authUrl = configuration.oauth2$_$authorization$_$url();
        final var tokenUrl = configuration.oauth2$_$token$_$url();
        final var refreshUrl = configuration.oauth2$_$refresh$_$url();
        return authUrl.isBlank() || tokenUrl.isBlank() ? null : new OpenApiOauth2Configuration(authUrl, tokenUrl,
            refreshUrl);
    }
}
