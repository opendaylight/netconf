/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.remote.impl;

import java.util.List;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.server.spi.EventFormatter;
import org.opendaylight.restconf.server.spi.TextParameters;
import org.opendaylight.yangtools.yang.data.tree.api.DataTreeCandidate;

/**
 * Base formatter for DataTreeCandidates which only handles exporting to a document for filter checking purpose.
 */
@NonNullByDefault
abstract class DataTreeCandidateFormatter extends EventFormatter<List<DataTreeCandidate>> {
    DataTreeCandidateFormatter(final TextParameters textParams) {
        super(textParams);
    }
}
