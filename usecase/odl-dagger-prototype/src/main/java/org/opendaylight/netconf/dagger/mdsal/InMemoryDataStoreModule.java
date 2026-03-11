/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.dagger.mdsal;

import com.google.errorprone.annotations.DoNotMock;
import dagger.MapKey;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoMap;
import jakarta.inject.Singleton;
import java.nio.file.Path;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.spi.store.DOMStore;
import org.opendaylight.mdsal.dom.store.inmemory.InMemoryDOMDataStore;
import org.opendaylight.netconf.dagger.springboot.config.ConfigLoader;
import org.opendaylight.odlparent.dagger.ResourceSupport;
import org.opendaylight.yangtools.util.concurrent.SpecialExecutors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.context.properties.bind.Name;
import org.springframework.context.annotation.Description;

/**
 * A Dagger module providing in-memory configuration and operational data-store.
 */
@Module
@DoNotMock
@NonNullByDefault
public interface InMemoryDataStoreModule {

    @Provides
    @Singleton
    @IntoMap
    @DatastoreTypeKey(LogicalDatastoreType.CONFIGURATION)
    static DOMStore configDatastore(final DOMSchemaService domSchemaService,
            final ConfigLoader configLoader, final ResourceSupport resourceSupport) {
        final var DataStoreConfig = configLoader.getConfig(DataStoreConfig.class, "configuration",
            Path.of("org.opendaylight.mdsal.inmemory.datastore.cfg"));

        final var configName = LogicalDatastoreType.CONFIGURATION.name();
        final var executorService = SpecialExecutors.newBlockingBoundedFastThreadPool(
            DataStoreConfig.dataChangeExecutorPoolSize, DataStoreConfig.dataChangeExecutorQueueSize,
            configName + "-DCL", InMemoryDOMDataStore.class);
        final var inMemoryDOMDataStore = new InMemoryDOMDataStore(configName, LogicalDatastoreType.CONFIGURATION,
            executorService, DataStoreConfig.dataChangeListenerQueueSize, DataStoreConfig.debugTransaction);

        resourceSupport.register(executorService);
        domSchemaService.registerSchemaContextListener(inMemoryDOMDataStore::onModelContextUpdated);
        resourceSupport.register(inMemoryDOMDataStore);

        return inMemoryDOMDataStore;
    }

    @Provides
    @Singleton
    @IntoMap
    @DatastoreTypeKey(LogicalDatastoreType.OPERATIONAL)
    static DOMStore operationalDatastore(final DOMSchemaService domSchemaService,
            final ConfigLoader configLoader, final ResourceSupport resourceSupport) {
        final var DataStoreConfig = configLoader.getConfig(DataStoreConfig.class, "operational",
            Path.of("org.opendaylight.mdsal.inmemory.datastore.cfg"));

        final var operName = LogicalDatastoreType.OPERATIONAL.name();
        final var executorService = SpecialExecutors.newBlockingBoundedFastThreadPool(
            DataStoreConfig.dataChangeExecutorPoolSize, DataStoreConfig.dataChangeExecutorQueueSize,
            operName + "-DCL", InMemoryDOMDataStore.class);
        final var inMemoryDOMDataStore = new InMemoryDOMDataStore(operName, LogicalDatastoreType.OPERATIONAL,
            executorService, DataStoreConfig.dataChangeListenerQueueSize, DataStoreConfig.debugTransaction);

        resourceSupport.register(executorService);
        domSchemaService.registerSchemaContextListener(inMemoryDOMDataStore::onModelContextUpdated);
        resourceSupport.register(inMemoryDOMDataStore);

        return inMemoryDOMDataStore;
    }

    @ConfigurationProperties
    record DataStoreConfig(
        @Name("max-data-change-executor-queue-size")
        @DefaultValue("1000")
        @Description("The maximum queue size for the data change notification executor.")
        int dataChangeExecutorQueueSize,

        @Name("max-data-change-executor-pool-size")
        @DefaultValue("20")
        @Description("The maximum thread pool size for the data change notification executor.")
        int dataChangeExecutorPoolSize,

        @Name("max-data-change-listener-queue-size")
        @DefaultValue("1000")
        @Description("The maximum queue size for the data change listeners.")
        int dataChangeListenerQueueSize,

        @Name("max-data-store-executor-queue-size")
        @DefaultValue("5000")
        @Description("The maximum queue size for the data store executor.")
        int dataStoreExecutorQueueSize,

        @Name("debug-transaction")
        @DefaultValue("false")
        @Description("True if transaction allocation debugging should be enabled.")
        boolean debugTransaction
    ) {}

    @MapKey
    @interface DatastoreTypeKey {
        LogicalDatastoreType value();
    }
}
