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
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

public class PatchDataTransactionUtil {

    private PatchDataTransactionUtil() {
        throw new UnsupportedOperationException("Util class.");
    }

    public static PATCHStatusContext patchData(final PATCHContext context, final TransactionVarsWrapper transactionNode,
                                               final SchemaContext schemaContext)
    {
        final List<PATCHStatusEntity> editCollection = new ArrayList<>();
        final List<RestconfError> errors = new ArrayList<>();

        for (PATCHEntity patchEntity : context.getData()) {
            final PATCHEditOperation operation = PATCHEditOperation.valueOf(patchEntity.getOperation());

            switch (operation) {
                case CREATE:
                    postDataWithinTransaction(transactionNode, patchEntity, schemaContext);
                    break;
                case DELETE:
                    deleteDataWithinTransaction(transactionNode, patchEntity, schemaContext);
                case INSERT:
                    insertDataWithinTransaction(transactionNode, patchEntity, schemaContext);
                case MERGE:
                    mergeDataWithinTransaction(transactionNode, patchEntity, schemaContext);
                case MOVE:
                    moveDataWithinTransaction(transactionNode, patchEntity, schemaContext);
                case REPLACE:
                    replaceDataWithinTransaction(transactionNode, patchEntity, schemaContext);
                case REMOVE:
                    removeDataWithinTransaction(transactionNode, patchEntity, schemaContext);
                default:
                    break;
            }
        }


        // close transaction?
        return new PATCHStatusContext(context.getPatchId(), ImmutableList.copyOf(editCollection),
                errors.isEmpty(), errors);
    }

    private static void removeDataWithinTransaction(final TransactionVarsWrapper transactionNode,
                                                    final PATCHEntity patchEntity, final SchemaContext schemaContext) {

    }

    private static void replaceDataWithinTransaction(final TransactionVarsWrapper transactionNode,
                                                     final PATCHEntity patchEntity, final SchemaContext schemaContext) {

    }

    private static void moveDataWithinTransaction(final TransactionVarsWrapper transactionNode,
                                                  final PATCHEntity patchEntity, final SchemaContext schemaContext) {

    }

    private static void mergeDataWithinTransaction(final TransactionVarsWrapper transactionNode,
                                                   final PATCHEntity patchEntity, final SchemaContext schemaContext) {

    }

    private static void insertDataWithinTransaction(final TransactionVarsWrapper transactionNode,
                                                    final PATCHEntity patchEntity, final SchemaContext schemaContext) {

    }

    private static void deleteDataWithinTransaction(final TransactionVarsWrapper transactionNode,
                                                    final PATCHEntity patchEntity, final SchemaContext schemaContext) {
    }

    private static void postDataWithinTransaction(final TransactionVarsWrapper transactionNode,
                                                  final PATCHEntity patchEntity, final SchemaContext schemaContext) {
        transactionNode.getTransaction().put(transactionNode.getLogicalDatastoreType(), patchEntity.getTargetNode(),
                patchEntity.getNode());
    }


}
