package no.hvl.past.gqlintegration.queries;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import no.hvl.past.graph.GraphMorphism;
import no.hvl.past.graph.Sketch;
import no.hvl.past.graph.elements.Triple;
import no.hvl.past.graph.predicates.*;
import no.hvl.past.keys.Key;
import no.hvl.past.keys.KeyNotEvaluated;
import no.hvl.past.names.Name;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class GraphQLQueryDivider implements GraphQLQueryHandler {

    private static Logger logger = Logger.getLogger(GraphQLQueryDivider.class);

    // TODO make cursor its own class to have two advantages: 1) it can pre-calculate local names and field modifiers while requests are processed aynchronously 2) can make it technology independent of JSON

    private enum TraversePosition {
        MERGED_OBJECT,
        MERGED_FIELD,
        MERGED_OBJECT_FUSE_ARRAY,
        MERGED_OBJECT_CONCAT_ARRAY,
        MERGED_VALUE_ARRAY,
        LOCAL_OBJECT,
        LOCAL_FIELD,
        OBJECT_ARRAY,
        VALUE_ARRAY,
        VALUE
    }

    private class TraverseStep {
        private final Map<GraphMorphism, JsonNode> cursorPositions;
        private final TraversePosition position;
        private final JsonGenerator target;
        private final GraphQLQueryNode queryNode;

        private TraverseStep(Map<GraphMorphism, JsonNode> cursorPositions,
                            TraversePosition position,
                            JsonGenerator target,
                            GraphQLQueryNode queryNode) {
            this.cursorPositions = cursorPositions;
            this.position = position;
            this.target = target;
            this.queryNode = queryNode;
        }

        public void advance() throws IOException, KeyNotEvaluated {
            switch (position) {
                case VALUE:
                    handleValue();
                    break;
                case LOCAL_FIELD:
                    handleField();
                    break;
                case VALUE_ARRAY:
                    handleArray(false);
                    break;
                case OBJECT_ARRAY:
                    handleArray(true);
                    break;
                case LOCAL_OBJECT:
                    handleObject();
                    break;
                case MERGED_OBJECT:
                    handleMergedObject();
                    break;
                case MERGED_VALUE_ARRAY:
                    handleConcat(false);
                    break;
                case MERGED_OBJECT_CONCAT_ARRAY:
                    handleConcat(true);
                    break;
                case MERGED_OBJECT_FUSE_ARRAY:
                    handleMergeObjects();
                    break;
                case MERGED_FIELD:
                    handleMergedField();
                    break;
                default:
                    break;
            }
        }

        private void handleMergeObjects() throws KeyNotEvaluated, IOException {
            Key key = keyMap.get(comprehensiveSchema.carrier().get(queryNode.getField()).get());
            Set<Name> toIterate = new LinkedHashSet<>();
            for (GraphMorphism ep : cursorPositions.keySet()) {
                JsonNode node = cursorPositions.get(ep);
                if (node.isArray()) {
                    Iterator<JsonNode> iterator = node.iterator();
                    while (iterator.hasNext()) {
                        JsonNode n = iterator.next();
                        Name k = key.evaluate(n);
                        toIterate.add(k);
                        addToCache(ep, k, n);
                    }
                } else {
                    Name k = key.evaluate(node);
                    toIterate.add(k);
                    addToCache(ep, k , node);
                }
            }
            for (Name k : toIterate) {
                Map<GraphMorphism, JsonNode> mergedCursor = objectCursorCache.get(k);
                if (mergedCursor.keySet().size() <= 1) {
                    new TraverseStep(mergedCursor, TraversePosition.LOCAL_OBJECT, target, queryNode).advance();
                } else {
                    new TraverseStep(mergedCursor, TraversePosition.MERGED_OBJECT, target, queryNode).advance();
                }
            }
        }

        private void addToCache(GraphMorphism endpoint, Name key, JsonNode node) {
            if (objectCursorCache.containsKey(key)) {
                objectCursorCache.get(key).put(endpoint, node);
            } else {
                Map<GraphMorphism, JsonNode> cursorPos = new LinkedHashMap<>();
                cursorPos.put(endpoint, node);
                objectCursorCache.put(key, cursorPos);
            }
        }

        private void handleMergedField() throws IOException, KeyNotEvaluated {
            Triple edge = comprehensiveSchema.carrier().get(queryNode.getField()).get();
            boolean hasKeys = keyMap.containsKey(edge.getTarget());
            boolean isObject = hasObjectReturnType(edge);
            target.writeFieldName(plainNames.get(queryNode.getField()));
            if (isObject) {
                if (hasKeys) {
                    new TraverseStep(cursorPositions, TraversePosition.MERGED_OBJECT_FUSE_ARRAY, target, queryNode).advance();
                } else {
                    new TraverseStep(cursorPositions, TraversePosition.MERGED_OBJECT_CONCAT_ARRAY, target, queryNode).advance();
                }
            } else {
                new TraverseStep(cursorPositions, TraversePosition.MERGED_VALUE_ARRAY, target, queryNode).advance();

            }
        }

        private void handleConcat(boolean isObject) throws IOException, KeyNotEvaluated {
            target.writeStartArray();
            for (GraphMorphism ep : cursorPositions.keySet()) {
                JsonNode root = cursorPositions.get(ep);
                for (String f : localNames(ep, queryNode.getField())) {
                    JsonNode node = root.get(f);
                    if (node.isArray()) {
                        Iterator<JsonNode> iterator = node.iterator();
                        while (iterator.hasNext()) {
                            new TraverseStep(
                                    Collections.singletonMap(ep, iterator.next()),
                                    isObject ? TraversePosition.LOCAL_OBJECT : TraversePosition.VALUE,
                                    target,
                                    queryNode).advance();
                        }
                    } else {
                        new TraverseStep(Collections.singletonMap(ep, node),
                                isObject ? TraversePosition.LOCAL_OBJECT : TraversePosition.VALUE,
                                target,
                                queryNode).advance();
                    }
                }
            }
            target.writeEndArray();
        }



        private void handleMergedObject() throws IOException, KeyNotEvaluated {
            target.writeStartObject();
            for (GraphQLQueryNode child : queryNode.getChildren()) {
                Map<GraphMorphism, JsonNode> canDeliver = new LinkedHashMap<>();
                for (GraphMorphism ep : cursorPositions.keySet()) {
                    if (ep.selectByLabel(child.getField()).anyMatch(x -> true)) {
                        canDeliver.put(ep, cursorPositions.get(ep));
                    }
                }
                if (canDeliver.size() > 1) {
                    new TraverseStep(canDeliver,
                            TraversePosition.MERGED_FIELD,
                            target,
                            child).advance();
                } else if (canDeliver.size() == 1) {
                    new TraverseStep(canDeliver,
                            TraversePosition.LOCAL_FIELD,
                            target,
                            child).advance();
                } else {
                    target.writeFieldName(plainNames.get(child.getField()));
                    target.writeNull();
                }
            }
            target.writeEndObject();
        }


        private void handleObject() throws IOException, KeyNotEvaluated {
            target.writeStartObject();
            GraphMorphism key = cursorPositions.keySet().iterator().next();
            JsonNode jsonNode = cursorPositions.get(key);
            for (GraphQLQueryNode child : queryNode.getChildren()) {
                new TraverseStep(Collections.singletonMap(key, jsonNode),
                        TraversePosition.LOCAL_FIELD,
                        target,
                        child).advance();
            }
            target.writeEndObject();
        }

        private void handleArray(boolean isObject) throws IOException, KeyNotEvaluated {
            target.writeStartArray();
            GraphMorphism key = cursorPositions.keySet().iterator().next();
            JsonNode jsonNode = cursorPositions.get(key);
            Iterator<JsonNode> iterator = jsonNode.iterator();
            while (iterator.hasNext()) {
                new TraverseStep(Collections.singletonMap(key, iterator.next()),
                        isObject ?  TraversePosition.LOCAL_OBJECT : TraversePosition.VALUE,
                        target,
                        queryNode).advance();
            }
            target.writeEndArray();
        }

        private void handleField() throws IOException, KeyNotEvaluated {
            GraphMorphism key = cursorPositions.keySet().iterator().next();
            if (key.selectByLabel(queryNode.getField()).anyMatch(x -> true)) {
                target.writeFieldName(plainNames.get(queryNode.getField()));
                Triple edge = comprehensiveSchema.carrier().get(queryNode.getField()).get();
                Set<String> fieldNames = localNames(key, queryNode.getField());

                boolean listValued = isListValued(edge);
                boolean isObject = hasObjectReturnType(edge);
                if (listValued || fieldNames.size() > 1) {
                    if (fieldNames.size() == 1) {
                        String fName = fieldNames.iterator().next();
                        if (isObject) {
                            new TraverseStep(Collections.singletonMap(key, cursorPositions.get(key).get(fName)), TraversePosition.OBJECT_ARRAY, target, queryNode).advance();
                        } else {
                            new TraverseStep(Collections.singletonMap(key, cursorPositions.get(key).get(fName)), TraversePosition.VALUE_ARRAY, target, queryNode).advance();
                        }
                    } else {
                        // TODO multiple preimages
                    }
                } else {
                    String fName = fieldNames.iterator().next();
                    if (isObject) {
                        new TraverseStep(Collections.singletonMap(key, cursorPositions.get(key).get(fName)), TraversePosition.LOCAL_OBJECT, target, queryNode).advance();
                    } else {
                        new TraverseStep(Collections.singletonMap(key, cursorPositions.get(key).get(fName)), TraversePosition.VALUE, target, queryNode).advance();
                    }
                }
            }
        }

        private void handleValue() throws IOException {
            GraphMorphism ep = cursorPositions.keySet().iterator().next();
            JsonNode jsonNode = cursorPositions.get(ep);
            if (jsonNode.isTextual()) {
                target.writeString(jsonNode.asText());
            } else if (jsonNode.isIntegralNumber()) {
                target.writeNumber(jsonNode.asLong());
            } else if (jsonNode.isFloatingPointNumber()) {
                target.writeNumber(jsonNode.asDouble());
            } else if (jsonNode.isBoolean()) {
                target.writeBoolean(jsonNode.asBoolean());
            } else if (jsonNode.isNull()) {
                target.writeNull();
            } else {
                target.writeRaw(jsonNode.toString());
            }
        }

    }

    private Set<String> localNames(GraphMorphism ep, Name fieldName) {
        return ep.selectByLabel(fieldName)
                .map(Triple::getLabel)
                .map(n -> handlerMap.get(ep).plainNames().get(n))
                .collect(Collectors.toSet());
    }

    private boolean isListValued(Triple edge) {
        return comprehensiveSchema.diagramsOn(edge).noneMatch(diag -> TargetMultiplicity.class.isAssignableFrom(diag.label().getClass()) && ((TargetMultiplicity) diag.label()).getUpperBound() <= 1);
    }

    private boolean hasObjectReturnType(Triple edge) {
        return comprehensiveSchema.diagramsOn(Triple.node(edge.getTarget())).noneMatch(diag -> {
            return StringDT.class.isAssignableFrom(diag.label().getClass()) ||
                    IntDT.class.isAssignableFrom(diag.label().getClass()) ||
                    FloatDT.class.isAssignableFrom(diag.label().getClass()) ||
                    BoolDT.class.isAssignableFrom(diag.label().getClass());
        });
    }


    private static final Name QUERY_ROOT = Name.identifier("query");

    private final String url;
    private final Sketch comprehensiveSchema;
    private final Map<GraphMorphism, GraphQLQueryHandler>  handlerMap;
    private final Map<Name, Key> keyMap;
    private final Map<Name, Map<GraphMorphism, JsonNode>> objectCursorCache;
    private final Map<Name, String> plainNames;

    public GraphQLQueryDivider(String url,
                               Sketch comprehensiveSchema,
                               Map<Name, String> plainNames,
                               Map<GraphMorphism, GraphQLQueryHandler> handlerMap,
                               Map<Name, Key> keyMap) {
        this.url = url;
        this.handlerMap = handlerMap;
        this.keyMap = keyMap;
        this.comprehensiveSchema = comprehensiveSchema;
        this.plainNames = plainNames;
        this.objectCursorCache = new LinkedHashMap<>();
    }


    @Override
    public void handle(InputStream i, OutputStream o) throws IOException {
        LocalDateTime startParse = LocalDateTime.now();
        GraphQLQuery query = parse(i);
        logger.debug("Query handling - parsing took:" + Duration.between(startParse, LocalDateTime.now()).toMillis() + " ms");
        LocalDateTime startSplit = LocalDateTime.now();
        Map<GraphMorphism, GraphQLQuery> localQueries = split(query);
        logger.debug("Query handling - split took:" + Duration.between(startSplit, LocalDateTime.now()).toMillis() + " ms");
        LocalDateTime startDelegate = LocalDateTime.now();
        Map<GraphMorphism, InputStream> localQueryResults = executeQueries(localQueries);
        logger.debug("Query handling - delegate took:" + Duration.between(startDelegate, LocalDateTime.now()).toMillis() + " ms");
        try {
            LocalDateTime startMerge = LocalDateTime.now();
            merge(localQueryResults, query, o);
            logger.debug("Query handling - merge took:" + Duration.between(startMerge, LocalDateTime.now()).toMillis() + " ms");
        } catch (KeyNotEvaluated keyNotEvaluated) {
            throw new IOException(keyNotEvaluated);
        }
    }


    @Override
    public String endpointUrl() {
        return url;
    }

    @Override
    public Sketch getSchema() {
        return comprehensiveSchema;
    }

    @Override
    public Map<Name, String> plainNames() {
        return plainNames;
    }


    public void merge(
            Map<GraphMorphism, InputStream> localQueryResults,
            GraphQLQuery originalQuery,
            OutputStream outputStream) throws IOException, KeyNotEvaluated {
        JsonGenerator generator = new JsonFactory().createGenerator(outputStream);
        generator.writeStartObject();
        generator.writeFieldName("data");
        ObjectMapper objectMapper = new ObjectMapper();

        Map<GraphMorphism, JsonNode> cursors = new LinkedHashMap<>();
        for (GraphMorphism ep : localQueryResults.keySet()) {
            cursors.put(ep, objectMapper.readTree(localQueryResults.get(ep)).get("data")); // TODO local error handling?
        }
        new TraverseStep(cursors,
                TraversePosition.MERGED_OBJECT,
                generator,
                originalQuery.root()).advance();

        generator.writeEndObject();
        generator.flush();
        outputStream.close();
    }


    public Map<GraphMorphism, InputStream> executeQueries(Map<GraphMorphism, GraphQLQuery> localQueries)  throws IOException {
        Map<GraphMorphism, InputStream> localQueryResults = new LinkedHashMap<>();
        for (GraphMorphism ep : localQueries.keySet()) {
            if (handlerMap.containsKey(ep)) {
                localQueryResults.put(ep, handlerMap.get(ep).resolveAsStream(localQueries.get(ep)));
            }
        }
        return localQueryResults;
    }


    public Map<GraphMorphism, GraphQLQuery> split(GraphQLQuery query) {
        Map<GraphMorphism, GraphQLQuery> localQueries = new LinkedHashMap<>();
        for (GraphMorphism ep : this.handlerMap.keySet()) {
            GraphQLQuery localQuery = new GraphQLQuery(buildLocalQuery(new GraphQLQueryNode.Builder(query.root().getField()), query.root(), ep));
            localQueries.put(ep, localQuery);
            logger.debug("Local Query will be sent to " + handlerMap.get(ep).endpointUrl() + ", query content:\n" + localQuery.serialize(handlerMap.get(ep).plainNames()));
        }
        Set<GraphMorphism> toDelete = new HashSet<>();
        for (GraphMorphism ep : localQueries.keySet()) {
            if (localQueries.get(ep).root().getChildren().isEmpty()) {
                logger.debug("Local Query to " + handlerMap.get(ep).endpointUrl() + " will be skipped since it empty!");
                toDelete.add(ep);
            }
        }
        for (GraphMorphism m : toDelete) {
            localQueries.remove(m);
        }
        return localQueries;
    }

    private GraphQLQueryNode.Builder buildLocalQuery(
            GraphQLQueryNode.Builder builder,
            GraphQLQueryNode node,
            GraphMorphism morphism) {

        for (GraphQLQueryNode child : node.getChildren()) {
            Optional<Triple> original = morphism.selectByLabel(child.getField()).findAny();
            if (original.isPresent()) {
                GraphQLQueryNode.Builder childBuilder = builder.startChild(original.get().getLabel());
                buildLocalQuery(childBuilder, child, morphism);
            }
        }

        return builder;
    }
}
