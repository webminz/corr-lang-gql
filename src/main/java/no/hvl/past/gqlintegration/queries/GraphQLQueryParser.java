package no.hvl.past.gqlintegration.queries;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import no.hvl.past.attributes.StringValue;
import no.hvl.past.gqlintegration.GraphQLEndpoint;
import no.hvl.past.gqlintegration.predicates.FieldArgument;
import no.hvl.past.gqlintegration.predicates.MutationMessage;
import no.hvl.past.gqlintegration.predicates.QueryMesage;
import no.hvl.past.graph.Sketch;
import no.hvl.past.graph.elements.Triple;
import no.hvl.past.graph.trees.TypedChildrenRelation;
import no.hvl.past.graph.trees.TypedNode;
import no.hvl.past.names.Name;
import no.hvl.past.names.PrintingStrategy;
import no.hvl.past.systems.MessageArgument;
import no.hvl.past.systems.MessageType;
import no.hvl.past.systems.Sys;
import no.hvl.past.util.Pair;
import no.hvl.past.util.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.function.BiFunction;

class GraphQLQueryParser {

    private Logger logger = Logger.getLogger(GraphQLQueryParser.class);

    private ParserState parserState;
    private StringBuilder nameBuilder;
    private StringBuilder argumentBuilder;
    private Stack<GraphQLQuery.Node> parentNodeStack;
    private GraphQLQuery.Node currentNode;
    private Sys schema;
    private BiFunction<Name, String, Optional<Triple>> lookup;

    private static final String SPECIAL_CHARS = "{}(),:\"";

    public GraphQLQueryParser(Sys sketch, BiFunction<Name, String, Optional<Triple>> lookup, GraphQLQuery.Node root) {
        this.schema = sketch;
        this.currentNode = null;
        this.parserState = ParserState.AWAITING_FIELD;
        this.nameBuilder = new StringBuilder();
        this.argumentBuilder = new StringBuilder();
        this.lookup = lookup;
        this.parentNodeStack = new Stack<>();
        this.parentNodeStack.push(root);
    }

    public static GraphQLQuery parse(GraphQLEndpoint endpoint, String query, String operationName, Map<String, Object> variables) throws IOException {
        // TODO arguments that are objects
        // TODO support named operations
        // TODO support fragments
        // TODO support variables
        List<GraphQLQuery.QueryRoot> qRoots = new ArrayList<>();
        String rest = query.trim();
        int firstIdx = rest.indexOf('{');
        String queryType = rest.substring(0, firstIdx).trim();
        rest = rest.substring(firstIdx + 1);
        while (!rest.isEmpty()) {
            int nextIndex = rest.indexOf('{');
            String queryOp = rest.substring(0, nextIndex).trim();
            Pair<Triple,Boolean> messageReturnTripleAndIsMutation = lookupMessageType(endpoint, queryType, queryOp);
            GraphQLQuery.QueryRoot root = new GraphQLQuery.QueryRoot(queryOp, messageReturnTripleAndIsMutation.getRight(), messageReturnTripleAndIsMutation.getLeft());
            qRoots.add(root);
            GraphQLQueryParser parser = new GraphQLQueryParser(endpoint, endpoint::lookupField ,root);
            rest = parser.process(rest.substring(nextIndex + 1).trim());
            if (rest.trim().startsWith("}")) {
                break;
            }
        }
        rest = rest.substring(rest.indexOf('}') + 1).trim();
        if (!rest.isEmpty()) {
            throw new IOException("There must only be one operation!");
        }
        return new GraphQLQuery(qRoots, endpoint.schema(), Name.anonymousIdentifier());// TODO can use operationName ehere
    }

    private static Pair<Triple, Boolean> lookupMessageType(
            GraphQLEndpoint endpoint,
            String queryType,
            String queryOp) throws IOException {
        // TODO if qt is empty --> query
        if (queryType.isEmpty()) {
            Optional<MessageType> message = endpoint.getMessage(queryOp);
            if (message.isPresent()) {
                return new Pair<>(message.get().arguments().filter(MessageArgument::isOutput).findFirst().get().asEdge(), message.get() instanceof MutationMessage);
            } else {
                throw new IOException("Cannot find query or mutation '" + queryOp + "'");
            }
        } else {
            String qt =  queryType.contains(" ")? queryOp.substring(0, queryOp.indexOf(' ')).trim() : queryType;
            Optional<MessageType> message = endpoint.getMessage(StringUtils.capitalizeFirst(qt) + "." + queryOp);
            if (message.isPresent()) {
                return new Pair<>(message.get().arguments().filter(MessageArgument::isOutput).findFirst().get().asEdge(), message.get() instanceof MutationMessage);
            } else {
                throw new IOException("Cannot find query or mutation '" + queryOp + "' of '" + StringUtils.capitalizeFirst(queryType) + "'");
            }
        }
    }

    private String process(String rest) throws IOException {
        String toProcess = rest;
        boolean isEnd;
        do {
            isEnd = accept(toProcess.charAt(0));
            toProcess = toProcess.substring(1);
        } while (!isEnd);
        return toProcess;
    }


