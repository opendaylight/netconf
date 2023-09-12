/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.common.patch;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableList;
import java.util.List;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.patch.rev170222.yang.patch.yang.patch.Edit.Operation;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

@NonNullByDefault
public record PatchRequest(String patchId, ImmutableList<PatchRequest.Edit> entities) {
    /**
     * A single patch edit operation, which requires value leaf representing data to be present.
     *
     * @param editId Id of Patch edit
     * @param operation Patch edit operation
     * @param target Target node for Patch edit operation
     * @param node Data defined by value leaf used by edit operation, or {@code null}
     */
    public record Edit(
            String editId,
            Operation operation,
            YangInstanceIdentifier target,
            @Nullable NormalizedNode node) {
        public Edit {
            requireNonNull(editId);
            requireNonNull(operation);
            requireNonNull(target);
        }

        /**
         * Constructor to create PatchEntity for Patch operations which do not allow value leaf representing data to be
         * present. {@code node} is set to {@code null} meaning that data are not allowed for edit operation.
         *
         * @param editId Id of Patch edit
         * @param operation Patch edit operation
         * @param target Target node for Patch edit operation
         */
        public Edit(final String editId, final Operation operation, final YangInstanceIdentifier target) {
            this(editId, operation, target, null);
        }
    }

    public PatchRequest {
        requireNonNull(patchId);
        requireNonNull(entities);
    }

    public PatchRequest(final String patchId, final List<Edit> entities) {
        this(patchId, ImmutableList.copyOf(entities));
    }
}
