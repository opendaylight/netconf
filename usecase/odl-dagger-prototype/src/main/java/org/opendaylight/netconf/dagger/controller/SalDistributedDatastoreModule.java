/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.dagger.controller;

import com.google.errorprone.annotations.DoNotMock;
import dagger.Module;
import dagger.Provides;
import jakarta.inject.Singleton;
import java.lang.annotation.Annotation;
import java.nio.file.Path;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.controller.cluster.databroker.ConcurrentDOMDataBroker;
import org.opendaylight.controller.cluster.databroker.DataBrokerCommitExecutor;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.spi.store.DOMStore;
import org.opendaylight.netconf.dagger.mdsal.MdsalQualifiers.ConfigDataStore;
import org.opendaylight.netconf.dagger.mdsal.MdsalQualifiers.OperationalDataStore;
import org.opendaylight.netconf.dagger.springboot.config.ConfigLoader;
import org.opendaylight.odlparent.dagger.ResourceSupport;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.context.properties.bind.Name;

/**
 * A Dagger module providing {@code sal-distributed-datastore} services.
 */
@Module
@DoNotMock
@NonNullByDefault
public interface SalDistributedDatastoreModule {

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
            final @ConfigDataStore DOMStore config, final @OperationalDataStore DOMStore oper,
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
}
