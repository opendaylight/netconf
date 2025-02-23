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
import org.opendaylight.netconf.databind.DatabindContext;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.restconf.server.api.ServerException;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.rev170126.YangApi;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.rev170126.restconf.Restconf;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack.Inference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RESTCONF {@code /yang-library-version} content for a {@code GET} operation as per
 * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-3.3.3">RFC8040, section 3.3.3</a>.
 */
@NonNullByDefault
public record YangLibraryVersionResource(DatabindContext databind, Inference restconf, LeafNode<String> leaf)
        implements HttpGetResource {
    private static final Logger LOG = LoggerFactory.getLogger(YangLibraryVersionResource.class);
    private static final QName YANG_LIBRARY_VERSION = QName.create(Restconf.QNAME, "yang-library-version").intern();

    public YangLibraryVersionResource {
        requireNonNull(databind);
        requireNonNull(restconf);
        requireNonNull(leaf);
    }

    public static HttpGetResource of(final DatabindContext databind) {
        final var modelContext = databind.modelContext();

        final Inference leafInference;
        try {
            final var stack = SchemaInferenceStack.of(modelContext);
            stack.enterYangData(YangApi.NAME);
            stack.enterDataTree(Restconf.QNAME);
            stack.enterDataTree(YANG_LIBRARY_VERSION);
            stack.exitToDataTree();
            leafInference = stack.toInference();
        } catch (IllegalArgumentException e) {
            LOG.debug("Cannot find yang-library-version", e);
            return new FailedHttpGetResource(new ServerException("yang-library-version is not available", e));
        }

        final var it = modelContext.findModuleStatements("ietf-yang-library").iterator();
        if (!it.hasNext()) {
            LOG.debug("Cannot find ietf-yang-library");
            return new FailedHttpGetResource(new ServerException("No ietf-yang-library present"));
        }

        return new YangLibraryVersionResource(databind, leafInference,
            ImmutableNodes.leafNode(YANG_LIBRARY_VERSION, it.next().localQNameModule().revisionUnion().unionString()));
    }

    @Override
    public void httpGET(final ServerRequest<FormattableBody> request) {
        request.completeWith(new DataFormattableBody<>(databind, restconf, leaf));
    }

    @Override
    public void httpGET(final ServerRequest<FormattableBody> request, final ApiPath apiPath) {
        throw new UnsupportedOperationException();
    }
}
