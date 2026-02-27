/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.dagger.mdsal;

import static java.util.Objects.requireNonNull;

import com.google.errorprone.annotations.DoNotMock;
import dagger.Module;
import dagger.Provides;
import jakarta.inject.Singleton;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.mdsal.binding.api.ActionProviderService;
import org.opendaylight.mdsal.binding.api.ActionService;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.MountPointService;
import org.opendaylight.mdsal.binding.api.NotificationPublishService;
import org.opendaylight.mdsal.binding.api.NotificationService;
import org.opendaylight.mdsal.binding.api.RpcProviderService;
import org.opendaylight.mdsal.binding.api.RpcService;
import org.opendaylight.mdsal.binding.dom.adapter.AdapterContext;
import org.opendaylight.mdsal.binding.dom.adapter.BindingAdapterFactory;
import org.opendaylight.mdsal.binding.dom.adapter.ConstantAdapterContext;
import org.opendaylight.mdsal.dom.api.DOMActionProviderService;
import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMNotificationPublishService;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.mdsal.dom.api.DOMRpcProviderService;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.netconf.dagger.mdsal.MdsalQualifiers.ClassPathYangModules;
import org.opendaylight.netconf.dagger.mdsal.MdsalQualifiers.SchemaServiceContext;
import org.opendaylight.odlparent.dagger.ResourceSupport;
import org.opendaylight.yangtools.binding.data.codec.api.BindingNormalizedNodeSerializer;
import org.opendaylight.yangtools.binding.data.codec.impl.BindingCodecContext;
import org.opendaylight.yangtools.binding.generator.dagger.BindingRuntimeGeneratorModule;
import org.opendaylight.yangtools.binding.meta.YangModelBindingProvider;
import org.opendaylight.yangtools.binding.meta.YangModuleInfo;
import org.opendaylight.yangtools.binding.runtime.api.BindingRuntimeContext;
import org.opendaylight.yangtools.binding.runtime.api.BindingRuntimeGenerator;
import org.opendaylight.yangtools.binding.runtime.api.BindingRuntimeTypes;
import org.opendaylight.yangtools.binding.runtime.api.DefaultBindingRuntimeContext;
import org.opendaylight.yangtools.binding.runtime.api.ModuleInfoSnapshot;
import org.opendaylight.yangtools.binding.runtime.spi.ModuleInfoSnapshotResolver;
import org.opendaylight.yangtools.odlext.parser.dagger.OdlCodegenModule;
import org.opendaylight.yangtools.odlext.parser.dagger.YangExtModule;
import org.opendaylight.yangtools.openconfig.parser.dagger.OpenConfigModule;
import org.opendaylight.yangtools.rfc6241.parser.dagger.Rfc6241Module;
import org.opendaylight.yangtools.rfc6536.parser.dagger.Rfc6536Module;
import org.opendaylight.yangtools.rfc6643.parser.dagger.Rfc6643Module;
import org.opendaylight.yangtools.rfc7952.parser.dagger.Rfc7952Module;
import org.opendaylight.yangtools.rfc8040.parser.dagger.Rfc8040Module;
import org.opendaylight.yangtools.rfc8528.parser.dagger.Rfc8528Module;
import org.opendaylight.yangtools.rfc8639.parser.dagger.Rfc8639Module;
import org.opendaylight.yangtools.rfc8819.parser.dagger.Rfc8819Module;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.parser.api.YangParserFactory;
import org.opendaylight.yangtools.yang.parser.dagger.YangLibResolverModule;
import org.opendaylight.yangtools.yang.parser.dagger.YangParserFactoryModule;
import org.opendaylight.yangtools.yang.xpath.dagger.YangXPathParserFactoryModule;

/**
 * A Dagger module providing {@code mdsal-binding-dom-adapter} services.
 */
@Module(includes = {
    BindingRuntimeGeneratorModule.class,
    YangXPathParserFactoryModule.class,
    YangParserFactoryModule.class,
    YangLibResolverModule.class,
    Rfc6241Module.class,
    Rfc6536Module.class,
    Rfc6643Module.class,
    Rfc7952Module.class,
    Rfc8040Module.class,
    Rfc8528Module.class,
    Rfc8639Module.class,
    Rfc8819Module.class,
    OdlCodegenModule.class,
    YangExtModule.class,
    OpenConfigModule.class,
})
@DoNotMock
@NonNullByDefault
public interface MdsalBindingDomAdapterModule {

