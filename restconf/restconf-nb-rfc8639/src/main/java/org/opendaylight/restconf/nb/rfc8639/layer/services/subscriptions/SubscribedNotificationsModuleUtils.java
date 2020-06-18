/*
 * Copyright (c) 2020 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.nb.rfc8639.layer.services.subscriptions;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.net.URI;
import java.util.Optional;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.Encoding;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EstablishSubscriptionInput;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.ReplayCompleted;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionCompleted;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.SubscriptionTerminated;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscription.policy.modifiable.Target;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.subscription.Receivers;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.subscriptions.subscription.receivers.Receiver;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.push.rev190909.update.policy.modifiable.UpdateTrigger;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.push.rev190909.update.policy.modifiable.update.trigger.Periodic;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.AugmentationIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNodes;

public final class SubscribedNotificationsModuleUtils {
    private SubscribedNotificationsModuleUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static final String PATH_TO_SUBSCRIPTION_WITHOUT_KEY = "ietf-subscribed-notifications:subscriptions/"
            + "subscription=";

    public static final QNameModule SUBSCRIBED_MODULE = QNameModule.create(
            URI.create("urn:ietf:params:xml:ns:yang:ietf-subscribed-notifications"), Revision.of("2019-09-09"))
            .intern();
    public static final QNameModule YANG_PUSH_MODULE = QNameModule.create(
            URI.create("urn:ietf:params:xml:ns:yang:ietf-yang-push"), Revision.of("2019-09-09"))
            .intern();

    // RPCs inputs and outputs
    public static final NodeIdentifier ESTABLISH_SUBSCRIPTION_INPUT = NodeIdentifier.create(
            EstablishSubscriptionInput.QNAME);
    public static final NodeIdentifier ENCODING_LEAF_ID = NodeIdentifier.create(Encoding.QNAME);

    public static final NodeIdentifier TARGET_CHOICE_ID = NodeIdentifier.create(Target.QNAME);
    public static final NodeIdentifier STREAM_LEAF_ID = NodeIdentifier.create(
            QName.create(SUBSCRIBED_MODULE, "stream").intern());
    public static final NodeIdentifier REPLAY_START_TIME_LEAF_ID = NodeIdentifier.create(
            QName.create(SUBSCRIBED_MODULE, "replay-start-time").intern());
    public static final AugmentationIdentifier AUGMENTATION_ID = AugmentationIdentifier.create(
            ImmutableSet.of(STREAM_LEAF_ID.getNodeType(), REPLAY_START_TIME_LEAF_ID.getNodeType()));

    public static final NodeIdentifier IDENTIFIER_LEAF_ID = NodeIdentifier.create(
            QName.create(SUBSCRIBED_MODULE, "id").intern());
    public static final NodeIdentifier REPLAY_START_TIME_REVISION_ID = NodeIdentifier.create(
            QName.create(SUBSCRIBED_MODULE, "replay-start-time-revision"));

    public static final NodeIdentifier STOP_TIME_LEAF_ID = NodeIdentifier.create(
            QName.create(SUBSCRIBED_MODULE, "stop-time").intern());

    // notifications
    public static final NodeIdentifier REPLAY_COMPLETED_NOTIFICATION_ID = NodeIdentifier.create(ReplayCompleted.QNAME);
    public static final NodeIdentifier SUBSCRIPTION_COMPLETED_NOTIFICATION_ID = NodeIdentifier.create(
            SubscriptionCompleted.QNAME);
    public static final NodeIdentifier SUBSCRIPTION_TERMINATED_NOTIFICATION_ID = NodeIdentifier.create(
            SubscriptionTerminated.QNAME);

    // container subscriptions
    public static final NodeIdentifier RECEIVERS_CONTAINER_ID = NodeIdentifier.create(Receivers.QNAME);
    public static final NodeIdentifier RECEIVER_LIST_ID = NodeIdentifier.create(Receiver.QNAME);
    public static final QName RECEIVER_NAME = QName.create(SUBSCRIBED_MODULE, "name").intern();
    public static final QName RECEIVER_STATE = QName.create(SUBSCRIBED_MODULE, "state");

    // yang-push
    public static final NodeIdentifier UPDATE_TRIGGER_CHOICE_ID = NodeIdentifier.create(UpdateTrigger.QNAME);
    public static final NodeIdentifier PERIODIC_CASE_ID = NodeIdentifier.create(Periodic.QNAME);
    public static final NodeIdentifier ANCHOR_TIME_LEAF_ID = NodeIdentifier.create(
            QName.create(YANG_PUSH_MODULE, "anchor-time").intern());
    public static final NodeIdentifier PERIOD_LEAF_ID = NodeIdentifier.create(
            QName.create(YANG_PUSH_MODULE, "period").intern());

    public static Optional<NormalizedNode<?, ?>> getIdentifier(
            final NormalizedNode<?, ?> input) {
        return NormalizedNodes.findNode(input, IDENTIFIER_LEAF_ID);
    }

    public static Optional<NormalizedNode<?, ?>> getStopTime(final NormalizedNode<?, ?> input) {
        return NormalizedNodes.findNode(input, STOP_TIME_LEAF_ID);
    }

    public static Optional<NormalizedNode<?, ?>> getPeriod(final NormalizedNode<?, ?> input) {
        final AugmentationIdentifier augmentationIdentifier = new AugmentationIdentifier(
                Sets.newHashSet(UpdateTrigger.QNAME));
        return NormalizedNodes.findNode(
                input,
                augmentationIdentifier,
                UPDATE_TRIGGER_CHOICE_ID,
                PERIODIC_CASE_ID,
                PERIOD_LEAF_ID);
    }

    public static Optional<NormalizedNode<?, ?>> getAnchorTime(final NormalizedNode<?, ?> input) {
        final AugmentationIdentifier augmentationIdentifier = new AugmentationIdentifier(
                Sets.newHashSet(UpdateTrigger.QNAME));
        return NormalizedNodes.findNode(
                input,
                augmentationIdentifier,
                UPDATE_TRIGGER_CHOICE_ID,
                PERIODIC_CASE_ID,
                ANCHOR_TIME_LEAF_ID);
    }
}
