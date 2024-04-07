/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfFuture;
import org.opendaylight.restconf.nb.rfc8040.legacy.NormalizedNodePayload;
import org.opendaylight.restconf.server.api.DatabindContext;
import org.opendaylight.restconf.server.api.QueryParams;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.rev170126.YangApi;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.rev170126.restconf.Restconf;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack.Inference;

/**
 * RESTCONF {@code /yang-library-version} content for a {@code GET} operation as per
 * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-3.3.3">RFC8040, section 3.3.3</a>.
 */
@NonNullByDefault
public sealed interface YangLibraryVersionResource
        permits DefaultYangLibraryVersionResource, FailedYangLibraryVersionResource {

    RestconfFuture<NormalizedNodePayload> httpGET(QueryParams params);

    static YangLibraryVersionResource of(final DatabindContext databind) {
        final var modelContext = databind.modelContext();

        final Inference leafInference;
        try {
            final var stack = SchemaInferenceStack.of(modelContext);
            stack.enterYangData(YangApi.NAME);
            stack.enterDataTree(Restconf.QNAME);
            stack.enterDataTree(DefaultYangLibraryVersionResource.YANG_LIBRARY_VERSION);
            leafInference = stack.toInference();
        } catch (IllegalArgumentException e) {
            return new FailedYangLibraryVersionResource(new RestconfDocumentedException(
                "yang-library-version is not available", e));
        }

        final var it = modelContext.findModuleStatements("ietf-yang-library").iterator();
        return it.hasNext()
            ? new DefaultYangLibraryVersionResource(leafInference,
                ImmutableNodes.leafNode(DefaultYangLibraryVersionResource.YANG_LIBRARY_VERSION,
                    it.next().localQNameModule().revisionUnion().unionString()))
            : new FailedYangLibraryVersionResource(new RestconfDocumentedException("No ietf-yang-library present"));
    }
}
