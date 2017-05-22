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
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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

    @SuppressWarnings("illegalCatch")
    Collection<YidFilter> getDataRootsFromFilter(final XmlElement operationElement) throws DocumentedException {
        try {
            final Optional<XmlElement> filter = operationElement.getOnlyChildElementOptionally(FILTER);
            if (!filter.isPresent()) {
                return Collections.singleton(new YidFilter(YangInstanceIdentifier.EMPTY, null));
            }

            Map<QName, List<XmlElement>> groupedElements = groupByRootElements(filter.get().getChildElements());

            return mergedYidFilters(groupedElements);

        } catch (Exception e) {
            throw new DocumentedException(e.getMessage(),
                    DocumentedException.ErrorType.PROTOCOL,
                    DocumentedException.ErrorTag.OPERATION_FAILED,
                    DocumentedException.ErrorSeverity.ERROR);
        }
    }

    private Collection<YidFilter> mergedYidFilters(Map<QName, List<XmlElement>> groupedElements) throws Exception {
        Collection<YidFilter> returnList = new ArrayList<>();
        for (List<XmlElement> list : groupedElements.values()) {
            XmlElement mergedElement = mergeGroupedElements(list);
            returnList.add(createYidFilter(mergedElement));
        }

        return returnList;
    }

    private Map<QName, List<XmlElement>> groupByRootElements(List<XmlElement> filters)
            throws MissingNameSpaceException {
        Map<QName, List<XmlElement>> rootToFilters = Maps.newHashMap();
        for (XmlElement xmlElement : filters) {
            QName qname = getFilterRootQName(xmlElement);
            List<XmlElement> list = rootToFilters.get(qname);
            if (list == null) {
                list = Lists.newArrayList();
            }
            list.add(xmlElement);
            rootToFilters.put(qname, list);
        }
        return rootToFilters;
    }

    private YidFilter createYidFilter(final XmlElement filter) throws DocumentedException {
        return new YidFilter(validator.validate(filter), filter);
    }

    private QName getFilterRootQName(final XmlElement filter) throws MissingNameSpaceException {
        return QName.create(filter.getNamespace(), filter.getName());
    }

    @SuppressWarnings("illegalCatch")
    private XmlElement mergeGroupedElements(final List<XmlElement> idFilters) throws Exception {
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
