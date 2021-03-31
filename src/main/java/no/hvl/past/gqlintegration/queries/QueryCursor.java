package no.hvl.past.gqlintegration.queries;


import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Multimap;
import no.hvl.past.keys.Key;
import no.hvl.past.keys.KeyNotEvaluated;
import no.hvl.past.names.AnonymousIdentifier;
import no.hvl.past.names.Name;

import java.io.IOException;
import java.util.*;

// TODO abstraction for the generator to yield errors
public abstract class QueryCursor {

    private final GraphQLQuery.AbstractSelection queryNode;
    private final QueryCursor parent;
    private final List<QueryCursor> childrenPos;

    public QueryCursor(GraphQLQuery.AbstractSelection queryNode) {
        this.queryNode = queryNode;
        this.parent = null;
        this.childrenPos = new ArrayList<>();
    }

    public QueryCursor(GraphQLQuery.AbstractSelection queryNode, QueryCursor parent) {
        this.queryNode = queryNode;
        this.parent = parent;
        this.childrenPos = new ArrayList<>();
        parent.childrenPos.add(this);
    }

    void addChild(QueryCursor child) {
        this.childrenPos.add(child);
    }

    public GraphQLQuery.AbstractSelection getQueryNode() {
        return queryNode;
    }

    public void enter(JsonGenerator generator) throws IOException {
        generator.writeFieldName(getQueryNode().field());
        if (getQueryNode().isListValued()) {
            generator.writeStartArray();
        }
    }
    public void enterChild(JsonGenerator generator) throws IOException {
        if (getQueryNode().isComplex()) {
            generator.writeStartObject();
        }

    }
    public abstract void atomic(JsonGenerator generator) throws IOException;

    public void leaveChild(JsonGenerator generator) throws IOException {
        if (getQueryNode().isComplex()) {
            generator.writeEndObject();
        }
    }


    public void leave(JsonGenerator generator) throws IOException {
        if (getQueryNode().isListValued()) {
            generator.writeEndArray();
        }
    }

    public abstract void processOne(JsonGenerator generator) throws IOException;


    List<QueryCursor> getChildrenPos() {
        return childrenPos;
    }

    public static class LocalCursor extends QueryCursor {

        private List<List<JsonNode>> result = new ArrayList<>();

        public LocalCursor(GraphQLQuery.AbstractSelection queryNode) {
            super(queryNode);
        }

        public LocalCursor(GraphQLQuery.AbstractSelection queryNode, QueryCursor parent) {
            super(queryNode, parent);
        }

        @Override
        public void atomic(JsonGenerator generator) throws IOException {
            if ((this.result.isEmpty() || this.result.get(0).isEmpty()) && !getQueryNode().isListValued()) {
                generator.writeNull();
            } else {
                for (JsonNode v : this.result.get(0)) {
                    value(generator, v);
                }
            }
        }

        private void value(JsonGenerator generator, JsonNode v) throws IOException {
            if (v.isTextual()) {
                generator.writeString(v.asText());
            } else if (v.isIntegralNumber()) {
                generator.writeNumber(v.asLong());
            } else if (v.isFloatingPointNumber()) {
                generator.writeNumber(v.asDouble());
            } else if (v.isBoolean()) {
                generator.writeBoolean(v.asBoolean());
            } else if (v.isNull()) {
                generator.writeNull();
            } else {
                generator.writeRaw(v.toString());
            }
        }

        int getWidth() {
            if (this.result.isEmpty()) {
                return 0;
            } else {
                return this.result.get(0).size();
            }
        }

        @Override
        public void processOne(JsonGenerator generator) throws IOException {
            if (!this.result.isEmpty()) {
                enter(generator);
                if (this.getQueryNode().isComplex()) {
                    complex(generator);
                } else {
                    this.atomic(generator);
                }
                this.moveOn();
                leave(generator);
            }
        }

        @Override
        protected void moveOn() {
            this.result.remove(0);
        }

