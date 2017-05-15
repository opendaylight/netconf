/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.cli.commands;

import com.google.common.base.Optional;
import jline.console.completer.Completer;
import jline.console.completer.NullCompleter;
import org.opendaylight.netconf.cli.commands.input.InputDefinition;
import org.opendaylight.netconf.cli.commands.output.OutputDefinition;
import org.opendaylight.netconf.cli.io.ConsoleContext;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;

public abstract class AbstractCommand implements Command {

    private final QName qualifiedName;
    private final InputDefinition args;
    private final OutputDefinition output;
    private final String description;

    public AbstractCommand(final QName qualifiedName, final InputDefinition args, final OutputDefinition output,
            final String description) {
        this.qualifiedName = qualifiedName;
        this.args = args;
        this.output = output;
        this.description = description;
    }

    protected static OutputDefinition getOutputDefinition(final RpcDefinition rpcDefinition) {
        final ContainerSchemaNode output = rpcDefinition.getOutput();
        return output != null ? OutputDefinition.fromOutput(output) : OutputDefinition.empty();
    }

    @Override
    public OutputDefinition getOutputDefinition() {
        return output;
    }

    protected static InputDefinition getInputDefinition(final RpcDefinition rpcDefinition) {
        final ContainerSchemaNode input = rpcDefinition.getInput();
        return InputDefinition.fromInput(input);
    }

    @Override
    public InputDefinition getInputDefinition() {
        return args;
    }

    @Override
    public QName getCommandId() {
        return qualifiedName;
    }

    @Override
    public ConsoleContext getConsoleContext() {
        return new ConsoleContext() {

            @Override
            public Completer getCompleter() {
                return new NullCompleter();
            }

            @Override
            public Optional<String> getPrompt() {
                return Optional.of(qualifiedName.getLocalName());
            }
        };
    }

    @Override
    public Optional<String> getCommandDescription() {
        return Optional.fromNullable(description);
    }
}
