/*
 * Copyright (C) 2019 Ericsson Software Technology AB. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.utils;

import org.opendaylight.mdsal.dom.api.DOMActionResult;
import org.opendaylight.yangtools.concepts.Builder;

/**
 * Implementation of {@link ActionResultFactory}.
 */
public class ActionResultFactory extends FutureDataFactory<DOMActionResult> implements Builder<DOMActionResult> {

    @Override
    public DOMActionResult build() throws IllegalArgumentException {
        return this.result;
    }
}
