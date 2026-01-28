/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.dagger.mdsal;

import com.google.errorprone.annotations.DoNotMock;
import dagger.Module;
import dagger.Provides;
import jakarta.inject.Singleton;
import java.lang.annotation.Annotation;
import java.nio.file.Path;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMNotificationPublishService;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.mdsal.dom.api.DOMRpcProviderService;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.broker.DOMMountPointServiceImpl;
import org.opendaylight.mdsal.dom.broker.DOMNotificationRouter;
import org.opendaylight.mdsal.dom.broker.DOMRpcRouter;
import org.opendaylight.mdsal.dom.broker.RouterDOMActionService;
import org.opendaylight.mdsal.dom.broker.RouterDOMNotificationService;
import org.opendaylight.mdsal.dom.broker.RouterDOMPublishNotificationService;
import org.opendaylight.mdsal.dom.broker.RouterDOMRpcProviderService;
import org.opendaylight.mdsal.dom.broker.RouterDOMRpcService;
import org.opendaylight.mdsal.dom.spi.FixedDOMSchemaService;
import org.opendaylight.netconf.dagger.config.ConfigLoader;
import org.opendaylight.netconf.dagger.mdsal.MdsalQualifiers.SchemaServiceContext;
import org.opendaylight.odlparent.dagger.ResourceSupport;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.context.properties.bind.Name;

/**
 * A Dagger module providing {@code mdsal-dom-broker} services.
 */
@Module
@DoNotMock
@NonNullByDefault
public interface MdsalDomBrokerModule {

    @Provides
    @Singleton
    static DOMMountPointService domMountPointService() {
        return new DOMMountPointServiceImpl();
    }

    @Provides
    @Singleton
    static DOMNotificationRouter domNotificationRouter(final ResourceSupport resourceSupport,
            final ConfigLoader configLoader) {
        final var config = configLoader.getConfig(DOMNotificationRouterConfig.class,
            "", Path.of("org.opendaylight.mdsal.dom.notification.cfg"));
        final var domNotificationRouter = new DOMNotificationRouter(config);
        resourceSupport.register(domNotificationRouter);
        return domNotificationRouter;
    }

    @Provides
    @Singleton
    static DOMRpcRouter domRpcRouter(final DOMSchemaService schemaService,
            final ResourceSupport resourceSupport) {
        final var domRpcRouter = new DOMRpcRouter(schemaService);
        resourceSupport.register(domRpcRouter);
        return domRpcRouter;
    }

    @Provides
    static DOMSchemaService domSchemaService(
            @SchemaServiceContext final EffectiveModelContext effectiveModel) {
        return new FixedDOMSchemaService(effectiveModel);
    }

    @Provides
    @Singleton
    static DOMActionService domActionService(final DOMRpcRouter router) {
        return new RouterDOMActionService(router);
    }

    @Provides
    @Singleton
    static DOMNotificationService domNotificationService(
            final DOMNotificationRouter notificationRouter) {
        return new RouterDOMNotificationService(notificationRouter);
    }

    @Provides
    @Singleton
    static DOMNotificationPublishService domNotificationPublishService(
            final DOMNotificationRouter notificationRouter) {
        return new RouterDOMPublishNotificationService(notificationRouter);
    }

    @Provides
    @Singleton
    static DOMRpcProviderService domRpcProviderService(final DOMRpcRouter router) {
        return new RouterDOMRpcProviderService(router);
    }

    @Provides
    @Singleton
    static DOMRpcService domRpcService(final DOMRpcRouter router) {
        return new RouterDOMRpcService(router);
    }

    /**
     * Implementation of OSGi DOMNotificationRouter configuration used for components that are not initialized
     * or managed by the OSGi.
     */
    @ConfigurationProperties
    class DOMNotificationRouterConfig implements DOMNotificationRouter.Config {
        private int queueDepth = 65536;

        @ConstructorBinding
        public DOMNotificationRouterConfig(@Name("notification-queue-depth") @DefaultValue("65536") int queueDepth) {
            this.queueDepth = queueDepth;
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return DOMNotificationRouter.Config.class;
        }

        @Override
        public int queueDepth() {
            return queueDepth;
        }
    }
}
