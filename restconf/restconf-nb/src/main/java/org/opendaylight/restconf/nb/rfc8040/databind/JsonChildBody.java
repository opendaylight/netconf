/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.databind;

import com.google.common.collect.ImmutableList;
import java.io.InputStream;
import org.opendaylight.restconf.server.api.DataPostPath;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.stream.InputStreamNormalizer.PrefixAndResult;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizationException;

public final class JsonChildBody extends ChildBody {
    public JsonChildBody(final InputStream inputStream) {
        super(inputStream);
    }

    @Override
    @SuppressWarnings("checkstyle:illegalCatch")
    PrefixAndBody toPayload(final DataPostPath path, final InputStream inputStream) {
        final PrefixAndResult prefixAndResult;
        try {
            prefixAndResult = path.databind().jsonCodecs().parseChildData(path.inference(), inputStream);
        } catch (NormalizationException e) {
            throw mapException(e);
        }

        final var result = prefixAndResult.result().data();
        return new PrefixAndBody(ImmutableList.<PathArgument>builder()
            .addAll(prefixAndResult.prefix())
            .add(result.name())
            .build(), result);
    }
}
