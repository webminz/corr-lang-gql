package no.hvl.past.gqlintegration.schema;

import com.google.common.collect.Sets;
import no.hvl.past.gqlintegration.predicates.FieldArgument;
import no.hvl.past.graph.Graph;
import no.hvl.past.graph.Universe;
import no.hvl.past.graph.Visitor;
import no.hvl.past.graph.elements.Triple;
import no.hvl.past.graph.elements.Tuple;
import no.hvl.past.graph.predicates.*;
import no.hvl.past.logic.Formula;
import no.hvl.past.names.Name;
import no.hvl.past.names.PrintingStrategy;
import no.hvl.past.util.StreamExt;

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

    private enum ContainerType {
        OBJECT,
        SCALAR,
        ENUM,
        INTERFACE,
        UNION
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
            }
        }
    }

    private enum Decoration {
        SCALAR,
        OPTIONAL,
        MANDATORY,
        ARGUMENT,
        ENUM
    }

    private static final class ContainerChild {
        private final Name edgeLabel;
        private final Name ownerName;
        private final Name targetName;
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
                    if (BUILTIN_SCALARS.contains(current.getTypeName())) {
                        writer.append(current.getTypeName().print(PrintingStrategy.IGNORE_PREFIX));
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
                if (BUILTIN_SCALARS.contains(targetName)) {
                    writer.append(targetName.print(PrintingStrategy.IGNORE_PREFIX));
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

    private Map<Name, Container> typeMap = new HashMap<>();
    private List<Container> finalList = new ArrayList<>();
    private Map<Name, String> nameToText = new HashMap<>();


    private Name srcBind;
    private Name lblBind;
    private Name trgBind;
    private Decoration currentDecorator;
    private boolean ignoreElementsNow = false;
    private FieldArgument currentArgument;
    private Set<Name> currentEnumLiterals;

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



    @Override
    public void beginDiagram() {
    }

    public static boolean isBaseTypePredicate(Formula<Graph> formula) {
        return SCALAR_PREDICATES.stream().anyMatch(clazz -> clazz.isAssignableFrom(formula.getClass()));
    }

    @Override
    public void handleFormula(Formula<Graph> graphFormula) {
        if (isBaseTypePredicate(graphFormula)) {
            this.currentDecorator = Decoration.SCALAR;
        } else if (TargetMultiplicity.class.isAssignableFrom(graphFormula.getClass())) {
            TargetMultiplicity multiplicity = (TargetMultiplicity) graphFormula;
            if (multiplicity.getLowerBound() == 1 && multiplicity.getUpperBound() == 1) {
                this.currentDecorator = Decoration.MANDATORY;
            } else if (multiplicity.getLowerBound() == 0 && multiplicity.getUpperBound() == 1) {
                this.currentDecorator = Decoration.OPTIONAL;
            }
        } else if (FieldArgument.class.isAssignableFrom(graphFormula.getClass())) {
            this.currentArgument =  (FieldArgument) graphFormula;
            this.currentDecorator = Decoration.ARGUMENT;
        } else if (EnumValue.class.isAssignableFrom(graphFormula.getClass())) {
            EnumValue enumeration = (EnumValue) graphFormula;
            this.currentDecorator = Decoration.ENUM;
            this.currentEnumLiterals = enumeration.literals();
        }
    }

    @Override
    public void beginMorphism() {

    }

    @Override
    public void handleMapping(Tuple tuple) {
        if (this.currentDecorator != null) {
            switch (currentDecorator) {
                case SCALAR:
                    if (!BUILTIN_SCALARS.contains(tuple.getCodomain())) {
                        Container container = typeMap.get(tuple.getCodomain());
                        container.type = ContainerType.SCALAR;
                    }
                    break;
                case MANDATORY:
                    bind(tuple);
                    if (isCompletelyBound()) {
                        this.typeMap.get(srcBind).fields.stream().filter(f -> f.edgeLabel.equals(lblBind))
                                .forEach(f -> {
                                    f.mandatory = true;
                                    f.setValued = false;
                                });
                    }
                    break;
                case OPTIONAL:
                    bind(tuple);
                    if (isCompletelyBound()) {
                        this.typeMap.get(srcBind).fields.stream().filter(f -> f.edgeLabel.equals(lblBind))
                                .forEach(f -> f.setValued = false);
                    }
                    break;
                case ARGUMENT:
                    bind(tuple);
                    if (isCompletelyBound()) {
                        this.typeMap.get(srcBind).fields.stream().filter(f -> f.edgeLabel.equals(lblBind))
                                .forEach(f -> f.addArgument(currentArgument));
                    }
                    break;
                case ENUM:
                    Container container = typeMap.get(tuple.getCodomain());
                    container.type = ContainerType.ENUM;
                    for (Name literal : this.currentEnumLiterals) {
                        container.fields.add(new ContainerChild(literal, tuple.getCodomain()));
                    }
                    break;
            }
        }

    }

    private void bind(Tuple tuple) {
        if (tuple.getDomain().equals(Universe.ARROW_SRC_NAME)) {
            srcBind = tuple.getCodomain();
        } else if (tuple.getDomain().equals(Universe.ARROW_LBL_NAME)) {
            lblBind = tuple.getCodomain();
        } else if (tuple.getDomain().equals(Universe.ARROW_TRG_NAME)) {
            trgBind = tuple.getCodomain();
        }
    }

    public void printToBuffer(BufferedWriter writer) throws IOException {
        for (Container c : this.finalList) {
            c.print(writer, typeMap, nameToText);
            writer.newLine();
        }
        writer.flush();
    }

    private boolean isCompletelyBound() {
        return srcBind != null && lblBind != null && trgBind != null;
    }

    @Override
    public void endMorphism() {

    }


    @Override
    public void endDiagram() {
        this.currentDecorator = null;
        this.srcBind = null;
        this.lblBind = null;
        this.trgBind = null;
        this.currentEnumLiterals = null;
        this.currentArgument = null;
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
                .withDuplicateProperty(c -> c.displayName)
                .forEach(duplicates::add);
        for (Container duplicate : duplicates) {
                duplicate.displayName = duplicate.node.print(p);
        }

        // disambiguate fields
        for (Container c : this.typeMap.values()) {
            Set<ContainerChild> duplicateFields = StreamExt.stream(c.fields).withDuplicateProperty(child -> child.displayName).collect(Collectors.toSet());
            for (ContainerChild duplicateField : duplicateFields) {
                String disambiguated = duplicateField.edgeLabel.print(p);
                if (Character.isUpperCase(disambiguated.charAt(0))) {
                    char first = Character.toLowerCase(disambiguated.charAt(0));
                    disambiguated = first + disambiguated.substring(1);
                }
                duplicateField.displayName = disambiguated;
            }
        }

        typeMap.values().stream().filter(c -> c.type.equals(ContainerType.SCALAR)).sorted(comparator).forEach(finalList::add);
        typeMap.values().stream().filter(c -> c.type.equals(ContainerType.ENUM)).sorted(comparator).forEach(finalList::add);
        typeMap.values().stream().filter(c -> c.type.equals(ContainerType.OBJECT)).sorted(comparator).forEach(finalList::add);

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
