/*
 * Copyright (c) 2017 Pantheon Technologies s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.mdsal.connector.ops.get;

import com.google.common.base.Optional;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.opendaylight.controller.config.util.xml.DocumentedException;
import org.opendaylight.controller.config.util.xml.MissingNameSpaceException;
import org.opendaylight.controller.config.util.xml.XmlElement;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;

class Filter {

    private static final YangInstanceIdentifier ROOT = YangInstanceIdentifier.EMPTY;
    private static final String FILTER = "filter";

    private final FilterContentValidator validator;

    Filter(FilterContentValidator validator) {
        this.validator = validator;
    }

    /**
     * Obtain data root according to filter from operation element.
     *
     * @param operationElement operation element
     * @return if filter is present and not empty returns Optional of the InstanceIdentifier to the read location
     *      in datastore. Empty filter returns Optional.absent() which should equal an empty &lt;data/&gt;
     *      container in the response. If filter is not present we want to read the entire datastore - return ROOT.
     * @throws DocumentedException if not possible to get identifier from filter
     */
    Collection<YidFilter> getDataRootsFromFilter(final XmlElement operationElement)
            throws DocumentedException {
        try {
            final Optional<XmlElement> filter = operationElement.getOnlyChildElementOptionally(FILTER);
            if (!filter.isPresent()) {
                return Collections.singleton(new YidFilter(YangInstanceIdentifier.EMPTY, null));
            }
            final List<XmlElement> filters = filter.get().getChildElements();
            final Map<QName, List<XmlElement>> rootToFilters = filters.stream()
                    .collect(Collectors.groupingBy(this::getFilterRootQName));
            return rootToFilters.values().stream()
                    .map(this::merge)
                    .map(this::createYidFilter)
                    .collect(Collectors.toList());

        } catch (UncheckedDocumentedException e) {
            throw (DocumentedException) e.getCause();
        }
    }

    private YidFilter createYidFilter(XmlElement filter) {
        return new YidFilter(getIdFromFilter(filter), filter);
    }

    private QName getFilterRootQName(XmlElement filter) {
        try {
            return QName.create(filter.getNamespace(), filter.getName());
        } catch (MissingNameSpaceException e) {
            throw new UncheckedDocumentedException(e);
        }
    }

    private YangInstanceIdentifier getIdFromFilter(XmlElement filter) {
        try {
            return validator.validate(filter);
        } catch (DocumentedException e) {
            throw new UncheckedDocumentedException(e);
        }
    }

    private XmlElement merge(List<XmlElement> idFilters) {
        if (idFilters.size() != 1) {
            throw new UncheckedDocumentedException(
                    new DocumentedException("Multiple filters with same root not supported"));
        }
        return idFilters.get(0);
    }

    private static class UncheckedDocumentedException extends RuntimeException {

        private UncheckedDocumentedException(DocumentedException cause) {
            super(cause);
        }
    }

    static class YidFilter {
        private YangInstanceIdentifier path;
        private XmlElement filter;

        YidFilter(YangInstanceIdentifier path, XmlElement filter) {
            this.path = path;
            this.filter = filter;
        }

        YangInstanceIdentifier getPath() {
            return path;
        }

        XmlElement getFilter() {
            return filter;
        }
    }
}
