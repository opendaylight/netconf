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
import org.opendaylight.restconf.nb.rfc8040.utils.parser.YangInstanceIdentifierSerializer;
import org.opendaylight.restconf.server.api.DataPatchPath;
import org.opendaylight.restconf.server.spi.ApiPathNormalizer;
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

    public final @NonNull PatchContext toPatchContext(final @NonNull DataPatchPath path) throws IOException {
        try (var is = acquireStream()) {
            return toPatchContext(path, is);
        }
    }

    abstract @NonNull PatchContext toPatchContext(@NonNull DataPatchPath path, @NonNull InputStream inputStream)
        throws IOException;

    static final YangInstanceIdentifier parsePatchTarget(final DataPatchPath path, final String target)
            throws RestconfDocumentedException {
        final var urlPath = path.instance();
        if (target.equals("/")) {
            verify(!urlPath.isEmpty(),
                "target resource of URI must not be a datastore resource when target is '/'");
            return urlPath;
        }

        // FIXME: NETCONF-1157: these two operations should really be a single ApiPathNormalizer step -- but for that
        //                      we need to switch to ApiPath forms
        final var databind = path.databind();
        final String targetUrl;
        if (urlPath.isEmpty()) {
            targetUrl = target.startsWith("/") ? target.substring(1) : target;
        } else {
            targetUrl = new YangInstanceIdentifierSerializer(databind).serializePath(urlPath) + target;
        }

        try {
            return new ApiPathNormalizer(databind).normalizeDataPath(ApiPath.parse(targetUrl)).instance();
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
