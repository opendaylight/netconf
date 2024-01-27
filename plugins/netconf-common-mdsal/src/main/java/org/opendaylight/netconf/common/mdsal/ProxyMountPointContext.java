/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.common.mdsal;

import static java.util.Objects.requireNonNull;

import java.util.Optional;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yangtools.concepts.Delegator;
import org.opendaylight.yangtools.yang.common.MountPointLabel;
import org.opendaylight.yangtools.yang.data.api.schema.MountPointContext;
import org.opendaylight.yangtools.yang.data.api.schema.MountPointContextFactory;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

/**
 * A simple proxy, masking the model context towards ProxyEffectiveModelContext.
 */
final class ProxyMountPointContext implements Delegator<MountPointContext>, MountPointContext {
    private final @NonNull MountPointContext delegate;

    ProxyMountPointContext(final MountPointContext delegate) {
        this.delegate = requireNonNull(delegate);
    }

    @Override
    public MountPointContext getDelegate() {
        return delegate;
    }

    @Override
    public EffectiveModelContext modelContext() {
        return new ProxyEffectiveModelContext(delegate.modelContext());
    }

    @Override
    public Optional<MountPointContextFactory> findMountPoint(final MountPointLabel label) {
        return delegate.findMountPoint(label);
    }
}
