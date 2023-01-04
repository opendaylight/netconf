/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.nmda.mdsal;

import static java.util.Objects.requireNonNull;

import java.security.Principal;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPointListener;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindProvider;
import org.opendaylight.restconf.nb.rfc8040.nmda.DatastoreService;
import org.opendaylight.restconf.nb.rfc8040.nmda.FragmentServices;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.datastores.rev180214.Datastore;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

/**
 * Global implementation of {@link DatastoreService} in an MD-SAL setting. Provides lazily-loaded
 * {@link FragmentServices} based on mount points registered to {@link DOMMountPointService}.
 */
@Singleton
@Component(service = DatastoreService.class)
@NonNullByDefault
public final class DOMDatastoreService implements DatastoreService, AutoCloseable, DOMMountPointListener {
    private final DatabindProvider globalDatabind;
    private final Registration reg;

    @Inject
    @Activate
    public DOMDatastoreService(@Reference final DOMMountPointService mountService,
            @Reference final DatabindProvider globalDatabind,
            @Reference final DOMDataBroker globalData, @Reference final DOMNotificationService globalNotification,
            @Reference final DOMRpcService globalRpc, @Reference final DOMActionService globalAction) {
        this.globalDatabind = requireNonNull(globalDatabind);

        // More init

        reg = mountService.registerProvisionListener(this);
    }

    @Override
    public <S extends Datastore> @Nullable FragmentServices<S> fragmentFor(final Principal principal,
            final S datastore, final ApiPath path) {
        // TODO Auto-generated method stub
        return null;
    }


    @Override
    public void onMountPointCreated(final YangInstanceIdentifier path) {
        // TODO Auto-generated method stub

    }


    @Override
    public void onMountPointRemoved(final YangInstanceIdentifier path) {
        // TODO Auto-generated method stub

    }

    @PreDestroy
    @Deactivate
    @Override
    public void close() {
        reg.close();
    }
}
