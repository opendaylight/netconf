/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import static java.util.Objects.requireNonNull;

import java.util.List;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.databind.RequestError;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.patch.rev170222.yang.patch.status.YangPatchStatus;
import org.opendaylight.yangtools.yang.common.QName;

/**
 * Holder of patch status context.
 */
public record PatchStatusContext(
    @NonNull String patchId,
    @NonNull List<PatchStatusEntity> editCollection,
    boolean ok,
    @Nullable List<RequestError> globalErrors) {

    public PatchStatusContext {
        requireNonNull(patchId, "Missing required Schema node" + QName.create(YangPatchStatus.QNAME, "patch-id"));
        requireNonNull(editCollection, "required collection" + QName.create(YangPatchStatus.QNAME, "edit-status"));
    }
}
