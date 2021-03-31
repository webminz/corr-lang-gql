package no.hvl.past.gqlintegration.schema;

import no.hvl.past.names.Name;

public class FieldMult {
    private final Name elementName;
    private final boolean listValued;
    private final boolean mandatory;

    FieldMult(Name elementName, boolean listValued, boolean mandatory) {
        this.elementName = elementName;
        this.listValued = listValued;
        this.mandatory = mandatory;
    }

    public Name getElementName() {
        return elementName;
    }

    public boolean isListValued() {
        return listValued;
    }

    public boolean isMandatory() {
        return mandatory;
    }
}
