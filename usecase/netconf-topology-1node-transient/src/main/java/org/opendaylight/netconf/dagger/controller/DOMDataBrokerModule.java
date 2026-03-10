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
import java.util.Map;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.controller.cluster.databroker.ConcurrentDOMDataBroker;
import org.opendaylight.controller.cluster.databroker.DataBrokerCommitExecutor;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.spi.store.DOMStore;
import org.opendaylight.netconf.dagger.springboot.config.ConfigLoader;
import org.opendaylight.odlparent.dagger.ResourceSupport;
import org.opendaylight.yangtools.util.concurrent.SpecialExecutors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * A Dagger module providing {@link DOMDataBroker} service.
 */
@Module
@DoNotMock
@NonNullByDefault
public interface DOMDataBrokerModule {

    @Provides
    @Singleton
    static DOMDataBroker concurentDomDataBroker(final Map<LogicalDatastoreType, DOMStore> datastores,
            final ConfigLoader configLoader, final ResourceSupport resourceSupport) {
        final var config = configLoader.getConfig(DataBrokerCommitExecutorConfig.class,
            "odl.netconf.prototype.cluster-datastore-broker", Path.of("application.yaml"));
        final var executorService = SpecialExecutors.newBlockingBoundedCachedThreadPool(
            config.maxDataBrokerFutureCallbackPoolSize, config.maxDataBrokerFutureCallbackQueueSize,
            "CommitFutures", ConcurrentDOMDataBroker.class);
        resourceSupport.register(executorService);

        final var concurrentDOMDataBroker = new ConcurrentDOMDataBroker(datastores, executorService);
        resourceSupport.register(concurrentDOMDataBroker);
        return concurrentDOMDataBroker;
    }

    @ConfigurationProperties("odl.netconf.prototype.cluster-datastore-broker")
    class DataBrokerCommitExecutorConfig implements DataBrokerCommitExecutor.Config {
        /**
         * The maximum queue size for the data broker future callback executor.
         */
        private final int maxDataBrokerFutureCallbackQueueSize;
        /**
         * The maximum thread pool size for the data broker future callback executor.
         */
        private final int maxDataBrokerFutureCallbackPoolSize;

        @ConstructorBinding
        public DataBrokerCommitExecutorConfig(@DefaultValue("1000") int maxDataBrokerFutureCallbackQueueSize,
                @DefaultValue("20") int maxDataBrokerFutureCallbackPoolSize) {
            this.maxDataBrokerFutureCallbackQueueSize = maxDataBrokerFutureCallbackQueueSize;
            this.maxDataBrokerFutureCallbackPoolSize = maxDataBrokerFutureCallbackPoolSize;
        }

        @Override
        public int callbackQueueSize() {
            return maxDataBrokerFutureCallbackQueueSize;
        }

        @Override
        public int callbackPoolSize() {
            return maxDataBrokerFutureCallbackPoolSize;
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return DataBrokerCommitExecutor.Config.class;
        }
    }
}