    public boolean accept(char charAt) throws IOException {
        switch (this.parserState) {
            case AWAITING_FIELD:
                if (charAt == '}') {
                    return parentNodeStack.size() == 1;
                }
                if (isRegularCharacter(charAt)) {
                    this.parserState = ParserState.IN_FIELD;
                    this.nameBuilder.append(charAt);
                }
                return false;
            case IN_FIELD:
                if (Character.isWhitespace(charAt)) {
                    this.parserState = ParserState.AFTER_FIELD;
                } else if (SPECIAL_CHARS.contains(charAt + "")) {
                    this.parserState = ParserState.AFTER_FIELD;
                    this.accept(charAt);
                } else {
                    this.nameBuilder.append(charAt);
                }
                return false;
            case AFTER_FIELD:
                if (charAt == '(') {
                    this.finishField();
                    this.parserState = ParserState.AWAIT_PARAM;
                } else if (charAt == '{') {
                    this.finishField();
                    this.moveDown();
                    this.parserState = ParserState.AWAITING_FIELD;
                } else if (charAt == '}') {
                    this.finishField();
                    if (this.moveUp()) {
                        return true;
                    }
                    this.parserState = ParserState.AWAITING_FIELD;
                } else if (isRegularCharacter(charAt)) {
                    this.finishField();
                    this.nameBuilder.append(charAt);
                    this.parserState = ParserState.IN_FIELD;
                }
                return false;
            case AWAIT_PARAM:
                if (charAt == ')') {
                    this.parserState = ParserState.AFTER_FIELD;
                } else if (isRegularCharacter(charAt)) {
                    this.parserState = ParserState.IN_PARAM;
                    this.nameBuilder.append(charAt);
                }
                return false;
            case IN_PARAM:
                if (Character.isWhitespace(charAt) || charAt == ',') {
                    this.parserState = ParserState.AWAIT_VALUE;
                } else if (isRegularCharacter(charAt)) {
                    this.nameBuilder.append(charAt);
                }
                return false;
            case AWAIT_VALUE:
                if (charAt == '"') {
                    this.argumentBuilder.append(charAt);
                    this.parserState = ParserState.IN_VALUE;
                } else if (isRegularCharacter(charAt)) {
                    this.argumentBuilder.append(charAt);
                    this.parserState = ParserState.IN_VALUE;
                }
                return false;
            case IN_VALUE:
                if (charAt == '"' || charAt == ')' || Character.isWhitespace(charAt)) {
                    String key = nameBuilder.toString();
                    nameBuilder = new StringBuilder();
                    String value = argumentBuilder.toString();
                    argumentBuilder = new StringBuilder();
                    if (value.startsWith("\"")) {
                        value = value.substring(1, value.length() - 1);
                    }
                    addAttr(key, value);
                    if (charAt == ')') {
                        this.parserState = ParserState.AFTER_FIELD;
                    } else {
                        this.parserState = ParserState.AWAIT_PARAM;
                    }
                }
                return false;
        }
        return false;
    }

    private boolean moveUp() {
        if (this.parentNodeStack.size() <= 1) {
            return true;
        }
        this.currentNode = parentNodeStack.pop();
        return false;
    }

    private void moveDown() {
        this.parentNodeStack.push(this.currentNode);
        this.currentNode = null;
    }

    private void addAttr(String key, String value) {
        Optional<Triple> first = lookup.apply(currentNode.typing(), key);
        if (first.isPresent()) {
            this.currentNode.addAttribute(key, value, makeValue(value), first.get());
        } else {
            // TODO maybe gets obsolete if we assume Field arguments as hyperegdes
            Optional<FieldArgument> arg = schema.schema().diagramsOn(Triple.node(currentNode.typing()))
                    .filter(d -> d instanceof FieldArgument)
                    .map(d -> (FieldArgument) d)
                    .filter(f -> f.getFieldName().equals(key))
                    .findFirst();
            if (arg.isPresent()) {
                this.currentNode.addAttribute(key, value, makeValue(value),
                        Triple.edge(
                                ((TypedNode) this.currentNode.parentRelation().get().parent()).nodeType().get(),
                                ((TypedChildrenRelation) currentNode.parentRelation().get()).edgeTyping().get(),
                                this.currentNode.typing()));
            } else {
                logger.warn("The argument '" + key + "' on '" + this.currentNode.getLabel() + "' is not found");
            }
        }

    }

    @NotNull
    private StringValue makeValue(String value) {
        return Name.value(value); // TODO lookup typing etc...
    }


    private boolean isRegularCharacter(char charAt) {
        return !Character.isWhitespace(charAt) && !SPECIAL_CHARS.contains(charAt + "");
    }

    private void finishField() throws IOException {
        String field = this.nameBuilder.toString();
        this.nameBuilder = new StringBuilder();
        Optional<Triple> first = lookup.apply(parentNodeStack.peek().typing(), field);
        if (first.isPresent()) {
            GraphQLQuery.Node node = new GraphQLQuery.Node(field, first.get().getTarget());
            node.addParent(parentNodeStack.peek(), first.get(), !schema.isAttributeType(first.get()), schema.isCollectionValued(first.get()));
            this.currentNode = node;
        } else {
            throw new IOException("Field '" + field + "' is not found in type '" + parentNodeStack.peek().typing().print(PrintingStrategy.IGNORE_PREFIX) + "'!");
        }
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
}
