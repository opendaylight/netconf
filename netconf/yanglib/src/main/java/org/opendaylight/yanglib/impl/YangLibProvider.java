/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.yanglib.impl;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker;
import org.opendaylight.controller.sal.binding.api.BindingAwareProvider;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160409.ModulesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160409.ModulesStateBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160409.OptionalRevision;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160409.RevisionIdentifier;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160409.module.list.Module;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160409.module.list.ModuleBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160409.module.list.ModuleKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.YangIdentifier;
import org.opendaylight.yanglib.api.YangLibRestAppService;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaSourceRepresentation;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.PotentialSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaListenerRegistration;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceListener;
import org.opendaylight.yangtools.yang.parser.repo.SharedSchemaRepository;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Listens on new schema sources registered event. For each new source
 * registered generates URL representing its schema source and write this URL
 * along with source identifier to
 * ietf-netconf-yang-library/modules-state/module list.
 */
public class YangLibProvider implements BindingAwareProvider, AutoCloseable, SchemaSourceListener{
    private static final Logger LOG = LoggerFactory.getLogger(YangLibProvider.class);

    private static final OptionalRevision NO_REVISION = new OptionalRevision("");
    private static final Predicate<PotentialSchemaSource<?>> YANG_SCHEMA_SOURCE =
            input -> YangTextSchemaSource.class.isAssignableFrom(input.getRepresentation());

    protected DataBroker dataBroker;
    protected SchemaListenerRegistration schemaListenerRegistration;
    protected final SharedSchemaRepository schemaRepository;
    private final String bindingAddress;
    private final long bindingPort;

    public YangLibProvider(final SharedSchemaRepository schemaRepository,
                           final String bindingAddress, final long bindingPort) {
        this.schemaRepository = schemaRepository;
        this.bindingAddress = bindingAddress;
        this.bindingPort = bindingPort;
    }

    @Override
    public void close() throws Exception {
        dataBroker = null;
        schemaListenerRegistration.close();
    }

    @Override
    public void onSessionInitiated(final BindingAwareBroker.ProviderContext providerContext) {
        this.dataBroker = providerContext.getSALService(DataBroker.class);
        schemaListenerRegistration = schemaRepository.registerSchemaSourceListener(this);
        getObjectFromBundleContext(YangLibRestAppService.class, YangLibRestAppService.class.getName())
        .getYangLibService().setSchemaRepository(schemaRepository);
    }

    @Override
    public void schemaSourceEncountered(final SchemaSourceRepresentation source) {
        // NOOP
    }

    @Override
    public void schemaSourceRegistered(final Iterable<PotentialSchemaSource<?>> sources) {
        final List<Module> newModules = new ArrayList<>();

        for(PotentialSchemaSource<?> potentialYangSource : Iterables.filter(sources, YANG_SCHEMA_SOURCE)) {
            final YangIdentifier moduleName = new YangIdentifier(potentialYangSource.getSourceIdentifier().getName());

            final OptionalRevision moduleRevision = getRevisionForModule(potentialYangSource.getSourceIdentifier());

            final Module newModule = new ModuleBuilder()
                    .setName(moduleName)
                    .setRevision(moduleRevision)
                    .setSchema(getUrlForModule(potentialYangSource.getSourceIdentifier()))
                    .build();

            newModules.add(newModule);
        }

        if(newModules.isEmpty()) {
            // If no new yang modules then do nothing
            return;
        }

        WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
        tx.merge(LogicalDatastoreType.OPERATIONAL, InstanceIdentifier.create(ModulesState.class),
                new ModulesStateBuilder().setModule(newModules).build());

        Futures.addCallback(tx.submit(), new FutureCallback<Void>() {
            @Override
            public void onSuccess(@Nullable final Void result) {
                LOG.debug("Modules state successfully populated with new modules");
            }

            @Override
            public void onFailure(final Throwable t) {
                LOG.warn("Unable to update modules state", t);
            }
        });
    }

    @Override
    public void schemaSourceUnregistered(final PotentialSchemaSource<?> source) {
        if(!YANG_SCHEMA_SOURCE.apply(source)) {
            // if representation of potential schema source is not yang text schema source do nothing
            // we do not want to delete this module entry from module list
            return;
        }

        WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
        tx.delete(LogicalDatastoreType.OPERATIONAL,
                InstanceIdentifier.create(ModulesState.class)
                        .child(Module.class,
                                new ModuleKey(
                                        new YangIdentifier(source.getSourceIdentifier().getName()),
                                        getRevisionForModule(source.getSourceIdentifier()))));

        Futures.addCallback(tx.submit(), new FutureCallback<Void>() {
            @Override
            public void onSuccess(@Nullable final Void result) {
                LOG.debug("Modules state successfully updated.");
            }

            @Override
            public void onFailure(final Throwable t) {
                LOG.warn("Unable to update modules state", t);
            }
        });
    }



    private Uri getUrlForModule(final SourceIdentifier sourceIdentifier) {
        return new Uri("http://" + bindingAddress + ':' + bindingPort + "/yanglib/schemas/"
                + sourceIdentifier.getName() + '/' + revString(sourceIdentifier));
    }

    private static String revString(final SourceIdentifier id) {
        final String rev = id.getRevision();
        return rev == null || SourceIdentifier.NOT_PRESENT_FORMATTED_REVISION.equals(rev) ? "" : rev;
    }

    private static OptionalRevision getRevisionForModule(final SourceIdentifier sourceIdentifier) {
        final String rev = sourceIdentifier.getRevision();
        return rev == null || SourceIdentifier.NOT_PRESENT_FORMATTED_REVISION.equals(rev) ? NO_REVISION
                : new OptionalRevision(new RevisionIdentifier(rev));
    }

    private <T> T getObjectFromBundleContext(final Class<T> type, final String serviceRefName) {
        final BundleContext bundleContext = FrameworkUtil.getBundle(getClass()).getBundleContext();
        final ServiceReference<?> serviceReference = bundleContext.getServiceReference(serviceRefName);
        return (T) bundleContext.getService(serviceReference);
    }
}
