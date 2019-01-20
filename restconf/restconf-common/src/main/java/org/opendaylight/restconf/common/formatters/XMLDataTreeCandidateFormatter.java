/*
 * Copyright (c) 2019 Pantheon Technologies, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.common.formatters;

import java.io.IOException;
import java.time.Instant;
import java.util.Collection;
import javax.xml.xpath.XPathExpressionException;
import org.opendaylight.yangtools.yang.data.api.schema.tree.DataTreeCandidate;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

public final class XMLDataTreeCandidateFormatter extends DataTreeCandidateFormatter {
    private static final XMLDataTreeCandidateFormatter INSTANCE = new XMLDataTreeCandidateFormatter();

    public static final EventFormatterFactory<Collection<DataTreeCandidate>> FACTORY =
            new EventFormatterFactory<Collection<DataTreeCandidate>>() {
        @Override
        public XMLDataTreeCandidateFormatter getFormatter(final String xpathFilter) throws XPathExpressionException {
            return new XMLDataTreeCandidateFormatter(xpathFilter);
        }

        @Override
        public XMLDataTreeCandidateFormatter getFormatter() {
            return INSTANCE;
        }
    };

    private XMLDataTreeCandidateFormatter() {

    }

    private XMLDataTreeCandidateFormatter(final String xpathFilter) throws XPathExpressionException {
        super(xpathFilter);
    }

    @Override
    String createText(final SchemaContext schemaContext, final Collection<DataTreeCandidate> input, final Instant now)
            throws IOException {
        // TODO Auto-generated method stub
        return null;
    }
}
