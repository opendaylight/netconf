/*
 * Copyright (c) 2017 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.sal;

import java.util.Collection;
import org.opendaylight.mdsal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.DataObjectModification;
import org.opendaylight.mdsal.binding.api.DataTreeIdentifier;
import org.opendaylight.mdsal.binding.api.DataTreeModification;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netconf.nativ.netconf.communicator.protocols.ssh.NativeNetconfKeystore;
import org.opendaylight.netconf.nativ.netconf.communicator.protocols.ssh.NativeNetconfKeystoreImpl.KeyStoreUpdateStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.Keystore;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017._private.keys.PrivateKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.keystore.entry.KeyCredential;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.trusted.certificates.TrustedCertificate;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetconfKeystoreAdapter implements ClusteredDataTreeChangeListener<Keystore> {

    private static final Logger LOG = LoggerFactory.getLogger(NetconfKeystoreAdapter.class);

    private final InstanceIdentifier<Keystore> keystoreIid = InstanceIdentifier.create(Keystore.class);

    private final NativeNetconfKeystore keystore;

    public NetconfKeystoreAdapter(final DataBroker dataBroker, NativeNetconfKeystore keystore) {
        this.keystore = keystore;
        dataBroker.registerDataTreeChangeListener(DataTreeIdentifier.create(LogicalDatastoreType.CONFIGURATION,
            keystoreIid), this);
    }

    @Override
    public void onDataTreeChanged(final Collection<DataTreeModification<Keystore>> changes) {
        LOG.debug("Keystore updated: {}", changes);

        for (final DataTreeModification<Keystore> change : changes) {
            final DataObjectModification<Keystore> rootNode = change.getRootNode();

            for (final DataObjectModification<? extends DataObject> changedChild : rootNode.getModifiedChildren()) {
                if (changedChild.getDataType().equals(KeyCredential.class)) {
                    keystore.updateKeyCredentials(rootNode.getDataAfter());
                } else if (changedChild.getDataType().equals(PrivateKey.class)) {
                    onPrivateKeyChanged((DataObjectModification<PrivateKey>)changedChild);
                } else if (changedChild.getDataType().equals(TrustedCertificate.class)) {
                    onTrustedCertificateChanged((DataObjectModification<TrustedCertificate>)changedChild);
                }

            }
        }
    }

    private void onPrivateKeyChanged(final DataObjectModification<PrivateKey> objectModification) {
        switch (objectModification.getModificationType()) {
            case SUBTREE_MODIFIED:
            case WRITE:
                keystore.onPrivateKeyChanged(objectModification.getDataAfter(),
                        KeyStoreUpdateStatus.PUT);
                break;
            case DELETE:
                keystore.onPrivateKeyChanged(objectModification.getDataBefore(),
                        KeyStoreUpdateStatus.DELETE);
                break;
            default:
                break;
        }
    }

    private void onTrustedCertificateChanged(final DataObjectModification<TrustedCertificate> objectModification) {
        switch (objectModification.getModificationType()) {
            case SUBTREE_MODIFIED:
            case WRITE:
                keystore.onTrustedCertificateChanged(objectModification.getDataAfter(),
                        KeyStoreUpdateStatus.PUT);
                break;
            case DELETE:
                keystore.onTrustedCertificateChanged(objectModification.getDataBefore(),
                        KeyStoreUpdateStatus.DELETE);
                break;
            default:
                break;
        }
    }
}
