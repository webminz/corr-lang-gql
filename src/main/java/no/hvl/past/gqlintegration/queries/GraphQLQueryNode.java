package no.hvl.past.gqlintegration.queries;

import com.fasterxml.jackson.databind.JsonNode;
import no.hvl.past.attributes.TypedVariables;
import no.hvl.past.graph.Sketch;
import no.hvl.past.graph.elements.Triple;
import no.hvl.past.graph.trees.QueryNode;
import no.hvl.past.logic.Formula;
import no.hvl.past.names.Name;
import no.hvl.past.names.PrintingStrategy;
import no.hvl.past.util.StringUtils;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GraphQLQueryNode implements QueryNode {

    private final Name field;
    private final Map<String, String> arguments;
    private final List<GraphQLQueryNode> children;

    public GraphQLQueryNode(Name field, Map<String, String> arguments, List<GraphQLQueryNode> children) {
        this.field = field;
        this.arguments = arguments;
        this.children = children;
    }

    public static GraphQLQueryNode parse(Sketch schema, Map<Name, String> plainNames, Name currentType, String query, Name rootField) throws IOException {
        String rest = query.trim();
        if (currentType.equals(Name.identifier("Schema"))) {
            int firstIdx = rest.indexOf('{');
            int lastIdx = rest.lastIndexOf('}');
            String toParse = rest.substring(firstIdx + 1, lastIdx).trim();
            if (rest.startsWith("query")) {
                return parse(schema, plainNames, Name.identifier("Query"), toParse, Name.identifier("query"));
            } else if (rest.startsWith("mutation")) {
                return parse(schema, plainNames, Name.identifier("Mutation"), toParse, Name.identifier("mutation"));
            } else if (rest.startsWith("subscription")) {
                return parse(schema, plainNames, Name.identifier("Subscription"), toParse, Name.identifier("subscription"));
            } else {
                throw new IOException("Root query '" + query + "' is not supported");
            }
        } else if (rootField != null) {
            Parser parser = new Parser(schema, plainNames, currentType, new Builder(rootField));
            while (!rest.isEmpty()) {
                parser.accept(rest.charAt(0));
                rest = rest.substring(1);
            }
            return parser.getResult();
        } else {
            throw new IOException("Cannot parse query! Do not know at what type (entry point) to start!");
        }
    }



    @Override
    public Name filteredElementName() {
        return field;
    }

    @Override
    public Formula<TypedVariables> filterPredicate() {
        return Formula.top();
    }

    @Override
    public Stream<QueryNode> children() {
        return children.stream().map(t -> t);
    }

    public void print(StringBuilder sink, int nestingLevel, Map<Name, String> nameToText) {
        sink.append(StringUtils.produceIndentation(nestingLevel));
        sink.append(resolveName(nameToText));
        sink.append(' ');
        if (!arguments.isEmpty()) {
            sink.append('(');
            Iterator<String> it = arguments.keySet().iterator();
            while (it.hasNext()) {
                String current = it.next();
                sink.append(current);
                sink.append(" : \"");
                sink.append(arguments.get(current));
                sink.append('"');
                if (it.hasNext()) {
                    sink.append(", ");
                }
            }
            sink.append(") ");
        }
        if (!children.isEmpty()) {
            sink.append(" {\n");
            for (GraphQLQueryNode qNode :children) {
                qNode.print(sink, nestingLevel +1, nameToText);
            }
            sink.append(StringUtils.produceIndentation(nestingLevel));
            sink.append("}\n");
        } else {
            sink.append('\n');
        }
    }

    private String resolveName(Map<Name, String> nameToText) {
        if (nameToText != null) {
            if (nameToText.containsKey(field)) {
                return nameToText.get(field);
            }
        }
        return field.print(PrintingStrategy.IGNORE_PREFIX);
    }

    public Name getField() {
        return field;
    }

    public Map<String, String> getArguments() {
        return arguments;
    }

    public List<GraphQLQueryNode> getChildren() {
        return children;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GraphQLQueryNode that = (GraphQLQueryNode) o;
        return field.equals(that.field) &&
                arguments.equals(that.arguments) &&
                children.equals(that.children);
    }

    @Override
    public int hashCode() {
        return Objects.hash(field, arguments, children);
    }

    public String serialize(Map<Name, String> nameToText) {
        StringBuilder b = new StringBuilder();
        print(b, 0, nameToText);
        return b.toString();
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        print(b, 0, null);
        return b.toString();
    }


    private enum ParserState {
        AWAITING_FIELD,
        IN_FIELD,
        AFTER_FIELD,
        AWAIT_PARAM,
        IN_PARAM,
        AWAIT_VALUE,
        IN_VALUE
    }


    private static class Parser {
        private ParserState parserState;
        private StringBuilder nameBuilder;
        private StringBuilder argumentBuilder;
        private Builder currentBuilder;
        private Sketch schema;
        private Name currentType;
        private Map<Name, String> plainNames;

        private static final String SPECIAL_CHARS = "{}(),:\"";

        public Parser(Sketch sketch, Map<Name, String> plainNames, Name currentType, Builder startBuilder) {
            this.schema = sketch;
            this.currentType = currentType;
            this.currentBuilder = startBuilder;
            this.parserState = ParserState.AWAITING_FIELD;
            this.nameBuilder = new StringBuilder();
            this.argumentBuilder = new StringBuilder();
            this.plainNames = plainNames;
        }


        public GraphQLQueryNode getResult() {
            while (this.currentBuilder.parent != null) {
                this.currentBuilder = this.currentBuilder.parent;
            }
            return this.currentBuilder.build();
        }

        public void accept(char charAt) throws IOException {
            switch (this.parserState) {
                case AWAITING_FIELD:
                    if (isRegularCharacter(charAt)) {
                        this.parserState = ParserState.IN_FIELD;
                        this.nameBuilder.append(charAt);
                    }
                    break;
                case IN_FIELD:
                    if (Character.isWhitespace(charAt)) {
                        this.parserState = ParserState.AFTER_FIELD;
                    } else if (SPECIAL_CHARS.contains(charAt + "")) {
                        this.parserState = ParserState.AFTER_FIELD;
                        this.accept(charAt);
                    } else {
                        this.nameBuilder.append(charAt);
                    }
                    break;
                case AFTER_FIELD:
                    if (charAt == '(') {
                        this.finishField();
                        this.parserState = ParserState.AWAIT_PARAM;
                    } else if (charAt == '{') {
                        Builder b = this.finishField();
                        this.updateCurrentType(b.field);
                        this.currentBuilder = b;
                        this.parserState = ParserState.AWAITING_FIELD;
                    } else if (charAt == '}') {
                        this.finishField();
                        this.currentBuilder = this.currentBuilder.endChild();
                        this.updateCurrentType(currentBuilder.field);
                        this.parserState = ParserState.AWAITING_FIELD;
                    } else if (isRegularCharacter(charAt)) {
                        this.finishField();
                        this.nameBuilder.append(charAt);
                        this.parserState = ParserState.IN_FIELD;
                    }
                    break;
                case AWAIT_PARAM:
                    if (charAt == ')') {
                        this.parserState = ParserState.AFTER_FIELD;
                    } else if (isRegularCharacter(charAt)) {
                        this.parserState = ParserState.IN_PARAM;
                        this.nameBuilder.append(charAt);
                    }
                    break;
                case IN_PARAM:
                    if (Character.isWhitespace(charAt) || charAt == ',') {
                        this.parserState = ParserState.AWAIT_VALUE;
                    } else if (isRegularCharacter(charAt)) {
                        this.nameBuilder.append(charAt);
                    }
                    break;
                case AWAIT_VALUE:
                    if (charAt == '"') {
                        this.argumentBuilder.append(charAt);
                        this.parserState = ParserState.IN_VALUE;
                    } else if (isRegularCharacter(charAt)) {
                        this.argumentBuilder.append(charAt);
                        this.parserState = ParserState.IN_VALUE;
                    }
                    break;
                case IN_VALUE:
                    if (charAt == '"' || charAt == ')' || Character.isWhitespace(charAt)) {
                        String key = nameBuilder.toString();
                        nameBuilder = new StringBuilder();
                        String value = argumentBuilder.toString();
                        argumentBuilder = new StringBuilder();
                        if (value.startsWith("\"")) {
                            value = value.substring(1, value.length() - 1);
                        }
                        this.currentBuilder.argument(key, value);
                        if (charAt == ')') {
                            this.parserState = ParserState.AFTER_FIELD;
                        } else {
                            this.parserState = ParserState.AWAIT_PARAM;
                        }
                    }
                    break;
            }
        }

        private void updateCurrentType(Name field) {
            if (field.equals(Name.identifier("query"))) {
                this.currentType = Name.identifier("Query");
            } else if (field.equals(Name.identifier("mutation"))) {
                this.currentType = Name.identifier("Mutation");
            } else if (field.equals(Name.identifier("subscription"))) {
                this.currentType = Name.identifier("Subscription");
            } else {
                this.currentType = schema.carrier().get(field).get().getTarget();
            }
        }

        private boolean isRegularCharacter(char charAt) {
            return !Character.isWhitespace(charAt) && !SPECIAL_CHARS.contains(charAt + "");
        }

        private Builder finishField() throws IOException {
            String field = this.nameBuilder.toString();
            this.nameBuilder = new StringBuilder();
            Optional<Triple> first = this.schema.carrier().outgoing(currentType).filter(Triple::isEddge)
                    .filter(edge -> {
                        if (!plainNames.containsKey(edge.getLabel())) {
                            System.out.println("Could not find: " + edge.getLabel().print(PrintingStrategy.DETAILED));
                        }
                        return plainNames.get(edge.getLabel()).equals(field);
                    }).findFirst();
            if (first.isPresent()) {
                return this.currentBuilder.startChild(first.get().getLabel());
            } else {
                throw new IOException("Field '" + field + "' is not found in type '" + currentType.print(PrintingStrategy.IGNORE_PREFIX) + "'!");
            }
        }
    }

    public static class Builder {

        private Builder parent;
        private Name field;
        private Map<String, String> arguments;
        private List<Builder> children;

        public Builder(Builder parent, Name field) {
            this.parent = parent;
            this.field = field;
            this.arguments = new HashMap<>();
            this.children = new ArrayList<>();
        }

        public Builder(Name field) {
            this.field = field;
            this.arguments = new HashMap<>();
            this.children = new ArrayList<>();
        }

        public Builder argument(String key, String value) {
            this.arguments.put(key, value);
            return this;
        }

        public Builder startChild(Name field) {
            Builder child = new Builder(this, field);
            this.children.add(child);
            return child;
        }

        public Builder endChild() {
            if (parent != null) {
                return parent;
            }
            return this;
        }

        public GraphQLQueryNode build() {
            List<GraphQLQueryNode> c = this.children.stream().map(Builder::build).collect(Collectors.toList());
            return new GraphQLQueryNode(field, arguments, c);
        }


    }
}
