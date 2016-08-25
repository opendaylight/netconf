/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.util;

import com.google.common.base.Throwables;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.Futures;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public final class ContainerException extends Exception {

    private final List<Exception> innerExceptions = new LinkedList<>();

    public ContainerException() {}

    public ContainerException(final String message) {
        super(message);
    }

    public void add(final Exception e) {
        innerExceptions.add(e);
    }

    public void throwException() throws Exception {
        if (!innerExceptions.isEmpty()) {
            throw joinAllExceptions();
        }
    }

    public Optional<CheckedFuture> getFutureException(){
        if (innerExceptions.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(Futures.immediateFailedCheckedFuture(joinAllExceptions()));
    }

    private Exception joinAllExceptions() {
        final String message = innerExceptions.stream().map(Throwables::getStackTraceAsString).collect(Collectors.joining("\n"));
        return new ContainerException(message);
    }
}
