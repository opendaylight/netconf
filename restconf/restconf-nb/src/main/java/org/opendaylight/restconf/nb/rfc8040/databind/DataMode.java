/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.databind;

import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContext;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack.Inference;

/**
 *
 */
public sealed interface DataMode extends RequestMode permits ResourceMode, RootMode {

    @NonNull Inference inference();

    @NonNull YangInstanceIdentifier path();

    @NonNull DataSchemaContext dataContext();
}
