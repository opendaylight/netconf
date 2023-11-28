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
import org.opendaylight.restconf.nb.rfc8040.databind.PatchBody;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;

/**
 * An {@link ApiPath} subpath of {@code /data} {@code PUT} HTTP operation, as defined in
 * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-4.5">RFC8040 section 4.5</a>.
 *
 * @param databind Associated {@link DatabindContext}
 * @param instance Associated {@link YangInstanceIdentifier}
 * @see PatchBody
 */
@NonNullByDefault
public record DataPatchPath(DatabindContext databind, YangInstanceIdentifier instance)
        implements DatabindAware {
    public DataPatchPath {
        requireNonNull(databind);
        requireNonNull(instance);
    }
}
