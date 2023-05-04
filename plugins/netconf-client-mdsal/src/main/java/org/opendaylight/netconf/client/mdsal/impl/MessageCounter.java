/*
 * Copyright (c) 2014, 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.impl;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Strings;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.jdt.annotation.NonNull;

public final class MessageCounter {
    private final AtomicInteger messageId = new AtomicInteger();

    public @NonNull String getNewMessageId(final String prefix) {
        checkArgument(!Strings.isNullOrEmpty(prefix), "Null or empty prefix");
        return prefix + "-" + messageId.getAndIncrement();
    }
}
