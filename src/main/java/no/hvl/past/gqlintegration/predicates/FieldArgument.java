package no.hvl.past.gqlintegration.predicates;

import no.hvl.past.graph.Graph;
import no.hvl.past.graph.GraphMorphism;
import no.hvl.past.graph.GraphPredicate;
import no.hvl.past.graph.Universe;
import no.hvl.past.names.Name;

import java.util.Objects;

public class FieldArgument implements GraphPredicate {

    private final String fieldName;
    private final Name typeName;
    private boolean isListValued;
    private boolean isMandatory;

    public FieldArgument(String fieldName, Name typeName, boolean isListValued, boolean isMandatory) {
        this.fieldName = fieldName;
        this.typeName = typeName;
        this.isListValued = isListValued;
        this.isMandatory = isMandatory;
    }

    @Override
    public boolean check(GraphMorphism instance) {
        return true; // right now, we have no further information about this
    }

    @Override
    public String nameAsString() {
        return "@{" + fieldName + " : " + typeName + "}";
    }

    @Override
    public Graph arity() {
        return Universe.ARROW;
    }

    public String getFieldName() {
        return fieldName;
    }

    public Name getTypeName() {
        return typeName;
    }

    public boolean isListValued() {
        return isListValued;
    }

    public boolean isMandatory() {
        return isMandatory;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FieldArgument argument = (FieldArgument) o;
        return isListValued == argument.isListValued &&
                isMandatory == argument.isMandatory &&
                fieldName.equals(argument.fieldName) &&
                typeName.equals(argument.typeName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fieldName, typeName, isListValued, isMandatory);
    }
}
