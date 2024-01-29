/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.keystore.legacy;

import static java.util.Objects.requireNonNull;

import com.google.common.base.Stopwatch;
import java.util.List;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.mdsal.binding.api.DataTreeChangeListener;
import org.opendaylight.mdsal.binding.api.DataTreeModification;
import org.opendaylight.netconf.keystore.legacy.AbstractNetconfKeystore.StateBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.Keystore;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017._private.keys.PrivateKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.trusted.certificates.TrustedCertificate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NonNullByDefault
record ConfigListener(AbstractNetconfKeystore keystore) implements DataTreeChangeListener<Keystore> {
    private static final Logger LOG = LoggerFactory.getLogger(ConfigListener.class);

    ConfigListener {
        requireNonNull(keystore);
    }

    @Override
    public void onDataTreeChanged(final List<DataTreeModification<Keystore>> changes) {
        LOG.debug("Starting update with {} changes", changes.size());
        final var sw = Stopwatch.createStarted();
        keystore.runUpdate(builder -> onDataTreeChanged(builder, changes));
        LOG.debug("Update finished in {}", sw);
    }

    @Override
    public void onInitialData() {
        keystore.runUpdate(builder -> {
            builder.privateKeys().clear();
            builder.trustedCertificates().clear();
        });
    }

    private static void onDataTreeChanged(final StateBuilder builder,
            final List<DataTreeModification<Keystore>> changes) {
        for (var change : changes) {
            LOG.debug("Processing change {}", change);
            final var rootNode = change.getRootNode();

            // Note: we are using indirection through Runnable to turn switch *statements* to *expressions*, hence we
            //       get exhaustiveness guarantees and do not have to bother with 'default' statements.
            for (var mod : rootNode.getModifiedChildren(PrivateKey.class)) {
                final Runnable update = switch (mod.modificationType()) {
                    case SUBTREE_MODIFIED, WRITE -> () -> {
                        final var privateKey = mod.dataAfter();
                        builder.privateKeys().put(privateKey.getName(), privateKey);
                    };
                    case DELETE -> () -> builder.privateKeys().remove(mod.dataBefore().getName());
                };
                update.run();
            }
            for (var mod : rootNode.getModifiedChildren(TrustedCertificate.class)) {
                final Runnable update = switch (mod.modificationType()) {
                    case SUBTREE_MODIFIED, WRITE -> () -> {
                        final var trustedCertificate = mod.dataAfter();
                        builder.trustedCertificates().put(trustedCertificate.getName(), trustedCertificate);
                    };
                    case DELETE -> () -> builder.trustedCertificates().remove(mod.dataBefore().getName());
                };
                update.run();
            }
        }
    }
}