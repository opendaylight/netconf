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
import org.opendaylight.netconf.databind.DatabindPath.Data;
import org.opendaylight.netconf.databind.RequestException;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.patch.rev170222.yang.patch.yang.patch.Edit.Operation;
import org.opendaylight.yangtools.concepts.Immutable;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.SystemLeafSetNode;

/**
 * A YANG Patch body.
 */
public abstract sealed class PatchBody extends RequestBody permits JsonPatchBody, XmlPatchBody {
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
         * @throws RequestException if the sub-resource cannot be resolved
         */
        protected abstract ResourceContext resolveRelative(ApiPath apiPath) throws RequestException;
    }

    PatchBody(final InputStream inputStream) {
        super(inputStream);
    }

    public final @NonNull PatchContext toPatchContext(final @NonNull ResourceContext resource) throws RequestException {
        try (var is = consume()) {
            return toPatchContext(resource, is);
        } catch (IOException e) {
            throw new RequestException(ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE, e);
        }
    }

    abstract @NonNull PatchContext toPatchContext(@NonNull ResourceContext resource, @NonNull InputStream inputStream)
        throws IOException, RequestException;

    static final Data parsePatchTarget(final @NonNull ResourceContext resource, final String target)
            throws RequestException {
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
            throw new RequestException(ErrorType.RPC, ErrorTag.MALFORMED_MESSAGE,
                "Failed to parse edit target '" + target + "'", e);
        }

        final var result  = resource.resolveRelative(targetPath).path;
        if (result.instance().isEmpty()) {
            throw new RequestException(ErrorType.RPC, ErrorTag.MALFORMED_MESSAGE,
                "Target node resource must not be a datastore resource");
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

    static final NormalizedNode unwrapListNodes(final NormalizedNode node) {
        if (node instanceof MapNode map) {
            // TODO: This is a weird special case: a YANG-PATCH target cannot specify the entire map, but the body
            //       parser always produces a single-entry map for entries. We need to undo that damage here.
            return map.body().iterator().next();
        } else if (node instanceof SystemLeafSetNode<?> leafSetNode) {
            // Applying the same parser workaround logic as for MapNode above.
            return leafSetNode.body().iterator().next();
        }
        return node;
    }
}
