/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.eclipse.jdt.annotation.NonNullByDefault;

@Beta
@NonNullByDefault
public record PatchContext(String patchId, ImmutableList<PatchEntity> entities) {
    public PatchContext {
        requireNonNull(patchId);
        requireNonNull(entities);
    }

    public PatchContext(final String patchId, final List<PatchEntity> entities) {
        this(patchId, ImmutableList.copyOf(entities));
    }
}
