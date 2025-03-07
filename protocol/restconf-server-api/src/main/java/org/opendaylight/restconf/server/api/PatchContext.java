/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import static org.opendaylight.restconf.server.api.JsonPatchBody.requireNonNullValue;

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
public class PatchContext {
    private static final QName PATCH_ID = QName.create(YangPatch.QNAME, "patch-id").intern();

    private final String patchId;
    private final ImmutableList<PatchEntity> entities;

    public PatchContext(final String patchId, final ImmutableList<PatchEntity> entities) throws RequestException {
        this.patchId = requireNonNullValue(patchId, PATCH_ID);
        this.entities = requireNonNullValue(entities, Edit.QNAME);
    }

    public PatchContext(final String patchId, final List<PatchEntity> entities) throws RequestException {
        this(patchId, ImmutableList.copyOf(entities));
    }

    public String patchId() {
        return patchId;
    }

    public ImmutableList<PatchEntity> entities() {
        return entities;
    }
}
