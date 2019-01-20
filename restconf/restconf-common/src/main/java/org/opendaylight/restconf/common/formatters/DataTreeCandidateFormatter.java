/*
 * Copyright (c) 2019 Pantheon Technologies, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.common.formatters;

import java.io.IOException;
import java.util.Collection;
import javax.xml.xpath.XPathExpressionException;
import org.opendaylight.yangtools.yang.data.api.schema.tree.DataTreeCandidate;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.w3c.dom.Document;

public abstract class DataTreeCandidateFormatter extends EventFormatter<Collection<DataTreeCandidate>> {

    DataTreeCandidateFormatter() {

    }

    DataTreeCandidateFormatter(final String xpathFilter) throws XPathExpressionException {
        super(xpathFilter);
    }

    @Override
    void fillDocument(final Document doc, final SchemaContext schemaContext, final Collection<DataTreeCandidate> input)
            throws IOException {
        // TODO Auto-generated method stub

    }

}
