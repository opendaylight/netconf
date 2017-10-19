/*
 * Copyright (c) 2017 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.connect.netconf.sal;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nonnull;
import org.opendaylight.controller.md.sal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.Keystore;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.keypair.item.Keypair;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetconfKeystoreAdapter implements ClusteredDataTreeChangeListener<Keystore> {

    private static final Logger LOG = LoggerFactory.getLogger(NetconfKeystoreAdapter.class);

    private final InstanceIdentifier<Keystore> keystoreIid = InstanceIdentifier.create(Keystore.class);

    private final DataBroker dataBroker;
    private final Map<String, Keypair> pairs = Collections.synchronizedMap(new HashMap<>());

    public NetconfKeystoreAdapter(final DataBroker dataBroker) {
        this.dataBroker = dataBroker;

        dataBroker.registerDataTreeChangeListener(
                new DataTreeIdentifier<>(LogicalDatastoreType.OPERATIONAL, keystoreIid), this);
    }

    public Optional<Keypair> getKeypairFromId(final String keyId) {
        final Keypair keypair = pairs.get(keyId);
        return Optional.ofNullable(keypair);
    }

    @Override
    public void onDataTreeChanged(@Nonnull final Collection<DataTreeModification<Keystore>> changes) {
        LOG.debug("Keystore updated: {}", changes);
        final Keystore dataAfter = changes.iterator().next().getRootNode().getDataAfter();

        pairs.clear();
        if (dataAfter != null) {
            dataAfter.getKeypair().forEach(pair -> pairs.put(pair.getKey().getKeyId(), pair));
        }
    }
}
