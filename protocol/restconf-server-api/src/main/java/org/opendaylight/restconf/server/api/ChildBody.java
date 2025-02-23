/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableList;
import java.io.InputStream;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.databind.DatabindPath.Data;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

@NonNullByDefault
public abstract sealed class ChildBody extends RequestBody permits JsonChildBody, XmlChildBody {
    public record PrefixAndBody(ImmutableList<PathArgument> prefix, NormalizedNode body) {
        public PrefixAndBody {
            requireNonNull(prefix);
            requireNonNull(body);
        }
    }

    ChildBody(final InputStream inputStream) {
        super(inputStream);
    }

    /**
     * Interpret this object as a child of specified path.
     *
     * @param path POST request path
     * @return A {@link PrefixAndBody}
     * @throws ServerException if an error occurs
     */
    public final PrefixAndBody toPayload(final Data path) throws ServerException {
        return toPayload(path, consume());
    }

    abstract PrefixAndBody toPayload(Data path, InputStream inputStream) throws ServerException;
}
