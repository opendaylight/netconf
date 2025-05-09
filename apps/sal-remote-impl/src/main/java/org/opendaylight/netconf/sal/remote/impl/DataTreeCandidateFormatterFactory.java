/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.remote.impl;

import java.util.List;
import javax.xml.xpath.XPathExpressionException;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.server.spi.EventFormatterFactory;
import org.opendaylight.yangtools.yang.data.tree.api.DataTreeCandidate;

@NonNullByDefault
abstract class DataTreeCandidateFormatterFactory extends EventFormatterFactory<List<DataTreeCandidate>> {
    DataTreeCandidateFormatterFactory(final DataTreeCandidateFormatter emptyFormatter) {
        super(emptyFormatter);
    }

    @Override
    public final DataTreeCandidateXPathEventFilter newXPathFilter(final String expression)
            throws XPathExpressionException {
        return new DataTreeCandidateXPathEventFilter(expression);
    }
}