        private void complex(JsonGenerator generator) throws IOException {
            if (this.result.get(0).isEmpty() && !getQueryNode().isListValued()) {
                generator.writeNull();
            } else {
                for (int i = 0; i < getWidth(); i++) {
                    enterChild(generator);
                    for (QueryCursor cursor : this.getChildrenPos()) {
                        cursor.processOne(generator);
                    }
                    leaveChild(generator);
                }
            }
        }


        public void addResult(JsonNode rootNode) {
            JsonNode node = rootNode.get(getQueryNode().field());
            if (node != null) {
                if (node.isArray()) {
                    List<JsonNode> toAdd = new ArrayList<>();
                    for (JsonNode child : node) {
                        toAdd.add(child);
                        propagateDown(child);
                    }
                    result.add(toAdd);
                } else {
                    propagateDown(node);
                    this.result.add(Collections.singletonList(node));
                }
            } else {
                this.result.add(Collections.emptyList());
            }
        }

        private void propagateDown(JsonNode child) {
            if (getQueryNode().isComplex()) {
                for (QueryCursor childCursor : getChildrenPos()) {
                    if (childCursor instanceof LocalCursor) {
                        LocalCursor localCursor = (LocalCursor) childCursor;
                        localCursor.addResult(child);
                    }
                }
            }
        }

    }

    public static class ConcatCursor extends QueryCursor {

        private final Map<String, LocalCursor> localCursors;

        public ConcatCursor(GraphQLQuery.AbstractSelection queryNode, Map<String, LocalCursor> localCursors) {
            super(queryNode);
            this.localCursors = localCursors;
        }

        public ConcatCursor(GraphQLQuery.AbstractSelection queryNode, QueryCursor parent, Map<String, LocalCursor> localCursors) {
            super(queryNode, parent);
            this.localCursors = localCursors;
        }

        public void addLocalCursor(String sysKey, LocalCursor localCursor) {
            this.localCursors.put(sysKey, localCursor);
        }

         Map<String, LocalCursor> getLocalCursors() {
            return localCursors;
        }

        public void addResults(Map<String, JsonNode> localResults) {
            for (String key : localResults.keySet()) {
                JsonNode jsonNode = localResults.get(key);
                LocalCursor localCursor = this.localCursors.get(key);
                localCursor.addResult(jsonNode);
            }
        }

        @Override
        public void atomic(JsonGenerator generator) throws IOException {
//            for (String key : this.localCursors.keySet()) {
//                this.localCursors.get(key).atomic(generator);
//            }
            for (String key : this.localCursors.keySet()) {
                if (!this.localCursors.get(key).result.isEmpty()) {
                    this.localCursors.get(key).atomic(generator);
                    break;
                }
            }
//            String next = this.localCursors.keySet().iterator().next();
//            this.localCursors.get(next).atomic(generator);
        }

        public void processOneForBranch(String key, JsonGenerator generator) throws IOException {
            enter(generator);
            if (!getQueryNode().isComplex()) {
                atomic(key, generator);
            } else {
                if (!localCursors.containsKey(key)) {
                    if (!getQueryNode().isListValued()) {
                        generator.writeNull();
                    }
                } else {
                    int width = this.localCursors.get(key).getWidth();
                    for (int i = 0; i < width; i++) {
                        enterChild(generator);
                        for (QueryCursor cursor : getChildrenPos()) {
                            if (cursor instanceof ConcatCursor) {
                                ((ConcatCursor) cursor).processOneForBranch(key, generator);
                            } else {
                                processOne(generator);
                            }
                        }
                        leaveChild(generator);
                    }
                    this.localCursors.get(key).result.remove(0); // TODO should become get next
                }
            }
            leave(generator);
        }

        private void atomic(String key, JsonGenerator generator) throws IOException {
            if (this.localCursors.containsKey(key)) {
                this.localCursors.get(key).atomic(generator);
                if (!localCursors.get(key).result.isEmpty()) {
                    this.localCursors.get(key).result.remove(0);
                }
            } else if (!getQueryNode().isListValued()) {
                generator.writeNull();
            }
        }