    @Provides
    @Singleton
    static DataBroker dataBroker(final DOMDataBroker domDataBroker, final BindingAdapterFactory adapterFactory) {
        return adapterFactory.createDataBroker(domDataBroker);
    }

    @Provides
    @Singleton
    static MountPointService mountPointService(final DOMMountPointService domMountPointService,
            final BindingAdapterFactory adapterFactory) {
        return adapterFactory.createMountPointService(domMountPointService);
    }

    @Provides
    @Singleton
    static NotificationService notificationService(final DOMNotificationService domNotificationService,
            final BindingAdapterFactory adapterFactory) {
        return adapterFactory.createNotificationService(domNotificationService);
    }

    @Provides
    @Singleton
    static NotificationPublishService notificationPublishService(
            final DOMNotificationPublishService actionProviderService, final BindingAdapterFactory adapterFactory) {
        return adapterFactory.createNotificationPublishService(actionProviderService);
    }

    @Provides
    @Singleton
    static RpcService rpcService(final DOMRpcService domRpcService, final BindingAdapterFactory adapterFactory) {
        return adapterFactory.createRpcService(domRpcService);
    }

    @Provides
    @Singleton
    static RpcProviderService rpcProviderService(final DOMRpcProviderService domRpcProviderService,
            final BindingAdapterFactory adapterFactory) {
        return adapterFactory.createRpcProviderService(domRpcProviderService);
    }

    @Provides
    @Singleton
    static ActionService actionService(final DOMActionService actionService,
            final BindingAdapterFactory adapterFactory) {
        return adapterFactory.createActionService(actionService);
    }

    @Provides
    @Singleton
    static ActionProviderService actionProviderService(final DOMActionProviderService actionProviderService,
            final BindingAdapterFactory adapterFactory) {
        return adapterFactory.createActionProviderService(actionProviderService);
    }

    @Provides
    @Singleton
    static BindingAdapterFactory adapterFactory(final AdapterContext codec) {
        return new BindingAdapterFactory(codec);
    }

    @Provides
    @Singleton
    static AdapterContext adapterContext(final BindingCodecContext bindingCodecContext) {
        return new ConstantAdapterContext(bindingCodecContext);
    }

    @Provides
    @Singleton
    static BindingNormalizedNodeSerializer bindingNormalizedNodeSerializer(final AdapterContext codec) {
        return codec.currentSerializer();
    }

    @Provides
    @Singleton
    static BindingCodecContext bidingCodecContext(final BindingRuntimeContext bindingRuntimeContext) {
        return new BindingCodecContext(bindingRuntimeContext);
    }

    @Provides
    @Singleton
    static BindingRuntimeContext bindingRuntimeContext(final BindingRuntimeTypes runtimeTypes,
            final ModuleInfoSnapshot moduleInfos) {
        return new DefaultBindingRuntimeContext(runtimeTypes, moduleInfos);
    }

    @Provides
    @Singleton
    static BindingRuntimeTypes bindingRuntimeTypes(final BindingRuntimeGenerator bindingRuntimeGenerator,
            final ModuleInfoSnapshot moduleInfos) {
        return bindingRuntimeGenerator.generateTypeMapping(moduleInfos.modelContext());
    }

    @Provides
    @Singleton
    static ModuleInfoSnapshot getModuleInfos(@ClassPathYangModules final Set<YangModuleInfo> modelSet,
            final YangParserFactory yangParserFactory, final ResourceSupport resourceSupport) {
        final var snapshotResolver = new ModuleInfoSnapshotResolver("binding-dom-adapter", yangParserFactory);
        final var registrations = snapshotResolver.registerModuleInfos(modelSet);
        registrations.forEach(resourceSupport::register);
        return snapshotResolver.takeSnapshot();
    }

    @Provides
    @Singleton
    static @SchemaServiceContext EffectiveModelContext getModelContext(final ModuleInfoSnapshot moduleInfoSnapshot) {
        return moduleInfoSnapshot.modelContext();
    }

    @Provides
    static @ClassPathYangModules Set<YangModuleInfo> getModelsFromClassPath() {
        final var yangProviderLoader = ServiceLoader.load(YangModelBindingProvider.class);
        requireNonNull(yangProviderLoader);
        return yangProviderLoader.stream()
            .map(ServiceLoader.Provider::get)
            .map(YangModelBindingProvider::getModuleInfo)
            .collect(Collectors.toSet());
    }
}
