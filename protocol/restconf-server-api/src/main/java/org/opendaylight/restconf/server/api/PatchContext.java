/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import static java.util.Objects.requireNonNull;
import static org.opendaylight.restconf.server.api.ServerUtil.requireNonNullValue;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.databind.RequestException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.patch.rev170222.yang.patch.YangPatch;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.patch.rev170222.yang.patch.yang.patch.Edit;
import org.opendaylight.yangtools.yang.common.QName;

@Beta
@NonNullByDefault
public record PatchContext(String patchId, ImmutableList<PatchEntity> entities) {
    public PatchContext {
        requireNonNull(patchId);
        requireNonNull(entities);
    }

    public PatchContext(final String patchId, final List<PatchEntity> entities) {
        this(patchId, ImmutableList.copyOf(entities));
    }

    /**
     * Validate with checked exception that provided parameters are not null and create PatchContext.
     *
     * @param patchId value for patch-id leaf
     * @param entities value for edit leaf
     * @return {@link PatchContext}
     * @throws RequestException if any of provided parameters are null
     */
    public static PatchContext create(final String patchId, final ImmutableList<PatchEntity> entities)
            throws RequestException {
        return new PatchContext(
            requireNonNullValue(patchId,  QName.create(YangPatch.QNAME, "patch-id")),
            requireNonNullValue(entities, Edit.QNAME));
    }
}
