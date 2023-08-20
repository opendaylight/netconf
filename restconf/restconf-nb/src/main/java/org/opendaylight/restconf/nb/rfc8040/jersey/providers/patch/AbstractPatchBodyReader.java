/*
 * Copyright (c) 2017 Pantheon Technologies, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.jersey.providers.patch;

import java.util.Optional;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.patch.PatchContext;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindProvider;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.spi.AbstractIdentifierAwareJaxRsProvider;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.IdentifierCodec;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.ParserIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;

/**
 * Common superclass for readers producing {@link PatchContext}.
 *
 * @author Robert Varga
 */
abstract class AbstractPatchBodyReader extends AbstractIdentifierAwareJaxRsProvider<PatchContext> {
    protected AbstractPatchBodyReader(final DatabindProvider databindProvider,
            final DOMMountPointService mountPointService) {
        super(databindProvider, mountPointService);
    }

    @Override
    protected final PatchContext emptyBody(final InstanceIdentifierContext path) {
        return new PatchContext(path, null, null);
    }

    static final YangInstanceIdentifier parsePatchTarget(final InstanceIdentifierContext context, final String target) {
        final var schemaContext = context.getSchemaContext();
        final var urlPath = context.getInstanceIdentifier();
        final String targetUrl;
        if (urlPath.isEmpty()) {
            targetUrl = target.startsWith("/") ? target.substring(1) : target;
        } else {
            targetUrl = IdentifierCodec.serialize(urlPath, schemaContext) + target;
        }

        return ParserIdentifier.toInstanceIdentifier(targetUrl, schemaContext, Optional.empty())
            .getInstanceIdentifier();
    }
}