        @Override
        public void processOne(JsonGenerator generator) throws IOException {
            enter(generator);
            if (!getQueryNode().isComplex()) {
                atomic(generator);
            } else {
                boolean hadValue = false;
                for (String key : this.localCursors.keySet()) { // TODO should become hasNext
                    for (int i = 0; i < this.localCursors.get(key).getWidth(); i++) {
                        hadValue = true;
                        enterChild(generator);
                        for (QueryCursor cursor : getChildrenPos()) {
                            if (cursor instanceof ConcatCursor) {
                                ((ConcatCursor) cursor).processOneForBranch(key,generator);
                            } else {
                                cursor.processOne(generator);
                            }
                        }
                        leaveChild(generator);
                    }
                }
                if (!hadValue && !getQueryNode().isListValued()) {
                    generator.writeNull();
                }
            }
            leave(generator);
        }

        @Override
        protected void moveOn() {
            for (String key : this.localCursors.keySet()) {
                if (!localCursors.get(key).result.isEmpty()) {
                    this.localCursors.get(key).moveOn();
                    break;
                }
            }
        }
    }


    public static class ConcatMergeCursor extends ConcatCursor {

        private final Multimap<String, Key> keys;
        private int width = 0;

        public ConcatMergeCursor(GraphQLQuery.AbstractSelection queryNode, Map<String, LocalCursor> localCursors, Multimap<String, Key> keys) {
            super(queryNode, localCursors);
            this.keys = keys;
        }

        public ConcatMergeCursor(GraphQLQuery.AbstractSelection queryNode, QueryCursor parent, Map<String, LocalCursor> localCursors, Multimap<String, Key> keys) {
            super(queryNode, parent, localCursors);
            this.keys = keys;
        }

        public Set<Name> evaluateKeys(String sysKey, JsonNode node) {
            Set<Name> result = new HashSet<>();
            for (Key k : this.keys.get(sysKey)) {
                try {
                    result.add(k.evaluate(node));
                } catch (KeyNotEvaluated e) {

                }
            }
            return result;
        }


        @Override
        public void addResults(Map<String, JsonNode> localResults) {
            Map<Name, MergeJsonNode> merges = new LinkedHashMap<>();
            for (String key : localResults.keySet()) {
                JsonNode jsonNode = localResults.get(key).get(getLocalCursors().get(key).getQueryNode().field());
                if (jsonNode != null) {
                    if (jsonNode.isArray()) {
                        for (JsonNode node : jsonNode) {
                            mergeIn(merges, key, node);
                        }
                    } else {
                        mergeIn(merges,key,jsonNode);
                    }
                }
            }
            Set<MergeJsonNode> values = new LinkedHashSet<>(merges.values());
            this.width = values.size();
            for (MergeJsonNode m : values) {
                for (QueryCursor cursor : getChildrenPos()) {
                    if (cursor instanceof ConcatCursor) {
                        ConcatCursor cc = (ConcatCursor) cursor;
                        // TODO if many keys --> merging
                        Iterator<String> sysKeyIterator = cc.localCursors.keySet().iterator();
                        boolean notFound = true;
                        while (sysKeyIterator.hasNext() && notFound) {
                            String current = sysKeyIterator.next();
                            if (m.systems.contains(current)) {
                                notFound = false;
                                cc.addResults(Collections.singletonMap(current, m.mergeNode(cc.localCursors.get(current).getQueryNode().field(), cursor.getQueryNode().isListValued())));
                            }
                        }
                        if (notFound) {
                            cc.addResults(Collections.singletonMap(cc.localCursors.keySet().iterator().next(), JsonNodeFactory.instance.objectNode()));
                        }
                    } else if (cursor instanceof LocalCursor) {
                        ((LocalCursor)cursor).addResult(m.mergeNode(cursor.getQueryNode().field(), cursor.getQueryNode().isListValued()));
                    }
                }

            }
        }

