/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.patch.PatchContext;
import org.opendaylight.restconf.server.api.DatabindPath.Data;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.patch.rev170222.yang.patch.YangPatch;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.patch.rev170222.yang.patch.yang.patch.Edit.Operation;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.patch.rev170222.yang.patch.yang.patch.edit.Value;
import org.opendaylight.yangtools.concepts.Immutable;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.NormalizationResultHolder;

/**
 * A YANG Patch body.
 */
public abstract sealed class PatchBody extends AbstractBody permits JsonPatchBody, XmlPatchBody {
    /**
     * Resource context needed to completely resolve a {@link PatchBody}.
     */
    @NonNullByDefault
    public abstract static class ResourceContext implements Immutable {
        protected final Data path;

        protected ResourceContext(final Data path) {
            this.path = requireNonNull(path);
        }

        /**
         * Return a {@link ResourceContext} for a sub-resource identified by an {@link ApiPath}.
         *
         * @param apiPath sub-resource
         * @return A {@link ResourceContext}
         * @throws RestconfDocumentedException if the sub-resource cannot be resolved
         */
        protected abstract ResourceContext resolveRelative(ApiPath apiPath);
    }

    private static final QName EDIT_ID = QName.create(YangPatch.QNAME, "edit-id").intern();
    private static final QName OPERATION = QName.create(YangPatch.QNAME, "operation").intern();
    private static final QName PATCH_ID = QName.create(YangPatch.QNAME, "patch-id").intern();
    private static final QName POINT = QName.create(YangPatch.QNAME, "point").intern();
    private static final QName TARGET = QName.create(YangPatch.QNAME, "target").intern();
    private static final QName WHERE = QName.create(YangPatch.QNAME, "where").intern();
    private static final QName VALUE = Value.QNAME;

    PatchBody(final InputStream inputStream) {
        super(inputStream);
    }

    public final @NonNull PatchContext toPatchContext(final ResourceContext resource) throws IOException {
        final ContainerNode yangPatch;
        try (var is = acquireStream()) {
            final var holder = new NormalizationResultHolder();
            try (var streamWriter = ImmutableNormalizedNodeStreamWriter.from(holder)) {
                streamTo(resource.path.databind(), is, streamWriter);
            } catch (IOException e) {
                throw new RestconfDocumentedException("Error parsing input: " + e.getMessage(), ErrorType.PROTOCOL,
                        ErrorTag.MALFORMED_MESSAGE, e);
            }

            yangPatch = (ContainerNode) holder.getResult().data();
        }

        return toPatchContext(resource, yangPatch);
    }

    private final @NonNull PatchContext toPatchContext(final ResourceContext resource,
            final ContainerNode yangPatch) {


        return new PatchContext(null, null)



        throw new UnsupportedOperationException();
    }

    abstract void streamTo(DatabindContext databind, InputStream inputStream,
        NormalizedNodeStreamWriter writer) throws IOException;

    static final Data parsePatchTarget(final @NonNull ResourceContext resource, final String target) {
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

        final Data result;
        try {
            result = resource.resolveRelative(targetPath).path;
        } catch (RestconfDocumentedException e) {
            throw new RestconfDocumentedException("Invalid edit target '" + targetPath + "'",
                ErrorType.RPC, ErrorTag.MALFORMED_MESSAGE, e);
        }
        if (result.instance().isEmpty()) {
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
