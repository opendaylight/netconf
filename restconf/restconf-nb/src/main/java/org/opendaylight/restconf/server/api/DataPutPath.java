/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import static java.util.Objects.requireNonNull;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.nb.rfc8040.databind.DataPostBody;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.meta.EffectiveStatement;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack.Inference;

/**
 * An {@link ApiPath} subpath of {@code /data} {@code PUT} HTTP operation, as defined in
 * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.5">RFC8040 section 4.5</a>.
 *
 * @param databind Associated {@link DatabindContext}
 * @param inference Associated {@link Inference} pointing to the {@link EffectiveStatement} of the last ApiPath element.
 *                  This can be one of:
 *                  <ul>
 *                    <li>a datatore, inference being {@link Inference#isEmpty() empty}</li>
 *                    <li>a data resource, inference pointing to the the {@code data schema node} identified by
 *                        {@code instance}</li>
 *                  </ul>
 * @param instance Associated {@link YangInstanceIdentifier}
 * @see DataPostBody
 */
@NonNullByDefault
public record DataPutPath(DatabindContext databind, Inference inference, YangInstanceIdentifier instance)
        implements DatabindAware {
    public DataPutPath {
        requireNonNull(databind);
        requireNonNull(inference);
        requireNonNull(instance);
    }

    public DataPutPath(final DatabindContext databind) {
        this(databind, Inference.ofDataTreePath(databind.modelContext()), YangInstanceIdentifier.of());
    }
}
