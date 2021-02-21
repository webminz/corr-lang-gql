package no.hvl.past.gqlintegration.schema;



import graphql.schema.*;
import no.hvl.past.gqlintegration.caller.GraphQLCaller;
import no.hvl.past.gqlintegration.predicates.FieldArgument;
import no.hvl.past.graph.*;
import no.hvl.past.graph.elements.Triple;
import no.hvl.past.graph.predicates.*;
import no.hvl.past.names.Identifier;
import no.hvl.past.names.Name;
import no.hvl.past.names.PrintingStrategy;
import no.hvl.past.util.Pair;


import java.util.*;
import java.util.stream.Collectors;

public class GraphQLSchemaReader {

    private static class FieldMult {
        private final Name elementName;
        private final boolean listValued;
        private final boolean mandatory;

        FieldMult(Name elementName, boolean listValued, boolean mandatory) {
            this.elementName = elementName;
            this.listValued = listValued;
            this.mandatory = mandatory;
        }
    }

    private final GraphBuilders builders;
    private boolean listValued;
    private boolean mandatory;
    private Map<GraphQLType, Name> typeMapping;
    private List<Triple> edges;
    private Set<String> scalarTypes;
    private List<FieldMult> multiplicities ;
    private List<Pair<GraphQLArgument, Triple>> arguments;
    private Map<Name, String> nameToText;

    public GraphQLSchemaReader(Universe universe) {
        this.builders = new GraphBuilders(universe, false, false);

    }

    public Sketch convert(Name resultName, GraphQLSchema schema) throws GraphError {
        this.typeMapping = new HashMap<>();
        this.edges = new ArrayList<>();
        this.scalarTypes = new HashSet<>();
        this.multiplicities = new ArrayList<>();
        this.arguments = new ArrayList<>();
        this.listValued = false;
        this.mandatory = false;
        this.nameToText = new HashMap<>();

        Set<GraphQLNamedType> types = schema.getAllTypesAsList().stream()
                .filter(t -> !t.getName().startsWith(GraphQLCaller.GRAPHQL_META_INFO_PREFIX))
                .collect(Collectors.toSet());

        List<GraphQLObjectType> complexTypes = new ArrayList<>();

        // Nodes for all types
        for (GraphQLNamedType type : types) {
            Name typeName = createName(type);
            builders.node(typeName);
            this.typeMapping.put(type, typeName);

            if (type instanceof GraphQLObjectType) {
                complexTypes.add((GraphQLObjectType) type);
            }

            if (type instanceof GraphQLScalarType) {
                scalarTypes.add(type.getName());
            }

            // TODO enums

            // TODO interfaces & unions
        }

        // Edges for all filed
        for (GraphQLObjectType object : complexTypes) {
            for (GraphQLFieldDefinition field : object.getFieldDefinitions()) {
                convertField(field, object);
            }
        }

        builders.graph(resultName.absolute());

        for (String scalarType : scalarTypes) {
            handleScalarType(scalarType);
        }

        for (FieldMult multiplicity : this.multiplicities) {
            if (multiplicity.listValued) {
                // add ordered
                this.edges.stream().filter(t -> t.getLabel().equals(multiplicity.elementName)).findFirst().ifPresent(edge -> {
                    builders.startDiagram(Ordered.getInstance());
                    builders.map(Universe.ARROW_SRC_NAME, edge.getSource());
                    builders.map(Universe.ARROW_LBL_NAME, edge.getLabel());
                    builders.map(Universe.ARROW_TRG_NAME, edge.getTarget());
                    builders.endDiagram(Ordered.getInstance().getName().appliedTo(multiplicity.elementName));
                });
            } else if (multiplicity.mandatory) {
                // add 1..1
                this.edges.stream().filter(t -> t.getLabel().equals(multiplicity.elementName)).findFirst().ifPresent(edge -> {
                    GraphPredicate pred = TargetMultiplicity.getInstance(1, 1);
                    builders.startDiagram(pred);
                    builders.map(Universe.ARROW_SRC_NAME, edge.getSource());
                    builders.map(Universe.ARROW_LBL_NAME, edge.getLabel());
                    builders.map(Universe.ARROW_TRG_NAME, edge.getTarget());
                    builders.endDiagram(pred.getName().appliedTo(multiplicity.elementName));
                });
            } else {
                // add 0..1
                this.edges.stream().filter(t -> t.getLabel().equals(multiplicity.elementName)).findFirst().ifPresent(edge -> {
                    GraphPredicate pred = TargetMultiplicity.getInstance(0, 1);
                    builders.startDiagram(pred);
                    builders.map(Universe.ARROW_SRC_NAME, edge.getSource());
                    builders.map(Universe.ARROW_LBL_NAME, edge.getLabel());
                    builders.map(Universe.ARROW_TRG_NAME, edge.getTarget());
                    builders.endDiagram(pred.getName().appliedTo(multiplicity.elementName));
                });
            }
        }

        for (Pair<GraphQLArgument, Triple> a : arguments) {
            this.convertArgument(a.getFirst(), a.getSecond());
        }

        builders.sketch(resultName);
        return builders.getResult(Sketch.class);
    }

