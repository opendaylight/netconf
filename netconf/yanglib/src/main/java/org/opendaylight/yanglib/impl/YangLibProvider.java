/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yanglib.impl;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.io.ByteStreams;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.LegacyRevisionUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.ModulesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.ModulesStateBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.module.list.Module;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.module.list.ModuleBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.module.list.ModuleKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.YangIdentifier;
import org.opendaylight.yanglib.api.YangLibService;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaSourceRepresentation;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.fs.FilesystemSchemaSourceCache;
import org.opendaylight.yangtools.yang.model.repo.spi.PotentialSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaListenerRegistration;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceListener;
import org.opendaylight.yangtools.yang.parser.api.YangParserFactory;
import org.opendaylight.yangtools.yang.parser.repo.SharedSchemaRepository;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listens on new schema sources registered event. For each new source
 * registered generates URL representing its schema source and write this URL
 * along with source identifier to
 * ietf-netconf-yang-library/modules-state/module list.
 */
@Singleton
@Component(service = {SchemaSourceListener.class, YangLibService.class})
@Designate(ocd = YangLibProvider.Configuration.class)
public final class YangLibProvider implements AutoCloseable, SchemaSourceListener, YangLibService {
    @ObjectClassDefinition
    public @interface Configuration {
        @AttributeDefinition(description = "Local filesystem folder to use as cache + to load yang models from")
        String cacheFolder();
        @AttributeDefinition(description = "Binding address is necessary for generating proper URLS (accessible from "
                + "the outside world) for models present directly in the library")
        String bindingAddress();
        @AttributeDefinition(description = "binding port is necessary for generating proper URLS (accessible from the "
                + "outside world) for models present directly in the library")
        int bindingPort();
    }

    private static final Logger LOG = LoggerFactory.getLogger(YangLibProvider.class);

    private static final Predicate<PotentialSchemaSource<?>> YANG_SCHEMA_SOURCE =
        input -> YangTextSchemaSource.class.isAssignableFrom(input.getRepresentation());

    private final DataBroker dataBroker;
    private final String cacheFolder;
    private final String bindingAddress;
    private final Uint32 bindingPort;
    private final SharedSchemaRepository schemaRepository;
    private SchemaListenerRegistration schemaListenerRegistration;

    @Activate
    public YangLibProvider(@Reference final @NonNull DataBroker dataBroker,
            @Reference final @NonNull YangParserFactory parserFactory, final @NonNull Configuration configuration) {
        this(dataBroker, parserFactory, configuration.cacheFolder(), configuration.bindingAddress(),
                Uint32.valueOf(configuration.bindingPort()));
    }

    @Inject
    public YangLibProvider(final @NonNull DataBroker dataBroker, final @NonNull YangParserFactory parserFactory,
                           final @NonNull String cacheFolder, final @NonNull String bindingAddress,
                           final @NonNull Uint32 bindingPort) {
        this.cacheFolder = cacheFolder;
        this.bindingAddress = bindingAddress;
        this.bindingPort = bindingPort;
        this.dataBroker = requireNonNull(dataBroker);
        schemaRepository = new SharedSchemaRepository("yang-library", parserFactory);
    }

    @Deactivate
    @Override
    public void close() {
        if (schemaListenerRegistration != null) {
            schemaListenerRegistration.close();
        }
    }

    @Activate
    public void init() {
        if (Strings.isNullOrEmpty(cacheFolder)) {
            LOG.info("No cache-folder set in yanglib-config - yang library services will not be available");
            return;
        }

        final File cacheFolderFile = new File(cacheFolder);
        checkArgument(cacheFolderFile.exists(), "cache-folder %s does not exist", cacheFolderFile);
        checkArgument(cacheFolderFile.isDirectory(), "cache-folder %s is not a directory", cacheFolderFile);

        final FilesystemSchemaSourceCache<YangTextSchemaSource> cache =
                new FilesystemSchemaSourceCache<>(schemaRepository, YangTextSchemaSource.class, cacheFolderFile);
        schemaRepository.registerSchemaSourceListener(cache);

        schemaListenerRegistration = schemaRepository.registerSchemaSourceListener(this);

        LOG.info("Started yang library with sources from {}", cacheFolderFile);
    }

