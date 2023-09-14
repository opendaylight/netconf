/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.databind;

import static java.util.Objects.requireNonNull;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContext;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack.Inference;

/**
 * Request URL details, as established by parsing a snippet.
 *
 * @param inference An {@link Inference} on which a potential body is to be parsed
 * @param path The path to the requested resource, empty to indicate an entire datastore
 * @param context The {@link DataSchemaContext} corresponding to {@code path}
 * @param mountPoint A nested mount point on which to execute based on {@code yang-ext:mount} presence
 */
@NonNullByDefault
public record RequestUrl(
        Inference inference,
        YangInstanceIdentifier path,
        DataSchemaContext context,
        @Nullable DOMMountPoint mountPoint) {
    public RequestUrl {
        requireNonNull(inference);
        requireNonNull(path);
        requireNonNull(context);
    }
}