    private void handleScalarType(String scalarType) {
        this.nameToText.put(Name.identifier(scalarType), scalarType);
        switch (scalarType) {
            case "Int":
                builders.startDiagram(IntDT.getInstance());
                builders.map(Universe.ONE_NODE_THE_NODE, Name.identifier(scalarType));
                builders.endDiagram(Name.identifier(scalarType + " scalar type"));
                break;
            case "Float":
                builders.startDiagram(FloatDT.getInstance());
                builders.map(Universe.ONE_NODE_THE_NODE, Name.identifier(scalarType));
                builders.endDiagram(Name.identifier(scalarType + " scalar type"));
                break;
            case "Boolean":
                builders.startDiagram(BoolDT.getInstance());
                builders.map(Universe.ONE_NODE_THE_NODE, Name.identifier(scalarType));
                builders.endDiagram(Name.identifier(scalarType + " scalar type"));
                break;
            case "String":
            case "ID":
                builders.startDiagram(StringDT.getInstance());
                builders.map(Universe.ONE_NODE_THE_NODE, Name.identifier(scalarType));
                builders.endDiagram(Name.identifier(scalarType + " scalar type"));
                break;
            default:
                builders.startDiagram(DataTypePredicate.getInstance());
                builders.map(Universe.ONE_NODE_THE_NODE, Name.identifier(scalarType));
                builders.endDiagram(Name.identifier(scalarType + " scalar type"));
                break;

        }
    }


    private void convertField(final GraphQLFieldDefinition fieldDefinition, final GraphQLType owner) {
        this.mandatory = false;
        this.listValued = false;

        Name fieldName = createName(owner, fieldDefinition);

        GraphQLOutputType resultType = fieldDefinition.getType();
        Name targetName = this.convertResultType(resultType);

        final Triple edge = Triple.edge(this.typeMapping.get(owner), fieldName, targetName);
        this.builders.edge(this.typeMapping.get(owner), fieldName, targetName); // TODO builder method that accepts an edge
        this.edges.add(edge);
        this.multiplicities.add(new FieldMult(fieldName, listValued, mandatory));



        for (GraphQLArgument argument : fieldDefinition.getArguments()) {
            this.arguments.add(new Pair<>(argument, edge));
        }
    }

    private void convertArgument(GraphQLArgument argument, Triple edge) {
        String argumentName = argument.getName();
        this.mandatory = false;
        this.listValued = false;
        Name resultType = convertResultType(argument.getType());
        builders.startDiagram(new FieldArgument(argumentName, resultType, listValued, mandatory));
        builders.map(Universe.ARROW_SRC_NAME, edge.getSource());
        builders.map(Universe.ARROW_LBL_NAME, edge.getLabel());
        builders.map(Universe.ARROW_TRG_NAME, edge.getTarget());
        builders.endDiagram(Name.identifier("@arg(" + argumentName + " : " + resultType.print(PrintingStrategy.IGNORE_PREFIX) + ")"));
    }


    public Name convertResultType(GraphQLType resultType) {
        if (GraphQLNonNull.class.isAssignableFrom(resultType.getClass())) {
            this.mandatory = true;
            return convertResultType((GraphQLType) resultType.getChildren().get(0));
        } else if (GraphQLList.class.isAssignableFrom(resultType.getClass())) {
            this.listValued = true;
            return convertResultType((GraphQLType) resultType.getChildren().get(0));
        } else {
            return typeMapping.get(resultType);
        }
    }

    private Name createName(GraphQLType owner, GraphQLFieldDefinition fieldDefinition) {
        Name name = Name.identifier(fieldDefinition.getName()).prefixWith(this.typeMapping.get(owner));
        this.nameToText.put(name, fieldDefinition.getName());
        return name;
    }

    private Name createName(GraphQLNamedType type) {
        Identifier identifier = Name.identifier(type.getName());
        this.nameToText.put(identifier, type.getName());
        return identifier;
    }

    public Map<Name, String> getNameToText() {
        return nameToText;
    }
}
