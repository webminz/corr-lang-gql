package no.hvl.past.gqlintegration.schema;



import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.schema.*;
import no.hvl.past.gqlintegration.GraphQLEndpoint;
import no.hvl.past.gqlintegration.caller.IntrospectionQuery;
import no.hvl.past.gqlintegration.predicates.*;
import no.hvl.past.graph.*;
import no.hvl.past.graph.elements.Triple;
import no.hvl.past.graph.predicates.*;
import no.hvl.past.names.Identifier;
import no.hvl.past.names.Name;
import no.hvl.past.names.PrintingStrategy;
import no.hvl.past.plugin.UnsupportedFeatureException;
import no.hvl.past.systems.MessageArgument;
import no.hvl.past.systems.MessageContainer;
import no.hvl.past.util.Pair;


import java.util.*;
import java.util.stream.Collectors;

public class GraphQLSchemaReader {


    public String getQueryTypeName() {
        return this.queryTypeName;
    }

    public String getMutationTypeName() {
        return this.mutationTypeName;
    }

    private static class MessageType {
        private final Name name;
        private final boolean hasSideEffect;
        private final List<Triple> arguments;
        private final Triple returnType;
        private final MessageContainer container;


        public MessageType(Name name, boolean hasSideEffect, List<Triple> arguments, Triple returnType, MessageContainer container) {
            this.name = name;
            this.hasSideEffect = hasSideEffect;
            this.arguments = arguments;
            this.returnType = returnType;
            this.container = container;
        }
    }

    private static class EnumLiterals {
        private final List<Name> literals;

        public EnumLiterals(List<Name> literals) {
            this.literals = literals;
        }

        public List<Name> getLiterals() {
            return literals;
        }
    }

    private final GraphBuilders builders;
    private boolean listValued;
    private boolean mandatory;
    private Map<GraphQLType, Name> typeMapping;
    private List<Triple> edges;
    private Set<String> scalarTypes;
    private List<FieldMult> multiplicities;
    private Map<Name, EnumLiterals> enums;
    private List<Pair<GraphQLArgument, Triple>> arguments;
    private Map<Name, String> nameToText;
    private List<MessageType> messageTypes;
    private Set<GraphQLInputObjectType> inputTypes = new LinkedHashSet<>();
    private String queryTypeName = "Query";
    private String mutationTypeName = "Mutation";

    public GraphQLSchemaReader(Universe universe) {
        this.builders = new GraphBuilders(universe, false, false);

    }

    public GraphQLEndpoint convert(
            String url,
            Name resultName,
            GraphQLSchema schema,
            ObjectMapper om,
            JsonFactory jsonFactory) throws GraphError, UnsupportedFeatureException {
        this.typeMapping = new HashMap<>();
        this.edges = new ArrayList<>();
        this.scalarTypes = new HashSet<>();
        this.multiplicities = new ArrayList<>();
        this.arguments = new ArrayList<>();
        this.listValued = false;
        this.mandatory = false;
        this.nameToText = new HashMap<>();
        this.messageTypes = new ArrayList<>();
        this.enums = new LinkedHashMap<>();


        Set<GraphQLNamedType> types = schema.getAllTypesAsList().stream()
                .filter(t -> schema.getQueryType() == null || !schema.getQueryType().equals(t))
                .filter(t -> schema.getMutationType() == null || !schema.getMutationType().equals(t))
                .filter(t -> schema.getSubscriptionType() == null || !schema.getSubscriptionType().equals(t))
                .filter(t -> !t.getName().startsWith(IntrospectionQuery.GRAPHQL_META_INFO_PREFIX))
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

            if (type instanceof GraphQLEnumType) {
                GraphQLEnumType enm = (GraphQLEnumType) type;
                List<Name> result = new ArrayList<>();
                for (GraphQLEnumValueDefinition literal : enm.getValues()) {
                    result.add(Name.identifier(literal.getName()));
                }
                this.enums.put(typeName, new EnumLiterals(result));
            }


            if (type instanceof GraphQLInputObjectType) {
                GraphQLInputObjectType inputType = (GraphQLInputObjectType) type;
                this.inputTypes.add(inputType);
            }

            // TODO unions
            if (type instanceof GraphQLUnionType) {
                throw new UnsupportedFeatureException("Union types such as '" + type.getName() + "' are currently not supported by the GraphQL plugin!");
            }


            // TODO interfaces
            if (type instanceof GraphQLInterfaceType) {
                throw new UnsupportedFeatureException("Interfaces such as '" + type.getName() + "' are currently not supported by the GraphQL plugin!");
            }

        }

