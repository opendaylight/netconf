/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.mdsal;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.nb.rfc8040.ApiPath;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

// FIXME: This should be a record once we have JDK17+
final class LocalContext {
    private final @NonNull YangInstanceIdentifier localPath;
    private final @Nullable ApiPath remotePath;
    // FIXME: also store SchemaNode/EffectiveStatementInference

    private LocalContext(final YangInstanceIdentifier localPath, final ApiPath remotePath) {
        this.localPath = requireNonNull(localPath);
        this.remotePath = remotePath == null ? null : remotePath.steps().isEmpty() ? null : remotePath;
    }

    static LocalContext ofRead(final ApiPath path, final EffectiveModelContext schemaContextRef) {
        // FIXME: split path on yang-ext:mount (as bound to the data model!)
        // FIXME: mount should not be the part of remotePath
        return new LocalContext();
    }

    @NonNull YangInstanceIdentifier localPath() {
        return localPath;
    }

    @Nullable ApiPath remotePath() {
        return remotePath;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).omitNullValues()
            .add("local", localPath)
            .add("remote", remotePath)
            .toString();
    }
}
