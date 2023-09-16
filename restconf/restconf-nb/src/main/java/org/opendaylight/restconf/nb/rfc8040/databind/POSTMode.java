/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.databind;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;

/**
 * {@link RequestMode}s available for {@code POST} HTTP method.
 */
@NonNullByDefault
public sealed interface POSTMode extends RequestMode permits CreateResourceMode, InvokeOperationMode {

    YangInstanceIdentifier parentPath();
}
