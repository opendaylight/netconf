/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.api.messages.NetconfHelloMessage;

public abstract class NetconfSessionPreferences {
    private final @NonNull NetconfHelloMessage helloMessage;

    protected NetconfSessionPreferences(final NetconfHelloMessage helloMessage) {
        this.helloMessage = requireNonNull(helloMessage);
    }

    /**
     * Getter for {@code NetconfHelloMessage}.
     *
     * @return the helloMessage
     */
    public final @NonNull NetconfHelloMessage getHelloMessage() {
        return helloMessage;
    }

    @Override
    public final String toString() {
        return addToStringAttributes(MoreObjects.toStringHelper(this).omitNullValues()).toString();
    }

    protected ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        return helper.add("hello", helloMessage);
    }
}
