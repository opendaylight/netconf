/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.restful.utils;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import org.opendaylight.netconf.sal.restconf.impl.PATCHContext;
import org.opendaylight.netconf.sal.restconf.impl.PATCHEditOperation;
import org.opendaylight.netconf.sal.restconf.impl.PATCHEntity;
import org.opendaylight.netconf.sal.restconf.impl.PATCHStatusContext;
import org.opendaylight.netconf.sal.restconf.impl.PATCHStatusEntity;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError;
import org.opendaylight.restconf.restful.transaction.TransactionVarsWrapper;

public class PatchDataTransactionUtil {

    private PatchDataTransactionUtil() {
        throw new UnsupportedOperationException("Util class.");
    }

    public static PATCHStatusContext patchData(final PATCHContext context, final TransactionVarsWrapper transactionNode)
    {
        final List<PATCHStatusEntity> editCollection = new ArrayList<>();
        final List<RestconfError> errors = new ArrayList<>();

        for (PATCHEntity patchEntity : context.getData()) {
            final PATCHEditOperation operation = PATCHEditOperation.valueOf(patchEntity.getOperation());

            switch (operation) {
                case CREATE:
                    // post?
                    break;
                case DELETE:
                    break;
                case INSERT:
                    break;
                case MERGE:
                    break;
                case MOVE:
                    break;
                case REPLACE:
                    break;
                case REMOVE:
                    break;
                default:
                    break;
            }
        }


        // cloce transactions?
        return new PATCHStatusContext(context.getPatchId(), ImmutableList.copyOf(editCollection),
                errors.isEmpty(), errors);
    }
}
