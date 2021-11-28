package no.hvl.past.gqlintegration.schema;

import no.hvl.past.systems.MessageType;

import java.util.ArrayList;
import java.util.List;

public class EntryPointType {

    private String name;
    private final List<MessageType> fields = new ArrayList<>();

    public EntryPointType(String name) {
        this.name = name;
    }

    public void addMessage(MessageType field) {
        this.fields.add(field);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<MessageType> getFields() {
        return fields;
    }
}
