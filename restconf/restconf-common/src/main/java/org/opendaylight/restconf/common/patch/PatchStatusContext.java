/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.common.patch;

import static java.util.Objects.requireNonNull;

import java.util.List;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.api.RestconfResponse;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

/**
 * Holder of patch status context.
 */
public record PatchStatusContext(
    // FIXME: DatabindContext when we are in our proper place
    @NonNull EffectiveModelContext context,
    @NonNull String patchId,
    @NonNull List<PatchStatusEntity> editCollection,
    boolean ok,
    @Nullable List<RestconfError> globalErrors) implements RestconfResponse.Body {

    public PatchStatusContext {
        requireNonNull(patchId);
        requireNonNull(context);
        requireNonNull(editCollection);
    }
}
