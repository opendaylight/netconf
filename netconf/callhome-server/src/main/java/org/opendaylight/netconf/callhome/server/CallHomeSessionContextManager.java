/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.server;

import io.netty.channel.Channel;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

public interface CallHomeSessionContextManager<T extends CallHomeSessionContext> extends AutoCloseable {

    boolean register(T context);

    @Nullable T findByChannel(@NonNull Channel channel);

    void remove(@NonNull String contextId);

}
