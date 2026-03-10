/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.dagger.controller;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.errorprone.annotations.DoNotMock;
import dagger.Module;
import dagger.Provides;
import jakarta.inject.Qualifier;
import jakarta.inject.Singleton;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.nio.file.Path;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.controller.cluster.ActorSystemProvider;
import org.opendaylight.controller.cluster.akka.impl.ActorSystemProviderImpl;
import org.opendaylight.controller.cluster.akka.impl.AkkaConfigFactory;
import org.opendaylight.controller.cluster.common.actor.AkkaConfigurationReader;
import org.opendaylight.controller.cluster.common.actor.QuarantinedMonitorActor;
import org.opendaylight.controller.cluster.databroker.ConcurrentDOMDataBroker;
import org.opendaylight.controller.cluster.databroker.DataBrokerCommitExecutor;
import org.opendaylight.controller.cluster.datastore.AbstractDataStore;
import org.opendaylight.controller.cluster.datastore.DatastoreContextIntrospectorFactory;
import org.opendaylight.controller.cluster.datastore.DatastoreContextPropertiesUpdater;
import org.opendaylight.controller.cluster.datastore.DatastoreSnapshotRestore;
import org.opendaylight.controller.cluster.datastore.DefaultDatastoreContextIntrospectorFactory;
import org.opendaylight.controller.cluster.datastore.DefaultDatastoreSnapshotRestore;
import org.opendaylight.controller.cluster.datastore.DistributedDataStoreFactory;
import org.opendaylight.controller.cluster.datastore.config.ConfigurationImpl;
import org.opendaylight.controller.cluster.datastore.config.FileModuleShardConfigProvider;
import org.opendaylight.controller.cluster.datastore.config.ModuleShardConfigProvider;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.netconf.dagger.springboot.config.ConfigLoader;
import org.opendaylight.odlparent.dagger.ResourceSupport;
import org.opendaylight.raft.spi.RaftPolicyResolver;
import org.opendaylight.yangtools.binding.data.codec.api.BindingNormalizedNodeSerializer;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.context.properties.bind.Name;

/**
 * A Dagger module providing {@code sal-distributed-datastore} services.
 */
@Module(includes = {
    AkkaConfigurationReaderModule.class
})
@DoNotMock
@NonNullByDefault
public interface SalDistributedDatastoreModule {
    Path STATE_DIR = Path.of("state");

    @Provides
    @Singleton
    static @ConfigDataStore AbstractDataStore configDatastore(final DOMSchemaService schemaService,
            final DatastoreContextIntrospectorFactory introspectorFactory,
            final DatastoreSnapshotRestore snapshotRestore, final ActorSystemProvider actorSystemProvider,
            final ModuleShardConfigProvider configProvider, final ConfigLoader configLoader,
            final ResourceSupport resourceSupport) {
        final var properties = configLoader.getConfigPropertiesMap(
            Path.of("org.opendaylight.controller.cluster.datastore.cfg"));
        final var introspector = introspectorFactory.newInstance(LogicalDatastoreType.CONFIGURATION, properties);
        final var updater = new DatastoreContextPropertiesUpdater(introspector, properties);
        resourceSupport.register(updater);
        final var instance = DistributedDataStoreFactory.createInstance(STATE_DIR, schemaService,
            introspector.getContext(), snapshotRestore, actorSystemProvider, introspector, updater,
            new ConfigurationImpl(configProvider));
        resourceSupport.register(instance);
        return instance;
    }

