/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.databind;

import static com.google.common.base.Verify.verify;

import java.io.IOException;
import java.io.InputStream;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.patch.PatchContext;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.IdentifierCodec;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.ParserIdentifier;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.patch.rev170222.yang.patch.yang.patch.Edit.Operation;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

/**
 * A YANG Patch body.
 */
public abstract sealed class PatchBody extends AbstractBody permits JsonPatchBody, XmlPatchBody {
    PatchBody(final InputStream inputStream) {
        super(inputStream);
    }

    public final @NonNull PatchContext toPatchContext(final @NonNull EffectiveModelContext context,
            final @NonNull YangInstanceIdentifier urlPath) throws IOException {
        try (var is = acquireStream()) {
            return toPatchContext(context, urlPath, is);
        }
    }

    abstract @NonNull PatchContext toPatchContext(@NonNull EffectiveModelContext context,
        @NonNull YangInstanceIdentifier urlPath, @NonNull InputStream inputStream) throws IOException;

    static final YangInstanceIdentifier parsePatchTarget(final EffectiveModelContext context,
            final YangInstanceIdentifier urlPath, final String target) {
        if (target.equals("/")) {
            verify(!urlPath.isEmpty(),
                "target resource of URI must not be a datastore resource when target is '/'");
            return urlPath;
        }

        final String targetUrl;
        if (urlPath.isEmpty()) {
            targetUrl = target.startsWith("/") ? target.substring(1) : target;
        } else {
            targetUrl = IdentifierCodec.serialize(urlPath, context) + target;
        }

        try {
            return ParserIdentifier.toInstanceIdentifier(targetUrl, context, null).getInstanceIdentifier();
        } catch (RestconfDocumentedException e) {
            throw new RestconfDocumentedException("Failed to parse target " + target,
                ErrorType.RPC, ErrorTag.MALFORMED_MESSAGE, e);
        }
    }

    /**
     * Not all patch operations support value node. Check if operation requires value or not.
     *
     * @param operation Patch edit operation
     * @return true if operation requires value, false otherwise
     */
    static final boolean requiresValue(final Operation operation) {
        return switch (operation) {
            case Create, Insert, Merge, Replace -> true;
            case Delete, Move, Remove -> false;
        };
    }
}
