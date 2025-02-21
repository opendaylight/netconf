/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.subscription;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EncodeJson$I;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.EstablishSubscriptionInputBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.IetfSubscribedNotificationsData;
import org.opendaylight.yangtools.binding.data.codec.impl.SimpleBindingDOMCodecFactory;
import org.opendaylight.yangtools.binding.runtime.spi.BindingRuntimeHelpers;

class CodingTest {
    @Test
    void transcodeEstablish() {
        final var codec = new SimpleBindingDOMCodecFactory()
            .newBindingDataCodec(BindingRuntimeHelpers.createRuntimeContext(IetfSubscribedNotificationsData.class));

        final var input = codec.nodeSerializer().toNormalizedNodeRpcData(new EstablishSubscriptionInputBuilder()
            .setEncoding(EncodeJson$I.VALUE)
//            .setTarget(new StreamBuilder()
//                .setStreamFilter(new ByReferenceBuilder().setStreamFilterName("filterName").build())
//                .addAugmentation(new Stream1Builder()
////                    .setStream("NETCONF")
//                    .build())
//                .build())
            .build());
        assertEquals("""
            """,
//            containerNode (urn:ietf:params:xml:ns:yang:ietf-subscribed-notifications@2019-09-09)input = {
//                choiceNode target = {
//                    choiceNode stream-filter = {
//                        leafNode stream-filter-name = "filterName"
//                    }
//                }
//            }""",
            input.prettyTree().toString());
    }
}
