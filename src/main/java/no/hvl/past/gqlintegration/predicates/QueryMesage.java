package no.hvl.past.gqlintegration.predicates;

import no.hvl.past.graph.Diagram;
import no.hvl.past.graph.Graph;
import no.hvl.past.graph.GraphMorphism;
import no.hvl.past.names.Name;
import no.hvl.past.names.PrintingStrategy;
import no.hvl.past.systems.MessageArgument;
import no.hvl.past.systems.MessageType;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class QueryMesage extends GraphQLMessage {


    public QueryMesage(Graph schemaGraph, Name msgName, String containerObjectName, String operationName, List<MessageArgument> arguments) {
        super(schemaGraph, msgName, containerObjectName, operationName, arguments);
    }

    @Override
    public boolean isQuery() {
        return true;
    }

    @Override
    public boolean isMutation() {
        return false;
    }


    @Override
    public QueryMesage substitue(GraphMorphism morphism) {
        Name newName = morphism.map(typeName()).get();
        String messageNameFull = newName.print(PrintingStrategy.IGNORE_PREFIX);
        String containerType = messageNameFull.substring(0, messageNameFull.indexOf('.'));
        String operationName = messageNameFull.substring(messageNameFull.indexOf('.') + 1);
        List<MessageArgument> substitutedArguments = new ArrayList<>();
        QueryMesage substituted = new QueryMesage(morphism.codomain(), newName, containerType, operationName,substitutedArguments);
        for (MessageArgument arg : getArguments()) {
            substitutedArguments.add(arg.substitue(morphism, substituted));
        }
        return substituted;
    }
}
