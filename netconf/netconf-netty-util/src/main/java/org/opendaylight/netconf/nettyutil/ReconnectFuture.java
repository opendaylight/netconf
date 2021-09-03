/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil;

import com.google.common.annotations.Beta;
import io.netty.util.concurrent.Future;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yangtools.yang.common.Empty;

/**
 * A future representing the task of reconnecting of a certain channel. This future never completes successfully, it
 * either fails when the underlying strategy gives up, or when it is cancelled. It additionally exposes an additional
 * future, which completes when the session is established for the first time.
 */
@Beta
public interface ReconnectFuture extends Future<Empty> {
    /**
     * Return a Future which completes when the first session is established.
     *
     * @return First session establishment future
     */
    @NonNull Future<?> firstSessionFuture();
}
