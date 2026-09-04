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
import org.opendaylight.mdsal.dom.store.inmemory.InMemoryDOMStoreConfigProperties;
import org.opendaylight.mdsal.dom.store.inmemory.InMemoryDOMStoreFactory;
import org.opendaylight.netconf.dagger.springboot.config.ConfigLoader;
import org.opendaylight.odlparent.dagger.ResourceSupport;
import org.opendaylight.yangtools.yang.data.tree.api.DataTreeConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

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
    static DOMStore configDatastore(final DOMSchemaService domSchemaService, final ConfigLoader configLoader,
            final ResourceSupport resourceSupport, final InMemoryDOMStoreFactory storeFactory) {
        final var config = configLoader.getConfig(InMemoryDatastoreProperties.class,
            "odl.netconf.prototype.in-memory-datastore", Path.of("application.yaml")).configuration();
        final var domStore = storeFactory.create(LogicalDatastoreType.CONFIGURATION.name(),
            DataTreeConfiguration.DEFAULT_CONFIGURATION, InMemoryDOMStoreConfigProperties.builder()
            .debugTransactions(config.debugTransaction)
            .maxDataChangeExecutorPoolSize(config.maxDataChangeExecutorPoolSize)
            .maxDataChangeExecutorQueueSize(config.maxDataChangeExecutorQueueSize)
            .maxDataChangeListenerQueueSize(config.maxDataChangeListenerQueueSize)
            .build(), domSchemaService);
        resourceSupport.register(domStore);

        return domStore;
    }

    @Provides
    @Singleton
    @IntoMap
    @DatastoreTypeKey(LogicalDatastoreType.OPERATIONAL)
    static DOMStore operationalDatastore(final DOMSchemaService domSchemaService, final ConfigLoader configLoader,
            final ResourceSupport resourceSupport, final InMemoryDOMStoreFactory storeFactory) {
        final var config = configLoader.getConfig(InMemoryDatastoreProperties.class,
            "odl.netconf.prototype.in-memory-datastore", Path.of("application.yaml")).operational();
        final var domStore = storeFactory.create(LogicalDatastoreType.OPERATIONAL.name(),
            DataTreeConfiguration.DEFAULT_OPERATIONAL, InMemoryDOMStoreConfigProperties.builder()
            .debugTransactions(config.debugTransaction)
            .maxDataChangeExecutorPoolSize(config.maxDataChangeExecutorPoolSize)
            .maxDataChangeExecutorQueueSize(config.maxDataChangeExecutorQueueSize)
            .maxDataChangeListenerQueueSize(config.maxDataChangeListenerQueueSize)
            .build(), domSchemaService);
        resourceSupport.register(domStore);

        return domStore;
    }

    /**
     * Root for the data stores configuration.
     *
     * @param operational operational data store configuration.
     * @param configuration configuration data store configuration.
     */
    @ConfigurationProperties("odl.netconf.prototype.in-memory-datastore")
    record InMemoryDatastoreProperties(
        DataStoreConfig operational,
        DataStoreConfig configuration
    ) {}

    /**
     * Configuration properties when creating an operational InMemoryDOMDataStore.
     *
     * @param maxDataChangeExecutorQueueSize The maximum queue size for the data change notification executor.
     * @param maxDataChangeExecutorPoolSize The maximum thread pool size for the data change notification executor.
     * @param maxDataChangeListenerQueueSize The maximum queue size for the data change listeners.
     * @param maxDataStoreExecutorQueueSize The maximum queue size for the data store executor.
     * @param debugTransaction True if transaction allocation debugging should be enabled.
     */
    record DataStoreConfig(
        @DefaultValue("1000") int maxDataChangeExecutorQueueSize,
        @DefaultValue("20") int maxDataChangeExecutorPoolSize,
        @DefaultValue("1000") int maxDataChangeListenerQueueSize,
        @DefaultValue("5000") int maxDataStoreExecutorQueueSize,
        @DefaultValue("false") boolean debugTransaction
    ) {}

    @MapKey
    @interface DatastoreTypeKey {
        LogicalDatastoreType value();
    }
}
