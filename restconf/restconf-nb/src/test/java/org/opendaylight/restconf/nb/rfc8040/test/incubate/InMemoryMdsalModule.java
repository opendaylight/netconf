/*
 * Copyright (c) 2019 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.test.incubate;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import javax.annotation.PreDestroy;
import javax.inject.Singleton;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.dom.adapter.test.AbstractBaseDataBrokerTest;
import org.opendaylight.mdsal.binding.dom.adapter.test.AbstractConcurrentDataBrokerTest;
import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMNotificationPublishService;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.broker.DOMMountPointServiceImpl;
import org.opendaylight.mdsal.dom.broker.DOMNotificationRouter;
import org.opendaylight.mdsal.dom.broker.DOMRpcRouter;
import org.opendaylight.mdsal.dom.spi.DOMNotificationSubscriptionListenerRegistry;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContextProvider;

/**
 * Copy paste from org.opendaylight.controller.sal.restconf.impl.test.incubate.InMemoryMdsalModule.
 *
 * @author Michael Vorburger.ch
 */
public class InMemoryMdsalModule extends AbstractModule {
    private static final int NOTIFICATION_SERVICE_QUEUE_DEPTH = 128;

    private final AbstractBaseDataBrokerTest dataBrokerTest;
    private final DOMNotificationRouter domNotificationRouter;

    public InMemoryMdsalModule() throws Exception {
        dataBrokerTest = new AbstractConcurrentDataBrokerTest(true) {
            // NOT AbstractDataBrokerTest
        };
        dataBrokerTest.setup();

        domNotificationRouter = DOMNotificationRouter.create(NOTIFICATION_SERVICE_QUEUE_DEPTH);
    }

    @Override
    protected void configure() {
    }

    @Provides
    @Singleton
    DataBroker getDataBroker() {
        return dataBrokerTest.getDataBroker();
    }

    @Provides
    @Singleton DOMDataBroker getDOMDataBroker() {
        return dataBrokerTest.getDomBroker();
    }

    @Provides
    @Singleton DOMNotificationRouter getDOMNotificationRouter() {
        return dataBrokerTest.getDataBrokerTestCustomizer().getDomNotificationRouter();
    }

    @Provides
    @Singleton DOMSchemaService getSchemaService() {
        return dataBrokerTest.getDataBrokerTestCustomizer().getSchemaService();
    }

    @Provides
    @Singleton EffectiveModelContextProvider getSchemaContextProvider() {
        DOMSchemaService schemaService = dataBrokerTest.getDataBrokerTestCustomizer().getSchemaService();
        if (schemaService instanceof EffectiveModelContextProvider) {
            return (EffectiveModelContextProvider) schemaService;
        }
        throw new IllegalStateException(
                "The schema service isn't a SchemaContextProvider, it's a " + schemaService.getClass());
    }

    @Provides
    @Singleton DOMMountPointService getDOMMountPoint() {
        return new DOMMountPointServiceImpl();
    }

    @Provides
    @Singleton DOMNotificationService getDOMNotificationService() {
        return domNotificationRouter;
    }

    @Provides
    @Singleton DOMNotificationPublishService getDOMNotificationPublishService() {
        return domNotificationRouter;
    }

    @Provides
    @Singleton DOMNotificationSubscriptionListenerRegistry getDOMNotificationSubscriptionListenerRegistry() {
        return domNotificationRouter;
    }

    @Provides
    @Singleton DOMRpcService getDOMRpcService(final DOMSchemaService schemaService) {
        return new DOMRpcRouter(schemaService).getRpcService();
    }

    @Provides
    @Singleton
    DOMActionService getDOMActionService(final DOMSchemaService schemaService) {
        return new DOMRpcRouter(schemaService).getActionService();
    }

    @PreDestroy
    public void close() {
    }
}
