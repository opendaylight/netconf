/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.keystore.legacy.impl;

import static java.util.Objects.requireNonNull;

import com.google.common.base.Stopwatch;
import java.util.List;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.mdsal.binding.api.DataTreeChangeListener;
import org.opendaylight.mdsal.binding.api.DataTreeModification;
import org.opendaylight.netconf.keystore.legacy.impl.DefaultNetconfKeystoreService.ConfigStateBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev240708.Keystore;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev240708._private.keys.PrivateKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev240708.keystore.entry.KeyCredential;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev240708.trusted.certificates.TrustedCertificate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NonNullByDefault
record ConfigListener(DefaultNetconfKeystoreService keystore) implements DataTreeChangeListener<Keystore> {
    private static final Logger LOG = LoggerFactory.getLogger(ConfigListener.class);

    ConfigListener {
        requireNonNull(keystore);
    }

    @Override
    public void onInitialData() {
        keystore.runUpdate(builder -> {
            builder.privateKeys().clear();
            builder.trustedCertificates().clear();
        });
    }

    @Override
    public void onDataTreeChanged(final List<DataTreeModification<Keystore>> changes) {
        LOG.debug("Starting update with {} changes", changes.size());
        final var sw = Stopwatch.createStarted();
        keystore.runUpdate(builder -> onDataTreeChanged(builder, changes));
        LOG.debug("Update finished in {}", sw);
    }

    private static void onDataTreeChanged(final ConfigStateBuilder builder,
            final List<DataTreeModification<Keystore>> changes) {
        for (var change : changes) {
            LOG.debug("Processing change {}", change);
            final var rootNode = change.getRootNode();

            for (var mod : rootNode.getModifiedChildren(PrivateKey.class)) {
                switch (mod.modificationType()) {
                    case SUBTREE_MODIFIED, WRITE -> {
                        final var privateKey = mod.dataAfter();
                        builder.privateKeys().put(privateKey.requireName(), privateKey);
                    }
                    case DELETE -> builder.privateKeys().remove(mod.coerceKeyStep(PrivateKey.class).key().getName());
                    default -> {
                        // no-op
                    }
                }
            }
            for (var mod : rootNode.getModifiedChildren(TrustedCertificate.class)) {
                switch (mod.modificationType()) {
                    case SUBTREE_MODIFIED, WRITE -> {
                        final var trustedCertificate = mod.dataAfter();
                        builder.trustedCertificates().put(trustedCertificate.requireName(), trustedCertificate);
                    }
                    case DELETE -> builder.trustedCertificates()
                        .remove(mod.coerceKeyStep(TrustedCertificate.class).key().getName());
                    default -> {
                        // no-op
                    }
                }
            }
            for (var mod : rootNode.getModifiedChildren(KeyCredential.class)) {
                switch (mod.modificationType()) {
                    case SUBTREE_MODIFIED, WRITE -> {
                        final var keyCredential = mod.dataAfter();
                        builder.credentials().put(keyCredential.requireKeyId(), keyCredential);
                    }
                    case DELETE ->
                        builder.credentials().remove(mod.coerceKeyStep(KeyCredential.class).key().getKeyId());
                    default -> {
                        // no-op
                    }
                }
            }
        }
    }
}