    @Provides
    @Singleton
    static @OperationalDataStore AbstractDataStore operationalDatastore(final DOMSchemaService schemaService,
            final DatastoreContextIntrospectorFactory introspectorFactory,
            final DatastoreSnapshotRestore snapshotRestore, final ActorSystemProvider actorSystemProvider,
            final ModuleShardConfigProvider configProvider, final ConfigLoader configLoader,
            final ResourceSupport resourceSupport) {
        final var properties = configLoader.getConfigPropertiesMap(
            Path.of("org.opendaylight.controller.cluster.datastore.cfg"));
        final var introspector = introspectorFactory.newInstance(LogicalDatastoreType.OPERATIONAL, properties);
        final var updater = new DatastoreContextPropertiesUpdater(introspector, properties);
        resourceSupport.register(updater);
        final var instance = DistributedDataStoreFactory.createInstance(STATE_DIR, schemaService,
            introspector.getContext(), snapshotRestore, actorSystemProvider, introspector, updater,
            new ConfigurationImpl(configProvider));
        resourceSupport.register(instance);
        return instance;
    }

    @Provides
    @Singleton
    static ModuleShardConfigProvider moduleShardConfigProvider() {
        return new FileModuleShardConfigProvider();
    }

    @Provides
    @Singleton
    static DatastoreSnapshotRestore datastoreSnapshotRestore() {
        return new DefaultDatastoreSnapshotRestore();
    }

    @Provides
    @Singleton
    static ActorSystemProvider actorSystemProvider(final AkkaConfigurationReader reader,
            final ResourceSupport resourceSupport) {
        final var akkaConfig = AkkaConfigFactory.createAkkaConfig(reader);
        final var actorSystemProvider = new ActorSystemProviderImpl(AkkaConfigurationReader.class.getClassLoader(),
            QuarantinedMonitorActor.props(() -> { }), akkaConfig);
        resourceSupport.register(actorSystemProvider);
        return actorSystemProvider;
    }

    @Provides
    @Singleton
    static RaftPolicyResolver raftPolicyResolver() {
        return new DaggerRaftPolicyResolver();
    }

    @Provides
    @Singleton
    static DatastoreContextIntrospectorFactory introspectorFactory(final RaftPolicyResolver raftPolicyResolver,
            final BindingNormalizedNodeSerializer serializer) {
        return new DefaultDatastoreContextIntrospectorFactory(raftPolicyResolver, serializer);
    }

    @Provides
    @Singleton
    static DataBrokerCommitExecutor dataBrokerCommitExecutor(final ConfigLoader configLoader) {
        final var config = configLoader.getConfig(DataBrokerCommitExecutorConfig.class, "",
            Path.of("org.opendaylight.controller.cluster.datastore.broker.cfg"));
        return new DataBrokerCommitExecutor(config);
    }

    @Provides
    @Singleton
    static DOMDataBroker concurentDomDataBroker(final DataBrokerCommitExecutor executor,
            final @ConfigDataStore AbstractDataStore config, final @OperationalDataStore AbstractDataStore oper,
            final ResourceSupport resourceSupport) {
        final var concurrentDOMDataBroker = new ConcurrentDOMDataBroker(executor, config, oper);
        resourceSupport.register(concurrentDOMDataBroker);
        return concurrentDOMDataBroker;
    }

    @ConfigurationProperties
    class DataBrokerCommitExecutorConfig implements DataBrokerCommitExecutor.Config {
        private int callbackQueueSize = 1000;
        private int callbackPoolSize = 20;

        @ConstructorBinding
        public DataBrokerCommitExecutorConfig(
                @Name("max-data-broker-future-callback-queue-size") @DefaultValue("1000") int callbackQueueSize,
                @Name("max-data-broker-future-callback-pool-size") @DefaultValue("20") int callbackPoolSize) {
            this.callbackQueueSize = callbackQueueSize;
            this.callbackPoolSize = callbackPoolSize;
        }

        @Override
        public int callbackQueueSize() {
            return callbackQueueSize;
        }

        @Override
        public int callbackPoolSize() {
            return callbackPoolSize;
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return DataBrokerCommitExecutor.Config.class;
        }
    }

    @Qualifier
    @Retention(RUNTIME)
    @interface ConfigDataStore {}

    @Qualifier
    @Retention(RUNTIME)
    @interface OperationalDataStore {}
}
