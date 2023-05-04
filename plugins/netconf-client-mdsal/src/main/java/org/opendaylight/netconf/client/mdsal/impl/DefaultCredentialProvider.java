/*
 * Copyright (c) 2017 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.impl;

import com.google.common.collect.Iterables;
import java.util.Collection;
import java.util.Map;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.DataTreeIdentifier;
import org.opendaylight.mdsal.binding.api.DataTreeModification;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netconf.client.mdsal.api.CredentialProvider;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.Keystore;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.keystore.entry.KeyCredential;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.keystore.entry.KeyCredentialKey;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@Component(service = CredentialProvider.class)
public final class DefaultCredentialProvider
        implements CredentialProvider, ClusteredDataTreeChangeListener<Keystore>, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultCredentialProvider.class);

    private final @NonNull Registration reg;

    private volatile @NonNull Map<KeyCredentialKey, KeyCredential> credentials = Map.of();

    @Inject
    @Activate
    public DefaultCredentialProvider(@Reference final DataBroker dataBroker) {
        reg = dataBroker.registerDataTreeChangeListener(
            DataTreeIdentifier.create(LogicalDatastoreType.CONFIGURATION, InstanceIdentifier.create(Keystore.class)),
            this);
    }

    @Deactivate
    @PreDestroy
    @Override
    public void close() {
        reg.close();
    }

    @Override
    public KeyCredential credentialForId(final String id) {
        return credentials.get(new KeyCredentialKey(id));
    }

    @Override
    public void onDataTreeChanged(final Collection<DataTreeModification<Keystore>> changes) {
        final var keystore = Iterables.getLast(changes).getRootNode().getDataAfter();
        final var newCredentials = keystore != null ? keystore.nonnullKeyCredential()
            : Map.<KeyCredentialKey, KeyCredential>of();
        LOG.debug("Updating to {} credentials", newCredentials.size());
        credentials = newCredentials;
    }
}
