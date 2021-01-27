/*
 * Copyright (c) 2020 Pantheon Technologies, s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.test.notifications;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.binding.dom.codec.api.BindingNormalizedNodeSerializer;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.mdsal.dom.api.DOMNotificationListener;
import org.opendaylight.yang.gen.v1.http.cisco.com.ns.yang.cisco.ios.xr.ip._static.cfg.rev130722.VRFPREFIXTABLE;
import org.opendaylight.yangtools.yang.binding.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NotificationsCounter implements DOMNotificationListener {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationsCounter.class);

    /**
     * Custom pattern to identify nodes where performance should be measured
     */
    private static final Pattern NOTIFICATION_NUMBER_PATTERN = Pattern.compile(".*-notif-([0-9]+)");

    private final String nodeId;
    private final BindingNormalizedNodeSerializer serializer;
    private final AtomicLong notifCounter;
    private final long expectedNotificationCount;
    private Stopwatch stopWatch;
    private long totalPrefixesReceived = 0;

    public NotificationsCounter(final String nodeId, final BindingNormalizedNodeSerializer serializer) {
        this.nodeId = nodeId;
        this.serializer = serializer;
        final Matcher matcher = NOTIFICATION_NUMBER_PATTERN.matcher(nodeId);
        Preconditions.checkArgument(matcher.matches());
        expectedNotificationCount = Long.parseLong(matcher.group(1));
        Preconditions.checkArgument(expectedNotificationCount > 0);
        this.notifCounter = new AtomicLong(this.expectedNotificationCount);
    }


    @Override
    public void onNotification(@NonNull DOMNotification domNotification) {
        final long andDecrement = notifCounter.getAndDecrement();

        if(andDecrement == expectedNotificationCount) {
            this.stopWatch = Stopwatch.createStarted();
            LOG.info("First notification received at {}", stopWatch);
        }

        LOG.debug("Notification received, {} to go.", andDecrement);
        if(LOG.isTraceEnabled()) {
            LOG.trace("Notification received: {}", domNotification);
        }

        final Notification notification = serializer.fromNormalizedNodeNotification(domNotification.getType(),
            domNotification.getBody());
        if (notification instanceof VRFPREFIXTABLE) {
            totalPrefixesReceived += ((VRFPREFIXTABLE)notification).getVrfPrefixes().getVrfPrefix().size();
        }

        if(andDecrement == 1) {
            this.stopWatch.stop();
            LOG.info("Last notification received at {}", stopWatch);
            LOG.info("Elapsed ms for {} notifications: {}", expectedNotificationCount, stopWatch.elapsed(TimeUnit.MILLISECONDS));
            LOG.info("Performance (notifications/second): {}",
                (expectedNotificationCount * 1.0/stopWatch.elapsed(TimeUnit.MILLISECONDS)) * 1000);
            LOG.info("Performance (prefixes/second): {}",
                (totalPrefixesReceived * 1.0/stopWatch.elapsed(TimeUnit.MILLISECONDS)) * 1000);
        }
    }

}
