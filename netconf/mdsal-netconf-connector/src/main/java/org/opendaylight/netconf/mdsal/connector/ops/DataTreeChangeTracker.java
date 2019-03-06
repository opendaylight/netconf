/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.mdsal.connector.ops;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.opendaylight.yangtools.yang.data.api.ModifyAction;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

public class DataTreeChangeTracker {
    private final Deque<ModifyAction> actions = new ArrayDeque<>();
    private final Deque<PathArgument> currentPath = new ArrayDeque<>();
    private final List<DataTreeChange> dataTreeChanges = new ArrayList<>();
    private final ModifyAction defaultAction;

    private int deleteOperationTracker = 0;
    private int removeOperationTracker = 0;

    public DataTreeChangeTracker(final ModifyAction defaultAction) {
        this.defaultAction = requireNonNull(defaultAction);
    }

    public void pushAction(final ModifyAction action) {
        switch (action) {
            case DELETE:
                deleteOperationTracker++;
                break;
            case REMOVE:
                removeOperationTracker++;
                break;
            default:
                // no-op
        }

        actions.push(action);
    }

    // Returns nullable
    public ModifyAction peekAction() {
        return actions.peekFirst();
    }

    public ModifyAction currentAction() {
        final ModifyAction stack = peekAction();
        return stack != null ? stack : defaultAction;
    }

    public ModifyAction popAction() {
        final ModifyAction popResult = actions.pop();
        switch (popResult) {
            case DELETE:
                deleteOperationTracker--;
                break;
            case REMOVE:
                removeOperationTracker--;
                break;
            default:
                // no-op
        }
        return popResult;
    }

    public int getDeleteOperationTracker() {
        return deleteOperationTracker;
    }

    public int getRemoveOperationTracker() {
        return removeOperationTracker;
    }

    public void addDataTreeChange(final ModifyAction action, final NormalizedNode<?, ?> changeRoot) {
        dataTreeChanges.add(new DataTreeChange(changeRoot, action, currentPath));
    }

    public List<DataTreeChange> getDataTreeChanges() {
        return dataTreeChanges;
    }

    public void pushPath(final PathArgument pathArgument) {
        currentPath.push(pathArgument);
    }

    public PathArgument popPath() {
        return currentPath.pop();
    }

    public static final class DataTreeChange {
        private final NormalizedNode<?, ?> changeRoot;
        private final YangInstanceIdentifier path;
        private final ModifyAction action;

        DataTreeChange(final NormalizedNode<?, ?> changeRoot, final ModifyAction action,
                final Deque<PathArgument> path) {
            this.changeRoot = requireNonNull(changeRoot);
            this.action = requireNonNull(action);

            final Builder<PathArgument> builder = ImmutableList.builderWithExpectedSize(path.size());
            path.descendingIterator().forEachRemaining(builder::add);
            this.path = YangInstanceIdentifier.create(builder.build());
        }

        public NormalizedNode<?, ?> getChangeRoot() {
            return changeRoot;
        }

        public ModifyAction getAction() {
            return action;
        }

        public YangInstanceIdentifier getPath() {
            return path;
        }
    }
}
