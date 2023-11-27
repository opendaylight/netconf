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
import java.text.ParseException;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.patch.PatchContext;
import org.opendaylight.restconf.nb.rfc8040.legacy.InstanceIdentifierContext;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.IdentifierCodec;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.patch.rev170222.yang.patch.yang.patch.Edit.Operation;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;

/**
 * A YANG Patch body.
 */
public abstract sealed class PatchBody extends AbstractBody permits JsonPatchBody, XmlPatchBody {
    PatchBody(final InputStream inputStream) {
        super(inputStream);
    }

    public final @NonNull PatchContext toPatchContext(final @NonNull DatabindContext databind,
            final @NonNull YangInstanceIdentifier urlPath) throws IOException {
        try (var is = acquireStream()) {
            return toPatchContext(databind, urlPath, is);
        }
    }

    abstract @NonNull PatchContext toPatchContext(@NonNull DatabindContext databind,
        @NonNull YangInstanceIdentifier urlPath, @NonNull InputStream inputStream) throws IOException;

    static final YangInstanceIdentifier parsePatchTarget(final DatabindContext databind,
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
            targetUrl = IdentifierCodec.serialize(urlPath, databind) + target;
        }

        try {
            return InstanceIdentifierContext.ofApiPath(ApiPath.parse(targetUrl), databind, null)
                .getInstanceIdentifier();
        } catch (ParseException | RestconfDocumentedException e) {
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
