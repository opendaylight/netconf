/*
 * Copyright (C) 2019 Ericsson Software Technology AB. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.handlers;

import static java.util.Objects.requireNonNull;

import javax.inject.Inject;
import javax.inject.Singleton;
import jdk.nashorn.internal.ir.annotations.Reference;
import org.opendaylight.mdsal.dom.api.DOMActionService;


/**
 * Implementation of {@link ActionServiceHandler}.
 */
@Singleton
public class ActionServiceHandler implements Handler<DOMActionService> {
    private final DOMActionService actionService;

    /**
     * Set DOMActionService.
     *
     * @param actionService
     *             DOMActionService
     */
    @Inject
    public ActionServiceHandler(final @Reference DOMActionService actionService) {
        this.actionService = requireNonNull(actionService);
    }

    @Override
    public DOMActionService get() {
        return this.actionService;
    }
}
