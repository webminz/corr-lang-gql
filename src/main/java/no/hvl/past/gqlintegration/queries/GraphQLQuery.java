package no.hvl.past.gqlintegration.queries;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import no.hvl.past.graph.Graph;
import no.hvl.past.graph.Sketch;
import no.hvl.past.graph.elements.Triple;
import no.hvl.past.graph.trees.*;
import no.hvl.past.keys.Key;
import no.hvl.past.names.Name;
import no.hvl.past.names.Value;
import no.hvl.past.systems.ComprSys;
import no.hvl.past.systems.Sys;
import no.hvl.past.util.StringUtils;


import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GraphQLQuery implements QueryTree {

    private final List<QueryRoot> roots;
    private final Sketch targetSchema;
    private final Name queryName;


    public GraphQLQuery(List<QueryRoot> roots, Sketch targetSchema, Name queryName) {
        this.roots = roots;
        this.targetSchema = targetSchema;
        this.queryName = queryName;
    }

    @Override
    public boolean isInfinite() {
        return false;
    }

    public boolean isQueryEmpty() {
        return this.roots.isEmpty();
    }

    public List<QueryRoot> getRoots() {
        return roots;
    }

    public interface AbstractSelection {

        String field();

        boolean isListValued();

        boolean isComplex();
    }

    public static class SelectionSet implements QueryBranch.Projection, AbstractSelection {

         private final Triple typing;
         private final Node parent;
         private final Node child;
         private final boolean isComplex;
         private final boolean isListValued;

        SelectionSet(Triple typing, Node parent, Node child, boolean isComplex, boolean isListValued) {
             this.typing = typing;
             this.parent = parent;
             this.child = child;
             this.isComplex = isComplex;
             this.isListValued = isListValued;
         }

        public boolean isComplex() {
            return isComplex;
        }

        public Triple edge() {
            return typing;
        }

        @Override
        public String field() {
            return child.getLabel();
        }

        public boolean isListValued() {
            return isListValued;
        }

         @Override
         public QueryNode parent() {
             return parent;
         }

        @Override
        public Name edgeTyping() {
            return typing.getLabel();
        }

         @Override
         public String label() {
             return child.label;
         }

         @Override
         public QueryNode child() {
             return child;
         }

         @Override
         public boolean isCollection() {
             return false;
         }

         @Override
         public int index() {
             return 0;
         }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SelectionSet that = (SelectionSet) o;
            return typing.equals(that.typing) &&
                    child.equals(that.child);
        }

        @Override
        public int hashCode() {
            return Objects.hash(typing, child);
        }

        Collection<SelectionSet> localize(Node parent, ComprSys comprSys, Sys local) {
            List<SelectionSet> result = new ArrayList<>();
            comprSys.localNames(local,this.typing.getLabel()).forEach(localName -> {
                Triple typing = local.schema().carrier().get(localName).get();
                Node newNode = new Node(local.displayName(typing.getLabel()), typing.getTarget());
                SelectionSet newSelectionSect = new SelectionSet(typing, parent, newNode, this.isComplex, this.isListValued);
                QueryCursor.LocalCursor cursor = new QueryCursor.LocalCursor(newSelectionSect, parent.cursor);
                newNode.cursor = cursor;

                for (SelectionSet ss : this.child.children) {
                    newNode.children.addAll(ss.localize(newNode, comprSys, local));
                }

                if (!this.isComplex || !newNode.children.isEmpty()) {
                    ((QueryCursor.ConcatCursor)SelectionSet.this.child.cursor).addLocalCursor(local.url(),cursor);
                    for (Argument arg : this.child.getArguments()) {
                        newNode.arguments.addAll(arg.localize(newNode, comprSys, local));
                    }
                    result.add(newSelectionSect);
                }
            });
            return result;
        }
    }

    public static class Argument implements QueryBranch.Selection {

        private final String name;
        private final String valueText;
        private final Value value;
        private final Triple type;
        private final Node parent;

        Argument(String name, String valueText, Value value, Triple type, Node parent) {
            this.name = name;
            this.valueText = valueText;
            this.value = value;
            this.type = type;
            this.parent = parent;
        }


        public Value getValue() {
            return value;
        }

        public String getValueText() {
            return valueText;
        }

        @Override
        public Node parent() {
            return parent;
        }

        @Override
        public Name edgeTyping() {
            return type.getLabel();
        }

        @Override
        public String label() {
            return name;
        }

        @Override
        public QueryNode child() {
            return new QueryNode() {
                @Override
                public Name branchName() {
                    return Name.identifier(label());
                }

                @Override
                public Name nodeType() {
                    return type.getTarget();
                }

                @Override
                public Stream<Branch> children() {
                    return Stream.empty();
                }

                @Override
                public Optional<Branch> parentRelation() {
                    return Optional.of(GraphQLQuery.Argument.this);
                }
            };
        }

        @Override
        public boolean isCollection() {
            return false;
        }

        @Override
        public int index() {
            return 0;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Argument argument = (Argument) o;
            return name.equals(argument.name) &&
                    valueText.equals(argument.valueText) &&
                    value.equals(argument.value) &&
                    type.equals(argument.type);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, valueText, value, type);
        }

        Collection<Argument> localize(Node parent, ComprSys comprSys, Sys local) {
            List<Argument> result = new ArrayList<>();
            comprSys.localNames(local, this.type.getLabel()).forEach(localName -> {
                result.add(new Argument(local.displayName(localName), valueText, value, local.schema().carrier().get(localName).get(), parent));
            });
            return result;
        }
    }

    public static class Node implements QueryNode {

        private final String label;
        private final Name type;
        private final List<SelectionSet> children = new ArrayList<>();
        private final List<Argument> arguments = new ArrayList<>();
        private SelectionSet parent;
        QueryCursor cursor;

        public Optional<QueryCursor> getCursor() {
            return Optional.ofNullable(cursor);
        }

        protected QueryCursor createGlobalCursor(QueryCursor parentCursor, Set<Key> keys, List<Sys> locals) {
            if (keys.stream().anyMatch(k -> k.targetType().equals(type))) {
                Multimap<String, Key> keysMap = ArrayListMultimap.create();
                for (Sys local : locals) {
                    keys.stream()
                            .filter(k -> k.targetType().equals(type))
                            .filter(k -> k.requiredProperties().stream().allMatch(t -> local.schema().carrier().contains(t)))
                            .forEach(k -> keysMap.put(local.url(),k));
                }
                this.cursor = new QueryCursor.ConcatMergeCursor(parent, parentCursor, new LinkedHashMap<>(), keysMap);
            } else {
                this.cursor = new QueryCursor.ConcatCursor(parent, parentCursor, new LinkedHashMap<>());
            }
            for (SelectionSet s : this.children) {
                s.child.createGlobalCursor(this.cursor, keys, locals);
            }
            return this.cursor;
        }

        List<Argument> getArguments() {
            return arguments;
        }

        List<SelectionSet> getChildren() {
            return children;
        }

        void addParent(Node parent, Triple typingEdge, boolean isComplexTypeChild, boolean isListValued) {
            SelectionSet e = new SelectionSet(typingEdge, parent, this, isComplexTypeChild, isListValued);
            parent.children.add(e);
            this.parent = e;
        }

        void addAttribute(String attributeName, String valueAsText, Value attributeValue, Triple typing) {
            this.arguments.add(new Argument(attributeName, valueAsText, attributeValue, typing, this));
        }

        public void addChild(Node node, Triple typingEdge, boolean isComplexTypeChild, boolean isListValued) {
            SelectionSet e = new SelectionSet(typingEdge, this, node, isComplexTypeChild, isListValued);
            this.children.add(e);
            this.parent = e;
        }


        public Node(String label, Name type) {
            this.label = label;
            this.type = type;
        }


        public String getLabel() {
            return label;
        }

        @Override
        public Name branchName() {
            return Name.identifier(label);
        }

        public Name typing() {
            return type;
        }

        @Override
        public Optional<Branch> parentRelation() {
            return Optional.ofNullable(parent);
        }

        @Override
        public Name nodeType() {
            return typing();
        }

        @Override
        public Stream<Branch> children() {
            List<Branch> result = new ArrayList<>();
            this.arguments.forEach(result::add);
            this.children.forEach(result::add);
            return result.stream();
        }


        public void print(StringBuilder sink, int nestingLevel) {
            sink.append(StringUtils.produceIndentation(nestingLevel));
            sink.append(label);
            sink.append(' ');
            if (!arguments.isEmpty()) {
                sink.append('(');
                Iterator<Argument> it = arguments.iterator();
                while (it.hasNext()) {
                    Argument argument = it.next();
                    sink.append(argument.name);
                    sink.append(" : \"");
                    sink.append(argument.valueText);
                    sink.append('"');
                    if (it.hasNext()) {
                        sink.append(", ");
                    }
                }
                sink.append(") ");
            }
            if (!children.isEmpty()) {
                sink.append(" {\n");
                for (SelectionSet qNode :children) {
                    qNode.child.print(sink, nestingLevel +1);
                }
                sink.append(StringUtils.produceIndentation(nestingLevel));
                sink.append("}\n");
            } else {
                sink.append('\n');
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Node node = (Node) o;
            return label.equals(node.label) &&
                    type.equals(node.type) &&
                    children.equals(node.children) &&
                    arguments.equals(node.arguments);
        }

        @Override
        public int hashCode() {
            return Objects.hash(label, type, children, arguments);
        }

    }

    public static class QueryRoot extends Node implements QueryNode.Root, AbstractSelection {

        private final boolean isMutation;
        private final Triple messageReturnsTyping;

        public QueryRoot(String label, boolean isMutation, Triple messageReturnsTyping) {
            super(label, messageReturnsTyping.getTarget());
            this.isMutation = isMutation;
            this.messageReturnsTyping = messageReturnsTyping;
        }

        public QueryCursor createGlobalCursor(Set<Key> keys, List<Sys> locals) {
            if (keys.stream().anyMatch(k -> k.targetType().equals(messageReturnsTyping.getTarget()))) {
                Multimap<String, Key> keysMap = ArrayListMultimap.create();
                for (Sys local : locals) {
                    keys.stream()
                            .filter(k -> k.targetType().equals(messageReturnsTyping.getTarget()))
                            .filter(k -> k.requiredProperties().stream().allMatch(t -> local.schema().carrier().contains(t)))
                            .forEach(k -> keysMap.put(local.url(),k));
                }
                this.cursor = new QueryCursor.ConcatMergeCursor(this, new LinkedHashMap<>(), keysMap);
            } else {
                this.cursor = new QueryCursor.ConcatCursor(this, new LinkedHashMap<>());
            }
            for (SelectionSet s : this.getChildren()) {
                s.child.createGlobalCursor(this.cursor, keys, locals);
            }
            return this.cursor;
        }

        @Override
        public Name branchName() {
            return Name.identifier(getLabel());
        }

        @Override
        public Optional<Branch> parentRelation() {
            return Optional.empty();
        }


        @Override
        public Triple queryResultEdge() {
            return messageReturnsTyping;
        }

        List<GraphQLQuery.QueryRoot> localize(ComprSys comprSys, Sys local) {
            List<GraphQLQuery.QueryRoot> result = new ArrayList<>();
            comprSys.localNames(local,this.messageReturnsTyping.getSource()).forEach(localName -> {
                QueryRoot e = new QueryRoot(local.displayName(localName), isMutation, local.schema().carrier().get(localName).get());
                QueryCursor.LocalCursor localCursor = new QueryCursor.LocalCursor(e);
                e.cursor = localCursor;
                for (SelectionSet ss : this.getChildren()) {
                    e.getChildren().addAll(ss.localize(e, comprSys, local));
                }
                if (!e.getChildren().isEmpty()) {
                    ((QueryCursor.ConcatCursor) QueryRoot.this.cursor).addLocalCursor(local.url(), localCursor);
                    for (Argument arg : this.getArguments()) {
                        e.getArguments().addAll(arg.localize(e, comprSys, local));
                    }
                    result.add(e);
                }
            });
            return result;
        }

        public Triple edge() {
            return messageReturnsTyping;
        }

        @Override
        public String field() {
            return getLabel();
        }

        @Override
        public boolean isListValued() {
            return true; // TODO lookup
        }

        @Override
        public boolean isComplex() {
            return true; // TODO lookup
        }
    }

    private Optional<GraphQLQuery> localize(ComprSys comprSys, Sys local) {
        List<GraphQLQuery.QueryRoot> roots = new ArrayList<>();
        for (GraphQLQuery.QueryRoot r : this.roots) {
            roots.addAll(r.localize(comprSys, local));
        }
        if (roots.isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.of(new GraphQLQuery(roots, local.schema(), Name.anonymousIdentifier()));
        }
    }

    public Map<Sys, GraphQLQuery> split(ComprSys comprSys, List<Sys> locals) {
        LinkedHashMap<Sys, GraphQLQuery> result = new LinkedHashMap<>();
        for (GraphQLQuery.QueryRoot root : this.roots) {
            root.createGlobalCursor(comprSys.keys().collect(Collectors.toSet()), locals);
        }
        for (Sys local : locals) {
            this.localize(comprSys, local).ifPresent(q ->result.put(local, q));
        }
        return result;
    }

    @Override
    public Stream<QueryNode.Root> queryRoots() {
        return this.roots.stream().map(q -> q);
    }

    @Override
    public String textualRepresentation() {
        StringBuilder result = new StringBuilder();
        if (this.roots.stream().anyMatch(q -> !q.isMutation)) {
            result.append("query {\n");
            this.roots.stream().filter(q -> !q.isMutation).forEach(q -> q.print(result, 1));
            result.append("}\n");
        };
        if (this.roots.stream().anyMatch(q -> q.isMutation)) {
            result.append("mutation {\n");
            this.roots.stream().filter(q -> q.isMutation).forEach(q -> q.print(result, 1));
            result.append("}\n");
        }
        return result.toString();
    }

    @Override
    public Graph codomain() {
        return targetSchema.carrier();
    }

    @Override
    public Name getName() {
        return queryName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GraphQLQuery query = (GraphQLQuery) o;
        return roots.equals(query.roots);
    }

    @Override
    public int hashCode() {
        return Objects.hash(roots);
    }
}