        // Edges for all fields
        for (GraphQLObjectType object : complexTypes) {
            for (GraphQLFieldDefinition field : object.getFieldDefinitions()) {
                convertField(field, object);
            }
        }

        for (GraphQLInputObjectType inputType : inputTypes) {
            for (GraphQLInputObjectField field : inputType.getFieldDefinitions()) {
                convertField(field, inputType);
            }
        }

        if (schema.getQueryType() != null) {
            if (!this.queryTypeName.equals(schema.getQueryType().getName())) {
                this.queryTypeName = schema.getQueryType().getName();
            }
            builders.node(queryTypeName);
            for (GraphQLFieldDefinition querOp : schema.getQueryType().getFieldDefinitions()) {
                convertMessage(querOp, false, new MessageContainer(Name.identifier(schema.getQueryType().getName())));
            }
        }

        if (schema.getMutationType() != null) {
            if (!this.mutationTypeName.equals(schema.getMutationType().getName())) {
                this.mutationTypeName = schema.getMutationType().getName();
            }
            builders.node(mutationTypeName);
            for (GraphQLFieldDefinition querOp : schema.getMutationType().getFieldDefinitions()) {
                convertMessage(querOp, true, new MessageContainer(Name.identifier(schema.getMutationType().getName())));
            }
        }

        if (schema.getSubscriptionType() != null && !schema.getSubscriptionType().getFieldDefinitions().isEmpty()) {
            throw new UnsupportedFeatureException("Subscriptions are currently not supported by the GraphQL plugin!");
        }


        builders.graph(resultName.absolute());

        // post-processing (i.e. adding diagrams

        for (String scalarType : scalarTypes) {
            handleScalarType(scalarType);
        }

        for (FieldMult multiplicity : this.multiplicities) {
            if (multiplicity.isListValued()) {
                // add ordered
                this.edges.stream().filter(t -> t.getLabel().equals(multiplicity.getElementName())).findFirst().ifPresent(edge -> {
                    builders.startDiagram(Ordered.getInstance());
                    builders.map(Universe.ARROW_SRC_NAME, edge.getSource());
                    builders.map(Universe.ARROW_LBL_NAME, edge.getLabel());
                    builders.map(Universe.ARROW_TRG_NAME, edge.getTarget());
                    builders.endDiagram(Ordered.getInstance().getName().appliedTo(multiplicity.getElementName()));
                });
            } else if (multiplicity.isMandatory()) {
                // add 1..1
                this.edges.stream().filter(t -> t.getLabel().equals(multiplicity.getElementName())).findFirst().ifPresent(edge -> {
                    GraphPredicate pred = TargetMultiplicity.getInstance(1, 1);
                    builders.startDiagram(pred);
                    builders.map(Universe.ARROW_SRC_NAME, edge.getSource());
                    builders.map(Universe.ARROW_LBL_NAME, edge.getLabel());
                    builders.map(Universe.ARROW_TRG_NAME, edge.getTarget());
                    builders.endDiagram(pred.getName().appliedTo(multiplicity.getElementName()));
                });
            } else {
                // add 0..1
                this.edges.stream().filter(t -> t.getLabel().equals(multiplicity.getElementName())).findFirst().ifPresent(edge -> {
                    GraphPredicate pred = TargetMultiplicity.getInstance(0, 1);
                    builders.startDiagram(pred);
                    builders.map(Universe.ARROW_SRC_NAME, edge.getSource());
                    builders.map(Universe.ARROW_LBL_NAME, edge.getLabel());
                    builders.map(Universe.ARROW_TRG_NAME, edge.getTarget());
                    builders.endDiagram(pred.getName().appliedTo(multiplicity.getElementName()));
                });
            }
        }

        for (Map.Entry<Name, EnumLiterals> enm : enums.entrySet()) {
            builders.startDiagram(EnumValue.getInstance(enm.getValue().getLiterals()));
            builders.map(Universe.ONE_NODE_THE_NODE, enm.getKey());
            builders.endDiagram(Name.identifier("Enum").appliedTo(enm.getKey()));
        }

        for (GraphQLInputObjectType in : inputTypes) {
            builders.startDiagram(InputType.getInstance());
            builders.map(Universe.ONE_NODE_THE_NODE, typeMapping.get(in));
            builders.endDiagram(Name.identifier("InputType").appliedTo(Name.identifier(in.getName())));
        }

        for (Pair<GraphQLArgument, Triple> a : arguments) {
            this.convertArgument(a.getFirst(), a.getSecond());
        }

        builders.sketch(resultName);
        Sketch result = builders.getResult(Sketch.class);


