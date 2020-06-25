/*
 * Copyright (c) 2020 Pantheon.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.common.formatters;

import java.util.Collection;
import javax.xml.xpath.XPathExpressionException;
import org.opendaylight.yangtools.yang.data.api.schema.tree.DataTreeCandidate;

public interface DataTreeCandidateFormatterFactory extends EventFormatterFactory<Collection<DataTreeCandidate>> {

    @Override
    DataTreeCandidateFormatter getFormatter();

    @Override
    DataTreeCandidateFormatter getFormatter(String xpathFilter) throws XPathExpressionException;
}
