/*
 * Copyright (c) 2020 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8639.util.services;

import com.google.common.collect.Sets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutionException;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.restconf.nb.rfc8639.layer.services.subscriptions.SubscribedNotificationsModuleUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.push.rev190909.update.policy.modifiable.UpdateTrigger;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.AugmentationIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.AugmentationNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.DataContainerNodeBuilder;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

public final class SubscribedNotificationsUtil {

    private SubscribedNotificationsUtil() {
        // util class
    }

    /**
     * Converts QName of a YANG statement to a String consisting of module name prefix and statement local name
     * separated by colon.
     *
     * @param statement YANG statement QName
     * @param schemaContext global schema context
     *
     * @return prefixed statement name
     */
    public static String qNameToModulePrefixAndName(final QName statement, final SchemaContext schemaContext) {
        final Module module = schemaContext.findModule(statement.getModule()).get();
        final String name = statement.getLocalName();
        return module.getName() + ":" + name;
    }

    public static String timeStampToRFC3339Format(final Instant timeStamp) {
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(OffsetDateTime.ofInstant(timeStamp,
                ZoneId.systemDefault()));
    }

    /**
     * Submit wrote data in transaction.
     *
     * @param readWriteTransaction
     *            - transaction with data
     */
    public static void submitData(final DOMDataTreeReadWriteTransaction readWriteTransaction,
            final DOMTransactionChain domTransactionChain) {
        try {
            readWriteTransaction.commit().get();
        } catch (final InterruptedException | ExecutionException e) {
            throw new RestconfDocumentedException("Problem while submitting data to datastore.",
                    RestconfError.ErrorType.APPLICATION, RestconfError.ErrorTag.OPERATION_FAILED, e);
        } finally {
            domTransactionChain.close();
        }
    }

    public static AugmentationNode createPeriodLeafNode(final Uint32 period) {
        final AugmentationIdentifier augmentationId = new AugmentationIdentifier(Sets.newHashSet(UpdateTrigger.QNAME));
        return Builders.augmentationBuilder()
                .withNodeIdentifier(augmentationId)
                .withChild(Builders.choiceBuilder()
                        .withNodeIdentifier(SubscribedNotificationsModuleUtils.UPDATE_TRIGGER_CHOICE_ID)
                        .withChild(Builders.containerBuilder()
                                .withNodeIdentifier(SubscribedNotificationsModuleUtils.PERIODIC_CASE_ID)
                                .withChild(Builders.leafBuilder()
                                        .withNodeIdentifier(SubscribedNotificationsModuleUtils.PERIOD_LEAF_ID)
                                        .withValue(period)
                                        .build())
                                .build())
                        .build())
                .build();
    }

    public static void prepareLeafAndFillEntryBuilder(
            final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> streamEntry,
            final DataSchemaNode leafSchema, final Object value) {
        streamEntry.withChild(Builders.leafBuilder((LeafSchemaNode) leafSchema).withValue(value).build());
    }
}
