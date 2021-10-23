/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.api;

import com.google.common.annotations.Beta;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.eclipse.jdt.annotation.NonNullByDefault;

@Beta
@NonNullByDefault
public class ReadDataFuture extends CompletableFuture<ReadDataResponse> implements ReadDataStage {
    @Override
    public ReadDataFuture toCompletableFuture() {
        return this;
    }

    @Override
    public final ReadDataResponse get() throws InterruptedException {
        try {
            return super.get();
        } catch (ExecutionException e) {
            throw new IllegalStateException("Unexpected failure", e);
        }
    }
}
