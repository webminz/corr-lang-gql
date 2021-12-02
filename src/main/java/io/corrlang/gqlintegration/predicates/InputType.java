package io.corrlang.gqlintegration.predicates;

import no.hvl.past.graph.Graph;
import no.hvl.past.graph.GraphMorphism;
import no.hvl.past.graph.GraphPredicate;
import no.hvl.past.graph.Universe;

public class InputType implements GraphPredicate {

    private static InputType instance;

    private InputType() {

    }

    @Override
    public boolean check(GraphMorphism instance) {
        return true;
    }

    @Override
    public String nameAsString() {
        return "InputType";
    }

    @Override
    public Graph arity() {
        return Universe.ONE_NODE;
    }

    public static InputType getInstance() {
        if (instance == null) {
            instance = new InputType();
        }
        return instance;
    }
}
