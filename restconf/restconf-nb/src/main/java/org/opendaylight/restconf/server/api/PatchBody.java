/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.patch.PatchContext;
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

    public final @NonNull PatchContext toPatchContext(final DatabindPath.@NonNull Data path) throws IOException {
        try (var is = acquireStream()) {
            return toPatchContext(path, is);
        }
    }

    abstract @NonNull PatchContext toPatchContext(DatabindPath.@NonNull Data path, @NonNull InputStream inputStream)
        throws IOException;

    static final YangInstanceIdentifier parsePatchTarget(final DatabindPath.@NonNull Data path, final String target) {
        // As per: https://www.rfc-editor.org/rfc/rfc8072#page-18:
        //
        //        "Identifies the target data node for the edit
        //        operation.  If the target has the value '/', then
        //        the target data node is the target resource.
        //        The target node MUST identify a data resource,
        //        not the datastore resource.";
        //
        final ApiPath targetPath;
        try {
            targetPath = ApiPath.parse(target.startsWith("/") ? target.substring(1) : target);
        } catch (ParseException e) {
            throw new RestconfDocumentedException("Failed to parse edit target '" + target + "'",
                ErrorType.RPC, ErrorTag.MALFORMED_MESSAGE, e);
        }

        final YangInstanceIdentifier result;
        try {
            result = ApiPathNormalizer.normalizeSubResource(path, targetPath).instance();
        } catch (RestconfDocumentedException e) {
            throw new RestconfDocumentedException("Invalid edit target '" + targetPath + "'",
                ErrorType.RPC, ErrorTag.MALFORMED_MESSAGE, e);
        }
        if (result.isEmpty()) {
            throw new RestconfDocumentedException("Target node resource must not be a datastore resource",
                ErrorType.RPC, ErrorTag.MALFORMED_MESSAGE);
        }
        return result;
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
