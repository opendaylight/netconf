/*
 * Copyright (c) 2017 Pantheon Technologies s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.mdsal.connector.ops.get;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.opendaylight.controller.config.util.xml.DocumentedException;
import org.opendaylight.controller.config.util.xml.MissingNameSpaceException;
import org.opendaylight.controller.config.util.xml.XmlElement;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;

class Filter {

    private static final String FILTER = "filter";

    private final FilterContentValidator validator;

    Filter(final FilterContentValidator validator) {
        this.validator = validator;
    }

    /**
     * Transforms filter element to collection of non-overlapping sub-filters in form of {@link YidFilter}. It contains
     * also computed root {@link YangInstanceIdentifier} for each sub-filter. If filter isn't present,
     * returns one {@link YidFilter} with root path and null filter.
     *
     * @param operationElement filter
     * @return collection of sub-filters.
     * @throws DocumentedException if filter is invalid
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

            Collection<YidFilter> returnList = new ArrayList<>();
            for (List<XmlElement> list : rootToFilters.values()) {
                XmlElement result = merge(list);
                YidFilter yidFilter = createYidFilter(result);
                returnList.add(yidFilter);

            }
            return returnList;

        } catch (FilterContentValidator.ValidationException | DocumentedException | URISyntaxException e) {
            throw (DocumentedException) e.getCause();
        }
    }

    private YidFilter createYidFilter(final XmlElement filter) {
        return new YidFilter(getIdFromFilter(filter), filter);
    }

    private QName getFilterRootQName(final XmlElement filter) {
        try {
            return QName.create(filter.getNamespace(), filter.getName());
        } catch (final MissingNameSpaceException e) {
            throw new UncheckedDocumentedException(e);
        }
    }

    private YangInstanceIdentifier getIdFromFilter(final XmlElement filter) {
        try {
            return validator.validate(filter);
        } catch (final DocumentedException e) {
            throw new UncheckedDocumentedException(e);
        }
    }

    private XmlElement merge(final List<XmlElement> idFilters) throws
            FilterContentValidator.ValidationException, DocumentedException, URISyntaxException {
        if (idFilters.size() == 1) {
            return idFilters.get(0);
        }
        FilterCombiner combiner = new FilterCombiner();
        for (final XmlElement idFilter : idFilters) {
            FilterTree filterTree = validator.validateAndCreateFilterTree(idFilter);
            combiner.combine(idFilter, filterTree);
        }
        return combiner.getResult();
    }

    private static class UncheckedDocumentedException extends RuntimeException {

        private UncheckedDocumentedException(final DocumentedException cause) {
            super(cause);
        }
    }

    /**
     * Crate containing {@link XmlElement} with filter content and its root {@link YangInstanceIdentifier}.
     */
    static class YidFilter {
        private final YangInstanceIdentifier path;
        private final XmlElement filter;

        YidFilter(final YangInstanceIdentifier path, @Nullable final XmlElement filter) {
            this.path = Preconditions.checkNotNull(path);
            this.filter = filter;
        }

        YangInstanceIdentifier getPath() {
            return path;
        }

        @Nullable
        XmlElement getFilter() {
            return filter;
        }
    }
}
