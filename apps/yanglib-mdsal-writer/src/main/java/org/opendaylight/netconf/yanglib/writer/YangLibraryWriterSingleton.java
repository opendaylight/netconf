/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.yanglib.writer;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.Optional;
import java.util.Set;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.singleton.api.ClusterSingletonService;
import org.opendaylight.mdsal.singleton.api.ClusterSingletonServiceProvider;
import org.opendaylight.mdsal.singleton.api.ServiceGroupIdentifier;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.common.Empty;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link ClusterSingletonService} dealing with {@link YangLibraryWriter} lifecycle.
 */
@Singleton
@Component(service = { }, configurationPid = "org.opendaylight.netconf.yanglib")
@Designate(ocd = YangLibraryWriterSingleton.Configuration.class)
public final class YangLibraryWriterSingleton implements ClusterSingletonService, AutoCloseable {
    @ObjectClassDefinition
    public @interface Configuration {
        @AttributeDefinition(description = "Maintain RFC7895-compatible modules-state container. Defaults to true.")
        boolean write$_$legacy() default true;
    }

    private static final Logger LOG = LoggerFactory.getLogger(YangLibraryWriterSingleton.class);
    private static final @NonNull ServiceGroupIdentifier SGI = new ServiceGroupIdentifier("yanglib-mdsal-writer");

    private final @NonNull YangLibrarySchemaSourceUrlProvider urlProvider;
    private final DOMSchemaService schemaService;
    private final DataBroker dataBroker;
    private final boolean writeLegacy;

    private YangLibraryWriter instance;
    private Registration reg;

    public YangLibraryWriterSingleton(final ClusterSingletonServiceProvider cssProvider,
            final DOMSchemaService schemaService, final DataBroker dataBroker,
            final boolean writeLegacy, final @Nullable YangLibrarySchemaSourceUrlProvider urlProvider) {
        this.schemaService = requireNonNull(schemaService);
        this.dataBroker = requireNonNull(dataBroker);
        this.urlProvider = orEmptyProvider(urlProvider);
        this.writeLegacy = writeLegacy;
        reg = cssProvider.registerClusterSingletonService(this);
        LOG.info("ietf-yang-library writer registered");
    }

    public YangLibraryWriterSingleton(final ClusterSingletonServiceProvider cssProvider,
            final DOMSchemaService schemaService, final DataBroker dataBroker, final boolean writeLegacy) {
        this(cssProvider, schemaService, dataBroker, writeLegacy, null);
    }

    @Inject
    public YangLibraryWriterSingleton(final ClusterSingletonServiceProvider cssProvider,
            final DOMSchemaService schemaService, final DataBroker dataBroker,
            final Optional<YangLibrarySchemaSourceUrlProvider> urlProvider) {
        this(cssProvider, schemaService, dataBroker, true, urlProvider.orElse(null));
    }

    @Activate
    public YangLibraryWriterSingleton(@Reference final ClusterSingletonServiceProvider cssProvider,
            @Reference final DOMSchemaService schemaService, @Reference final DataBroker dataBroker,
            @Reference(cardinality = ReferenceCardinality.OPTIONAL, policyOption = ReferencePolicyOption.GREEDY)
                final @Nullable YangLibrarySchemaSourceUrlProvider urlProvider,
            final Configuration configuration) {
        this(cssProvider, schemaService, dataBroker, configuration.write$_$legacy(), urlProvider);
    }


    @Deactivate
    @PreDestroy
    @Override
    public synchronized void close() {
        if (reg == null) {
            return;
        }

        // Note: CSS is providing lifecycle management, hence it is safe to not wait for shutdown
        reg.close();
        reg = null;
        LOG.info("ietf-yang-library writer unregistered");
    }

    private static @NonNull YangLibrarySchemaSourceUrlProvider orEmptyProvider(
            final @Nullable YangLibrarySchemaSourceUrlProvider urlProvider) {
        return urlProvider != null ? urlProvider : emptyProvider();
    }

    @VisibleForTesting
    static @NonNull YangLibrarySchemaSourceUrlProvider emptyProvider() {
        return (moduleSetName, moduleName, revision) -> Set.of();
    }

    @Override
    public ServiceGroupIdentifier getIdentifier() {
        return SGI;
    }

    @Override
    public synchronized void instantiateServiceInstance() {
        instance = new YangLibraryWriter(schemaService, dataBroker, writeLegacy, urlProvider);
    }

    @Override
    public synchronized ListenableFuture<Empty> closeServiceInstance() {
        final var local = instance;
        if (local == null) {
            return Empty.immediateFuture();
        }
        instance = null;
        return local.shutdown();
    }
}
