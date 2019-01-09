/*
 * Copyright (c) 2019 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.controller.sal.restconf.impl.test.incubate;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import javax.annotation.PreDestroy;
import javax.inject.Singleton;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.dom.adapter.BindingToNormalizedNodeCodec;
import org.opendaylight.mdsal.binding.dom.adapter.test.AbstractBaseDataBrokerTest;
import org.opendaylight.mdsal.binding.dom.adapter.test.AbstractConcurrentDataBrokerTest;
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
import org.opendaylight.mdsal.dom.store.inmemory.InMemoryDOMDataStore;
import org.opendaylight.yangtools.yang.model.api.SchemaContextProvider;

/**
 * Guice Module which binds the mdsal (not controller) {@link DataBroker} & Co.
 * in-memory implementation suitable for tests.
 *
 * <p>BEWARE: Do *NOT* use this module in component tests or applications mixing
 * code requiring the old controller and the new mdsal {@link DataBroker} & Co.
 * APIs together - because this binds a *SEPARATE* {@link InMemoryDOMDataStore},
 * and doesn't delegate to controller's InMemoryDOMDataStore. This is just fine
 * for tests where all code under test already uses only the mdsal APIs.
 *
 * <p>This class is here temporarily.  It is inspired by the <a href=
 * "https://github.com/vorburger/opendaylight-simple/blob/519e08055f5ce164b50d4bc6c70c3dc3451aea98/src/main/java/org/opendaylight/mdsal/simple/MdsalModule.java">
 * MdsalModule in opendaylight-simple</a>,
 *
 * <p>and partially copy/pasted from
 * <a href="https://git.opendaylight.org/gerrit/#/c/79464/">DataBrokerTestWiring
 * in genius from c/79464,</a>
 *
 * <p>and may eventually be offered by the mdsal project itself.
 *
 * @author Michael Vorburger.ch
 */
public class InMemoryMdsalModule extends AbstractModule {

    private static final int NOTIFICATION_SERVICE_QUEUE_DEPTH = 128;

    private final AbstractBaseDataBrokerTest dataBrokerTest;
    private final DOMNotificationRouter domNotificationRouter;

    public InMemoryMdsalModule() throws Exception {
        dataBrokerTest = new AbstractConcurrentDataBrokerTest() { // NOT AbstractDataBrokerTest
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
    @Singleton BindingToNormalizedNodeCodec getBindingToNormalizedNodeCodec() {
        return dataBrokerTest.getDataBrokerTestCustomizer().getBindingToNormalized();
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
    @Singleton SchemaContextProvider getSchemaContextProvider() {
        DOMSchemaService schemaService = dataBrokerTest.getDataBrokerTestCustomizer().getSchemaService();
        if (schemaService instanceof SchemaContextProvider) {
            return (SchemaContextProvider) schemaService;
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
    @Singleton DOMRpcService getDOMRpcService(DOMSchemaService schemaService) {
        return DOMRpcRouter.newInstance(schemaService).getRpcService();
    }

    @PreDestroy
    public void close() {
        // TODO When moving this to mdsal, must close components to shut down Threads etc.
        // but cannot do this here (in netconf) yet, because we need to change AbstractBaseDataBrokerTest & Co..
    }
}