        private void mergeIn(Map<Name, MergeJsonNode> merges, String system, JsonNode node) {
            Set<Name> names = evaluateKeys(system, node);
            if (names.isEmpty()) {
                AnonymousIdentifier anon = Name.anonymousIdentifier();
                merges.put(anon, new MergeJsonNode(anon, system, node));
            } else {
                List<MergeJsonNode> mergePartners = new ArrayList<>();
                Iterator<Name> iterator = names.iterator();
                while (iterator.hasNext()) {
                    Name k = iterator.next();
                    if (merges.containsKey(k)) {
                        mergePartners.add(merges.get(k));
                        iterator.remove();
                    }
                }
                if (mergePartners.isEmpty()) {
                    MergeJsonNode mNode = new MergeJsonNode(names, system, node);
                    for (Name k : names) {
                        merges.put(k, mNode);
                    }
                } else {
                    if (mergePartners.size() == 1) {
                        mergePartners.get(0).nodes.add(node);
                        mergePartners.get(0).systems.add(system);
                        mergePartners.get(0).keyValues.addAll(names);
                    } else {
                        Set<Name> allKeys = new HashSet<>();
                        Set<String> allSystems = new LinkedHashSet<>();
                        List<JsonNode> allNodes = new ArrayList<>();
                        for (MergeJsonNode mergePartner : mergePartners) {
                            allKeys.addAll(mergePartner.keyValues);
                            allSystems.addAll(mergePartner.systems);
                            allNodes.addAll(mergePartner.nodes);
                        }
                        allKeys.addAll(names);
                        allSystems.add(system);
                        allNodes.add(node);

                        MergeJsonNode mNode = new MergeJsonNode(allKeys, allSystems, allNodes);
                        for (Name k : allKeys) {
                            merges.put(k, mNode);
                        }
                    }

                }

            }
        }

        @Override
        public void processOne(JsonGenerator generator) throws IOException {
            enter(generator);
            if (!getQueryNode().isComplex()) {
                atomic(generator); // TODO overwrite with merging beahvior
            } else {
                for (int i = 0; i < width; i++) {
                    enterChild(generator);
                    for (QueryCursor cursor : getChildrenPos()) {
                        cursor.processOne(generator);
                        cursor.moveOn();
                    }
                    leaveChild(generator);
                }
            }
            leave(generator);
        }
    }

    protected abstract void moveOn();

    private static class MergeJsonNode {


        private Set<Name> keyValues;
        // TODO work with multimaps here, then merging retriving the right object per endpoint becomes more straightforward
        private Set<String> systems;
        private List<JsonNode> nodes;

        public MergeJsonNode(Set<Name> keyValues, Set<String> systems, List<JsonNode> nodes) {
            this.keyValues = keyValues;
            this.systems = systems;
            this.nodes = nodes;
        }

        public MergeJsonNode(Set<Name> keys, String system, JsonNode node) {
            this.keyValues = keys;
            this.systems = new HashSet<>();
            this.nodes = new ArrayList<>();
            this.systems.add(system);
            this.nodes.add(node);
        }

        public MergeJsonNode(Name key, String system, JsonNode node) {
            this.keyValues = new HashSet<>();
            this.systems = new HashSet<>();
            this.nodes = new ArrayList<>();
            this.keyValues.add(key);
            this.systems.add(system);
            this.nodes.add(node);
        }

        JsonNode mergeNode(String field, boolean isListValued) {
            ObjectNode result = JsonNodeFactory.instance.objectNode();
            if (isListValued) {
                ArrayNode array = JsonNodeFactory.instance.arrayNode();
                for (JsonNode n : this.nodes) {
                    JsonNode node = n.get(field);
                    if (node != null) {
                        if (node.isArray()) {
                            for (JsonNode nn : node) {
                                array.add(nn);
                            }
                        } else {
                            array.add(node);
                        }
                    }
                }
                result.set(field, array);
            } else {
                for (JsonNode n : this.nodes) {
                    if (n.get(field)!= null) {
                        if (result.get(field) != null) {
                            // TODO error
                        }
                        result.set(field, n.get(field));
                    }
                }
            }
            return result;
        }
    }


}
