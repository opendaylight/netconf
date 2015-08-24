/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.cli.commands;

import com.google.common.base.Optional;
import org.opendaylight.netconf.cli.commands.input.Input;
import org.opendaylight.netconf.cli.commands.input.InputDefinition;
import org.opendaylight.netconf.cli.commands.output.Output;
import org.opendaylight.netconf.cli.commands.output.OutputDefinition;
import org.opendaylight.netconf.cli.io.ConsoleContext;
import org.opendaylight.yangtools.yang.common.QName;

/**
 * Local command e.g. help or remote rpc e.g. get-config must conform to this interface
 */
public interface Command {

    Output invoke(Input inputArgs) throws CommandInvocationException;

    InputDefinition getInputDefinition();

    OutputDefinition getOutputDefinition();

    QName getCommandId();

    Optional<String> getCommandDescription();

    ConsoleContext getConsoleContext();
}
