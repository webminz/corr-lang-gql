package no.hvl.past.gqlintegration.schema;

import com.google.common.collect.Sets;
import no.hvl.past.gqlintegration.predicates.FieldArgument;
import no.hvl.past.gqlintegration.predicates.InputType;
import no.hvl.past.gqlintegration.predicates.MutationMessage;
import no.hvl.past.graph.*;
import no.hvl.past.graph.elements.Triple;
import no.hvl.past.graph.elements.Tuple;
import no.hvl.past.graph.predicates.*;
import no.hvl.past.logic.Formula;
import no.hvl.past.names.Name;
import no.hvl.past.names.Prefix;
import no.hvl.past.names.PrintingStrategy;
import no.hvl.past.systems.MessageArgument;
import no.hvl.past.systems.MessageType;
import no.hvl.past.util.StreamExt;
import no.hvl.past.util.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class GraphQLSchemaWriter implements Visitor {


    private static final Set<Name> BUILTIN_SCALARS = Sets.newHashSet(
            Name.identifier("ID"),
            Name.identifier("String"),
            Name.identifier("Boolean"),
            Name.identifier("Int"),
            Name.identifier("Float"
            ));

    private static final Set<Class<?>> SCALAR_PREDICATES = Sets.newHashSet(
            IntDT.class,
            StringDT.class,
            FloatDT.class,
            BoolDT.class,
            DataTypePredicate.class
    );

    public List<FieldMult> getMults() {
        List<FieldMult> result = new ArrayList<>();
        for (Container c : this.typeMap.values()) {
            if (c.type != ContainerType.HIDDEN && c.type != ContainerType.ENUM) {
                for (ContainerChild ch : c.fields) {
                    result.add(new FieldMult(ch.edgeLabel, ch.setValued, ch.mandatory));
                }
            }
        }
        for (ContainerChild ch : queryContainer.fields) {
            result.add(new FieldMult(ch.edgeLabel, ch.setValued, ch.mandatory));
        }
        for (ContainerChild ch : queryContainer.fields) {
            result.add(new FieldMult(ch.edgeLabel, ch.setValued, ch.mandatory));
        }
        return result;
    }

    private enum ContainerType {
        OBJECT,
        SCALAR,
        ENUM,
        HIDDEN,
        INTERFACE,
        UNION,
        INPUT
    }


    private static final class Container {

        private final Name node;
        private final List<ContainerChild> fields;
        private String displayName;
        private ContainerType type;

        public Container(Name node) {
            this.node = node;
            this.fields = new ArrayList<>();
            this.displayName = node.print(PrintingStrategy.IGNORE_PREFIX);
            this.type = ContainerType.OBJECT;
        }

        private void print(BufferedWriter writer, Map<Name, Container> typeMap, Map<Name, String> nameToText) throws IOException {
            if (type != ContainerType.HIDDEN) {
                nameToText.put(node, displayName);
                switch (type) {
                    case SCALAR:
                        writer.append("scalar ");
                        writer.append(displayName);
                        writer.newLine();
                        break;
                    case OBJECT:
                        writer.append("type ");
                        writer.append(displayName);
                        writer.append(" {\n");
                        for (ContainerChild f : fields) {
                            f.print(writer,typeMap, nameToText);
                            writer.newLine();
                        }
                        writer.append("}\n");
                        break;

                    case ENUM:
                        writer.append("enum ");
                        writer.append(displayName);
                        writer.append(" {\n");
                        for (ContainerChild f : fields) {
                            f.print(writer,typeMap, new HashMap<>());
                            writer.newLine();
                        }
                        writer.append("}\n");
                        break;

                    case INPUT:
                        writer.append("input ");
                        writer.append(displayName);
                        writer.append(" {\n");
                        for (ContainerChild f : fields) {
                            f.print(writer,typeMap, new HashMap<>());
                            writer.newLine();
                        }
                        writer.append("}\n");
                        break;
                }
            }
        }
    }



    private  final class ContainerChild {
        private final Name edgeLabel;
        private final Name ownerName;
        private Name targetName;
        private String displayName;

        private boolean setValued;
        private boolean mandatory;
        private final List<FieldArgument> arguments;

        public ContainerChild(Name edgeLabel, Name ownerName) {
            this.edgeLabel = edgeLabel;
            this.ownerName = ownerName;
            this.targetName = null;
            this.displayName = edgeLabel.print(PrintingStrategy.IGNORE_PREFIX);
            this.setValued = false;
            this.mandatory = false;
            this.arguments = new ArrayList<>();
        }

        ContainerChild(Name ownerName, Name fieldName, Name targetTypeName) {
            this.ownerName = ownerName;
            this.edgeLabel = fieldName;
            this.targetName = targetTypeName;
            this.displayName = edgeLabel.print(PrintingStrategy.IGNORE_PREFIX);
            this.setValued = true;
            this.mandatory = false;
            this.arguments = new ArrayList<>();
        }

        void print(BufferedWriter writer, Map<Name, Container> typeMap, Map<Name, String> nameToText) throws IOException {
            nameToText.put(edgeLabel, displayName);
            writer.append("   ");
            writer.append(displayName);
            if (!arguments.isEmpty()) {
                writer.append("(");
                Iterator<FieldArgument> it = arguments.iterator();
                while (it.hasNext()) {
                    FieldArgument current = it.next();
                    writer.append(current.getFieldName());
                    writer.append(" : ");

                    if (current.isListValued()) {
                        writer.append("[");
                    }
                    if (isBuiltinBasType(current.getTypeName())) {
                        writer.append(getBuiltinBaseType(current.getTypeName()));
                    } else {
                        writer.append(typeMap.get(current.getTypeName()).displayName);
                    }
                    if (current.isListValued()) {
                        writer.append("]");
                    }
                    if (current.isMandatory()) {
                        writer.append("!");
                    }

                    if (it.hasNext()) {
                        if (arguments.size() <= 2) {
                            writer.append(", ");
                        } else {
                            writer.append("\n      ");
                        }
                    }
                }
                writer.append(")");
            }

            if (targetName != null) {
                writer.append(" : ");
                if (setValued) {
                    writer.append('[');
                }
                if (isBuiltinBasType(targetName)) {
                    writer.append(getBuiltinBaseType(targetName));
                } else {
                    writer.append(typeMap.get(targetName).displayName);
                }
                if (setValued) {
                    writer.append(']');
                }
                if (mandatory) {
                    writer.append("!");
                }
            }
        }

        void addArgument(FieldArgument argument) {
            this.arguments.add(argument);
        }

    }

    private String getBuiltinBaseType(Name typeName) {
        return this.nameToText.get(typeName);
    }

    private  boolean isBuiltinBasType(Name current) {
        return stringName.equals(current) || floatName.equals(current) || intName.equals(current) || boolName.equals(current) || idName.equals(current);
    }

    private Map<Name, Container> typeMap = new HashMap<>();
    private Map<Name, String> nameToText = new HashMap<>();
    private List<Container> finalList = new ArrayList<>();

    private Name stringName = Name.identifier("String");
    private Name floatName = Name.identifier("Float");
    private Name intName = Name.identifier("Int");
    private Name boolName = Name.identifier("Boolean");
    private Name idName = Name.identifier("ID");


    private boolean ignoreElementsNow = false;

    private Container queryContainer;
    private Container mutationContainer;

    public GraphQLSchemaWriter() {
        this.nameToText.put(Name.identifier("String"), "String");
        this.nameToText.put(Name.identifier("Int"), "Int");
        this.nameToText.put(Name.identifier("ID"), "ID");
        this.nameToText.put(Name.identifier("Boolean"), "Boolean");
        this.nameToText.put(Name.identifier("Float"), "Float");
    }

    @Override
    public void beginSketch() {

    }

    @Override
    public void handleElementName(Name name) {

    }

    @Override
    public void beginGraph() {

    }


    @Override
    public void handleNode(Name node) {
        if (node.equals(Name.identifier("Subscription"))) { // not supported
            return;
        }

        if (!ignoreElementsNow) {
            if (! BUILTIN_SCALARS.contains(node)) {
                this.typeMap.put(node, new Container(node));
            }
        }
    }

    @Override
    public void handleEdge(Triple triple) {
        if (triple.getSource().equals(Name.identifier("Subscription"))) { // not supported
            return;
        }

        if (!ignoreElementsNow) {
            if (typeMap.containsKey(triple.getSource())) {
                typeMap.get(triple.getSource()).fields.add(new ContainerChild(triple.getSource(), triple.getLabel(), triple.getTarget()));
            }
        }
    }

    @Override
    public void endGraph() {
        this.ignoreElementsNow = true;
    }




    private void handleMsgArg(MessageArgument argument) {
        ContainerChild message = createMessageContainerIfNeeded(argument.message());
        ContainerChild oldRepresentation = this.typeMap.get(argument.message().typeName()).fields.stream().filter(child -> child.edgeLabel.equals(argument.asEdge().getLabel())).findFirst().get();
        if (argument.isInput()) {
            if (argument.argumentOrder() >= message.arguments.size()) {
                message.arguments.add(new FieldArgument(argument.asEdge().getLabel().print(PrintingStrategy.IGNORE_PREFIX), oldRepresentation.targetName, oldRepresentation.setValued, oldRepresentation.mandatory));
            } else {
                message.arguments.add(argument.argumentOrder(),new FieldArgument(argument.asEdge().getLabel().print(PrintingStrategy.IGNORE_PREFIX), oldRepresentation.targetName, oldRepresentation.setValued, oldRepresentation.mandatory));
            }
        } else {
            message.targetName = oldRepresentation.targetName;
            message.setValued = oldRepresentation.setValued;
            message.mandatory = oldRepresentation.mandatory;
        }
    }

    private ContainerChild createMessageContainerIfNeeded(MessageType message) {
        if (typeMap.get(message.typeName()).type == ContainerType.HIDDEN) {
            if (message instanceof MutationMessage) {
                return this.mutationContainer.fields.stream().filter(child -> child.edgeLabel.equals(message.typeName())).findFirst().get();
            } else {
                return this.queryContainer.fields.stream().filter(child -> child.edgeLabel.equals(message.typeName())).findFirst().get();
            }
        } else {
            typeMap.get(message.typeName()).type = ContainerType.HIDDEN;
            if (message instanceof MutationMessage) {
                if (mutationContainer == null) {
                    mutationContainer = new Container(Name.identifier("Mutation"));
                }
                ContainerChild child = new ContainerChild(message.typeName(), Name.identifier("Mutation"));
                String preResult = message.typeName().print(PrintingStrategy.IGNORE_PREFIX);
                preResult = preResult.substring(preResult.indexOf('.') + 1);
                child.displayName = preResult;
                mutationContainer.fields.add(child);
                return child;
            } else {
                if (queryContainer == null) {
                    queryContainer = new Container(Name.identifier("Query"));
                }
                ContainerChild child = new ContainerChild(message.typeName(), Name.identifier("Query"));
                String preResult = message.typeName().print(PrintingStrategy.IGNORE_PREFIX);
                preResult = preResult.substring(preResult.indexOf('.') + 1);
                child.displayName = preResult;
                queryContainer.fields.add(child);
                return child;
            }
        }
    }

    @Override
    public void beginMorphism() {

    }

    @Override
    public void handleMapping(Tuple tuple) {
    }

    @Override
    public void handleDiagram(Diagram diagram) {
        if (diagram instanceof MessageArgument) {
            handleMsgArg((MessageArgument) diagram);
        } else {
            Formula<Graph> graphFormula = diagram.label();
            GraphMorphism binding = diagram.binding();
            if (StringDT.class.isAssignableFrom(graphFormula.getClass())) {
                diagram.nodeBinding().ifPresent(typ -> {
                    baseType(typ, "String");
                    stringName = typ;
                });
            } else if (IntDT.class.isAssignableFrom(graphFormula.getClass())) {
                diagram.nodeBinding().ifPresent(typ -> {
                    baseType(typ, "Int");
                    intName = typ;
                });
            } else if (BoolDT.class.isAssignableFrom(graphFormula.getClass())) {
                diagram.nodeBinding().ifPresent(typ -> {
                    baseType(typ, "Boolean");
                    boolName = typ;
                });
            } else if (FloatDT.class.isAssignableFrom(graphFormula.getClass())) {
                diagram.nodeBinding().ifPresent(typ -> {
                    baseType(typ, "Float");
                    floatName = typ;
                });
            } else if (DataTypePredicate.class.isAssignableFrom(graphFormula.getClass())) {
                diagram.nodeBinding().ifPresent(typ -> {
                    if (!typ.equals(Name.identifier("ID"))) {
                        if (typeMap.containsKey(typ)) {
                            Container container = typeMap.get(typ);
                            container.type = ContainerType.SCALAR;
                        }
                    }
                });

            } else if (TargetMultiplicity.class.isAssignableFrom(graphFormula.getClass())) {
                TargetMultiplicity multiplicity = (TargetMultiplicity) graphFormula;
                if (multiplicity.getLowerBound() == 1 && multiplicity.getUpperBound() == 1) {
                    diagram.edgeBinding().ifPresent(t -> {
                        this.typeMap.get(t.getSource()).fields.stream().filter(f -> f.edgeLabel.equals(t.getLabel()))
                                .forEach(f -> {
                                    f.mandatory = true;
                                    f.setValued = false;
                                });
                    });
                } else if (multiplicity.getLowerBound() == 0 && multiplicity.getUpperBound() == 1) {
                    diagram.edgeBinding().ifPresent(t -> {
                        this.typeMap.get(t.getSource()).fields.stream().filter(f -> f.edgeLabel.equals(t.getLabel()))
                                .forEach(f -> f.setValued = false);
                    });
                }
            } else if (FieldArgument.class.isAssignableFrom(graphFormula.getClass())) {
                diagram.edgeBinding().ifPresent(t -> {
                    this.typeMap.get(t.getSource()).fields.stream().filter(f -> f.edgeLabel.equals(t.getLabel()))
                            .forEach(f -> f.addArgument((FieldArgument) graphFormula));
                });

            } else if (EnumValue.class.isAssignableFrom(graphFormula.getClass())) {
                EnumValue enumeration = (EnumValue) graphFormula;
                diagram.nodeBinding().ifPresent(typ -> {
                    Container container = typeMap.get(typ);
                    container.type = ContainerType.ENUM;
                    for (Name literal : enumeration.literals()) {
                        container.fields.add(new ContainerChild(literal, container.node));
                    }
                });
            } else if (InputType.class.isAssignableFrom(graphFormula.getClass())) {
                diagram.nodeBinding().ifPresent(typ -> {
                    typeMap.get(typ).type = ContainerType.INPUT;
                });
            }
        }
    }

    private void baseType(Name typ, String string) {
        if (typeMap.containsKey(typ)) {
            typeMap.get(typ).type = ContainerType.HIDDEN;
        }
        nameToText.put(typ, string);
    }


    public void printToBuffer(BufferedWriter writer) throws IOException {
        for (Container c : this.finalList) {
            c.print(writer, typeMap, nameToText);
            writer.newLine();
        }
        writer.flush();
    }



    @Override
    public void endMorphism() {

    }




    @Override
    public void endSketch() {
        PrintingStrategy.DefaultPrintingStrategy p = new PrintingStrategy.DefaultPrintingStrategy(false, false, true);
        p.setPrefixDelimiter("_");
        Comparator<Container> comparator = new Comparator<Container>() {
            @Override
            public int compare(Container o1, Container o2) {
                return o1.displayName.compareTo(o2.displayName);
            }
        };

        // disambiguate types
        Set<Container> duplicates = new HashSet<>();
        StreamExt.stream(this.typeMap.values())
                .filter(c -> c.type != ContainerType.HIDDEN)
                .withDuplicateProperty(c -> c.displayName)
                .forEach(duplicates::add);
        for (Container duplicate : duplicates) {
                duplicate.displayName = duplicate.node.print(p);
        }

        // disambiguate fields
        for (Container c : this.typeMap.values()) {
            if (c.type != ContainerType.HIDDEN) {
                disambiguate(p, c);
            }
            if (queryContainer != null) {
                disambiguate(p, this.queryContainer);
            }
            if (mutationContainer != null) {
                disambiguate(p, this.mutationContainer);
            }
        }

        if (queryContainer != null) {
            finalList.add(queryContainer);
        }
        if (mutationContainer != null) {
            finalList.add(mutationContainer);
        }
        typeMap.values().stream().filter(c -> c.type.equals(ContainerType.SCALAR)).sorted(comparator).forEach(finalList::add);
        typeMap.values().stream().filter(c -> c.type.equals(ContainerType.ENUM)).sorted(comparator).forEach(finalList::add);
        typeMap.values().stream().filter(c -> c.type.equals(ContainerType.OBJECT)).sorted(comparator).forEach(finalList::add);
        typeMap.values().stream().filter(c -> c.type.equals(ContainerType.INPUT)).sorted(comparator).forEach(finalList::add);

    }

    private void disambiguate(PrintingStrategy.DefaultPrintingStrategy p, Container c) {
        Set<ContainerChild> duplicateFields = StreamExt.stream(c.fields).withDuplicateProperty(child -> child.displayName).collect(Collectors.toSet());
        for (ContainerChild duplicateField : duplicateFields) {
            if (duplicateField.edgeLabel.unprefixAll().print(PrintingStrategy.IGNORE_PREFIX).contains(".")) {
                if (duplicateField.edgeLabel instanceof Prefix) {
                    Prefix pref = (Prefix) duplicateField.edgeLabel;
                    Name prefixName = pref.getPrefix().get();
                    duplicateField.displayName = duplicateField.displayName + "_" + prefixName.print(p);
                } else {
                    String t = duplicateField.edgeLabel.unprefixAll().printRaw();
                    String[] split = t.split("\\.");
                    duplicateField.displayName = split[1] + "_" + split[0];
                }

            } else {
                duplicateField.displayName = StringUtils.lowerCaseFirst(duplicateField.edgeLabel.print(p));
            }

        }
    }

    public Map<Name, String> getNameToText() {
        return nameToText;
    }

    // Not used

    @Override
    public void beginSpan() {
    }

    @Override
    public void endSpan() {
    }
}
