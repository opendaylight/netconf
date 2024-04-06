/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.legacy;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.Beta;
import java.util.List;
import java.util.Set;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.api.FormatParameters;
import org.opendaylight.restconf.api.query.DepthParam;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.yangtools.yang.common.QName;

/**
 * This holds various options acquired from a requests's query part. This class needs to be further split up to make
 * sense of it, as parts of it pertain to how a {@link NormalizedNodePayload} should be created while others how it
 * needs to be processed (for example filtered).
 */
@Beta
public record WriterParameters(
        @NonNull PrettyPrintParam prettyPrint,
        @Nullable DepthParam depth,
        @Nullable List<Set<QName>> fields) implements FormatParameters {
    public static final @NonNull WriterParameters EMPTY = new WriterParameters(PrettyPrintParam.FALSE, null, null);

    public WriterParameters {
        requireNonNull(prettyPrint);
    }

    public static @NonNull WriterParameters of(final @NonNull PrettyPrintParam prettyPrint,
            final @Nullable DepthParam depth) {
        return depth == null && !prettyPrint.value() ? EMPTY : new WriterParameters(prettyPrint, depth, null);
    }
}
