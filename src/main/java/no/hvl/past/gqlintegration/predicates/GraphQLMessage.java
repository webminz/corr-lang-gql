package no.hvl.past.gqlintegration.predicates;

import no.hvl.past.graph.Graph;
import no.hvl.past.names.Name;
import no.hvl.past.systems.MessageArgument;
import no.hvl.past.systems.MessageType;

import java.util.List;
import java.util.stream.Stream;

public abstract class GraphQLMessage implements MessageType {

    private final String containerObjectName;
    private final String operationName;
    private final Graph schemaGraph;
    private final Name msgName;
    private final List<MessageArgument> arguments;

    GraphQLMessage(Graph schemaGraph, Name msgName, String containerObjectName, String operationName, List<MessageArgument> arguments) {
        this.schemaGraph = schemaGraph;
        this.msgName = msgName;
        this.arguments = arguments;
        this.containerObjectName = containerObjectName;
        this.operationName = operationName;
    }


    public String getContainerObjectName() {
        return containerObjectName;
    }

    public String getOperationName() {
        return operationName;
    }

    public abstract boolean isQuery();

    public abstract boolean isMutation();

    List<MessageArgument> getArguments() {
        return arguments;
    }

    @Override
    public Stream<MessageArgument> arguments() {
        return arguments.stream();
    }

    @Override
    public Graph carrier() {
        return schemaGraph;
    }

    @Override
    public Name typeName() {
        return msgName;
    }
}