        EntryPointType queries = new EntryPointType("Query");
        EntryPointType mutations = new EntryPointType("Mutation");
        if (schema.getQueryType() != null) {
            queries.setName(schema.getQueryType().getName());
        }
        if (schema.getMutationType() != null) {
            mutations.setName(schema.getMutationType().getName());
        }
        Map<Name, no.hvl.past.systems.MessageType> messageTypeMap = new HashMap<>();


        for (MessageType msgType : this.messageTypes) {
            List<MessageArgument> args = new ArrayList<>();
            List<MessageArgument> output = new ArrayList<>();
            no.hvl.past.systems.MessageType msg;
            if (msgType.hasSideEffect) {
                msg = new no.hvl.past.systems.MessageType(msgType.name, args, output, msgType.container , true);
                mutations.addMessage(msg);
            } else {
                msg = new no.hvl.past.systems.MessageType(msgType.name, args, output, msgType.container, false);
                queries.addMessage(msg);
            }
            messageTypeMap.put(msgType.name, msg);
            for (Triple t : msgType.arguments) {
                args.add(new MessageArgument(msg, t, args.size(), false));
            }
            output.add(new MessageArgument(msg, msgType.returnType, 0, true));
        }

        return new GraphQLEndpoint(
                url,
                result,
                nameToText,
                messageTypeMap,
                multiplicities,
                queries,
                mutations,
                om,
                jsonFactory
        );
    }




    private void convertMessage(GraphQLFieldDefinition op, boolean hasSideEffect, MessageContainer owner) {
        // Making a node for the message type
        Name msgName = Name.identifier(op.getName()).prefixWith(owner.getTypeName());
        List<Triple> msgArgs = new ArrayList<>();
        builders.node(msgName);

        // Adding all args as message input edges
        for (GraphQLArgument arg : op.getArguments()) {
            this.mandatory = false;
            this.listValued = false;
            Name argumentName = Name.identifier(arg.getName()).prefixWith(msgName);
            Name resultType = convertResultType(arg.getType());
            Triple edge = Triple.edge(msgName, argumentName, resultType);
            msgArgs.add(edge);
            builders.edge(msgName, argumentName, resultType);
            this.edges.add(edge);
            this.multiplicities.add(new FieldMult(argumentName, listValued, mandatory));
        }

        // Adding the return type as a message output egde
        this.mandatory = false;
        this.listValued = false;
        Name returnType = convertResultType(op.getType());
        // TODO MAGIC NAMES: replace this one
        Name returnTypeEdgeLabel = Name.identifier("result").prefixWith(msgName);
        this.nameToText.put(returnTypeEdgeLabel, op.getName());
        this.nameToText.put(msgName, op.getName());
        builders.edge(msgName, returnTypeEdgeLabel, returnType);
        this.edges.add(Triple.edge(msgName, returnTypeEdgeLabel, returnType));

        this.multiplicities.add(new FieldMult(returnTypeEdgeLabel, listValued, mandatory));

        this.messageTypes.add(new MessageType(msgName, hasSideEffect, msgArgs, Triple.edge(msgName, returnTypeEdgeLabel, returnType), owner));
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
                builders.startDiagram(StringDT.getInstance());
                builders.map(Universe.ONE_NODE_THE_NODE, Name.identifier(scalarType));
                builders.endDiagram(Name.identifier(scalarType + " scalar type"));
                break;
            case "ID":
            default:
                builders.startDiagram(DataTypePredicate.getInstance());
                builders.map(Universe.ONE_NODE_THE_NODE, Name.identifier(scalarType));
                builders.endDiagram(Name.identifier(scalarType + " scalar type"));
                break;

        }
    }

    private void convertField(GraphQLInputObjectField field, GraphQLInputObjectType owner) {
        this.mandatory = false;
        this.listValued = false;

        Name fieldName = createName(owner, field);

        GraphQLInputType resultType = field.getType();
        Name targetName = this.convertResultType(resultType);

        final Triple edge = Triple.edge(this.typeMapping.get(owner), fieldName, targetName);
        this.builders.edge(this.typeMapping.get(owner), fieldName, targetName); // TODO builder method that accepts an edge
        this.edges.add(edge);
        this.multiplicities.add(new FieldMult(fieldName, listValued, mandatory));

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

    public List<FieldMult> getMultiplicities() {
        return multiplicities;
    }

    private Name createName(GraphQLInputObjectType owner, GraphQLInputObjectField field) {
        Name name = Name.identifier(field.getName()).prefixWith(this.typeMapping.get(owner));
        this.nameToText.put(name, field.getName());
        return name;

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
