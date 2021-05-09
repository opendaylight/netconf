/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.util;

import static java.util.Objects.requireNonNull;

import java.util.Optional;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yangtools.concepts.Delegator;
import org.opendaylight.yangtools.rfc8528.data.api.MountPointContext;
import org.opendaylight.yangtools.rfc8528.data.api.MountPointContextFactory;
import org.opendaylight.yangtools.rfc8528.data.api.MountPointIdentifier;
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
    public EffectiveModelContext getEffectiveModelContext() {
        return new ProxyEffectiveModelContext(delegate.getEffectiveModelContext());
    }

    @Override
    public Optional<MountPointContextFactory> findMountPoint(final MountPointIdentifier label) {
        return delegate.findMountPoint(label);
    }
}