    @Override
    public void schemaSourceEncountered(final SchemaSourceRepresentation source) {
        // NOOP
    }

    @Override
    public void schemaSourceRegistered(final Iterable<PotentialSchemaSource<?>> sources) {
        final Map<ModuleKey, Module> newModules = new HashMap<>();

        for (PotentialSchemaSource<?> potentialYangSource : Iterables.filter(sources, YANG_SCHEMA_SOURCE)) {
            final YangIdentifier moduleName =
                new YangIdentifier(potentialYangSource.getSourceIdentifier().name().getLocalName());

            final Module newModule = new ModuleBuilder()
                    .setName(moduleName)
                    .setRevision(LegacyRevisionUtils.fromYangCommon(
                        Optional.ofNullable(potentialYangSource.getSourceIdentifier().revision())))
                    .setSchema(getUrlForModule(potentialYangSource.getSourceIdentifier()))
                    .build();

            newModules.put(newModule.key(), newModule);
        }

        if (newModules.isEmpty()) {
            // If no new yang modules then do nothing
            return;
        }

        WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
        tx.merge(LogicalDatastoreType.OPERATIONAL, InstanceIdentifier.create(ModulesState.class),
                new ModulesStateBuilder().setModule(newModules).build());

        tx.commit().addCallback(new FutureCallback<CommitInfo>() {
            @Override
            public void onSuccess(final CommitInfo result) {
                LOG.debug("Modules state successfully populated with new modules");
            }

            @Override
            public void onFailure(final Throwable throwable) {
                LOG.warn("Unable to update modules state", throwable);
            }
        }, MoreExecutors.directExecutor());
    }

    @Override
    public void schemaSourceUnregistered(final PotentialSchemaSource<?> source) {
        if (!YANG_SCHEMA_SOURCE.apply(source)) {
            // if representation of potential schema source is not yang text schema source do nothing
            // we do not want to delete this module entry from module list
            return;
        }

        WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
        tx.delete(LogicalDatastoreType.OPERATIONAL, InstanceIdentifier.create(ModulesState.class)
            .child(Module.class, new ModuleKey(new YangIdentifier(source.getSourceIdentifier().name().getLocalName()),
                LegacyRevisionUtils.fromYangCommon(Optional.ofNullable(source.getSourceIdentifier().revision())))));

        tx.commit().addCallback(new FutureCallback<CommitInfo>() {
            @Override
            public void onSuccess(final CommitInfo result) {
                LOG.debug("Modules state successfully updated.");
            }

            @Override
            public void onFailure(final Throwable throwable) {
                LOG.warn("Unable to update modules state", throwable);
            }
        }, MoreExecutors.directExecutor());
    }

    @Override
    public String getSchema(final String name, final String revision) {
        LOG.debug("Attempting load for schema source {}:{}", name, revision);
        final SourceIdentifier sourceId = new SourceIdentifier(name, revision.isEmpty() ? null : revision);

        final ListenableFuture<YangTextSchemaSource> sourceFuture = schemaRepository.getSchemaSource(sourceId,
            YangTextSchemaSource.class);

        try {
            final YangTextSchemaSource source = sourceFuture.get();
            return new String(ByteStreams.toByteArray(source.openStream()), Charset.defaultCharset());
        } catch (InterruptedException | ExecutionException | IOException e) {
            throw new IllegalStateException("Unable to get schema " + sourceId, e);
        }
    }

    private Uri getUrlForModule(final SourceIdentifier sourceIdentifier) {
        return new Uri("http://" + bindingAddress + ':' + bindingPort + "/yanglib/schemas/"
                + sourceIdentifier.name().getLocalName() + '/' + revString(sourceIdentifier));
    }

    private static String revString(final SourceIdentifier id) {
        final var rev = id.revision();
        return rev != null ? rev.toString() : "";
    }
}
