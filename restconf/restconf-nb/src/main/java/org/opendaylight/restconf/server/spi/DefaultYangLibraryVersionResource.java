/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import static java.util.Objects.requireNonNull;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.common.errors.RestconfFuture;
import org.opendaylight.restconf.nb.rfc8040.legacy.NormalizedNodePayload;
import org.opendaylight.restconf.nb.rfc8040.legacy.WriterParameters;
import org.opendaylight.restconf.server.api.QueryParams;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.rev170126.restconf.Restconf;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack.Inference;

@NonNullByDefault
record DefaultYangLibraryVersionResource(Inference leafInference, LeafNode<String> leaf)
        implements YangLibraryVersionResource {
    static final QName YANG_LIBRARY_VERSION = QName.create(Restconf.QNAME, "yang-library-version").intern();

    DefaultYangLibraryVersionResource {
        requireNonNull(leafInference);
        requireNonNull(leaf);
    }

    @Override
    public RestconfFuture<NormalizedNodePayload> httpGET(final QueryParams params) {
        return RestconfFuture.of(new NormalizedNodePayload(leafInference, leaf,
            WriterParameters.of(params.prettyPrint(), null)));
    }
}