package no.hvl.past.gqlintegration.queries;


import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import no.hvl.past.attributes.ErrorValue;
import no.hvl.past.gqlintegration.GraphQLEndpoint;
import no.hvl.past.gqlintegration.syntax.parser.GraphQLQueryBaseListener;
import no.hvl.past.gqlintegration.syntax.parser.GraphQLQueryLexer;
import no.hvl.past.graph.elements.Triple;
import no.hvl.past.names.Name;
import no.hvl.past.names.Value;
import no.hvl.past.systems.MessageType;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.*;

// TODO GraphQL Parsing 2.0: support fragments
class GraphQLQueryParser extends GraphQLQueryBaseListener {


    // Given from the start
    private final GraphQLEndpoint endpoint;
    private final String operationName;
    private final com.fasterxml.jackson.databind.JsonNode variables;
    private final Logger logger;


    private final Stack<GraphQLQuery.Node> parentNodeStack;
    private String toProcess;


    // New Stuff
    private final Multimap<String, GraphQLQuery.InputObject> translatedComplexVariables;
    private final Multimap<String, Value> translatedSimpelVariables;

    private GraphQLQuery.QueryRoot currentQueryRoot;


    private final Stack<State> state;
    private final List<GraphQLQuery.QueryRoot> queryRoots;

    private boolean currentIsMutation;
    private String currentOperationName;
    private String currentVariableName;
    private boolean currentIsMandatory;
    private boolean currentIsSetValued;
    private String currentTypeReference;

    private final Stack<String> currentArgumentName;
    private Name contextType;

    private boolean currentIsComplex;
    private final List<Value> currentValues;

    private final Stack<Triple> contextTypingStack;


    private final GraphQueryErrorMessage potentialError;

    public GraphQLQueryParser(
            GraphQLEndpoint endpoint,
            String query,
            String operationName,
            com.fasterxml.jackson.databind.JsonNode variables) {
        this.logger = LogManager.getLogger(getClass());
        this.endpoint = endpoint;

        this.toProcess = query;
        this.operationName = operationName;
        this.variables = variables;

        this.potentialError = new GraphQueryErrorMessage();

        this.state = new Stack<>();
        this.parentNodeStack = new Stack<>();
        this.currentArgumentName = new Stack<>();
        this.contextTypingStack = new Stack<>();

        this.translatedComplexVariables =  MultimapBuilder.hashKeys().arrayListValues().build();
        this.translatedSimpelVariables = MultimapBuilder.hashKeys().arrayListValues().build();
        this.currentValues = new ArrayList<>();
        this.queryRoots = new ArrayList<>();
    }

    public GraphQLQuery parse() throws IOException, GraphQueryErrorMessage {
        // preprocessing
        this.toProcess = toProcess.trim();
        if (!(toProcess.startsWith("query") || toProcess.startsWith("mutation") || toProcess.startsWith("subscription"))) {
            // some clients send GraphQL queries that does not start with an entry point operation
            // these are then interpreted as a query.
            toProcess = "query { " + toProcess + " }";
        }


        CharStream stream = CharStreams.fromString(this.toProcess);
        GraphQLQueryLexer lexer = new GraphQLQueryLexer(stream);
        no.hvl.past.gqlintegration.syntax.parser.GraphQLQueryParser parser = new no.hvl.past.gqlintegration.syntax.parser.GraphQLQueryParser(new CommonTokenStream(lexer));
        new ParseTreeWalker().walk(this, parser.document());




        if (potentialError.isErrorFree()) {

            return new GraphQLQuery(queryRoots, endpoint, Name.anonymousIdentifier());
        } else {
            logger.error("GraphQL Query parsing failed", potentialError);
            throw potentialError;
        }

    }


    public static GraphQLQuery parse(
            GraphQLEndpoint endpoint,
            String query,
            String operationName,
            com.fasterxml.jackson.databind.JsonNode variables) throws IOException, GraphQueryErrorMessage {
        return new GraphQLQueryParser(endpoint, query, operationName, variables).parse();
    }


    private enum State {
        OPERATION,
        VARIABLE,
        TYPE_REF,
        FIELD,
        ROOT_FIELD,
        ARGUMENT,
        VARIABL_REF,
        STRICT_ERROR_SKIP,
        AWAITING_CHILDREN,
        ARRAY,
        OBJECT,
        STRICT_DONE_SKIP
    }

    @Override
    public void exitDocument(no.hvl.past.gqlintegration.syntax.parser.GraphQLQueryParser.DocumentContext ctx) {
        if (operationName != null) {
            if (!operationName.equals(currentOperationName)) { // current will be the last in this situation
                queryRoots.clear();
                potentialError.addError(ParsingErrorMessages.unknownOperationChosen(operationName));
            }
        }
    }

    @Override
    public void enterOperationDefinition(no.hvl.past.gqlintegration.syntax.parser.GraphQLQueryParser.OperationDefinitionContext ctx) {
        if (!this.queryRoots.isEmpty()) {
            if (operationName != null) {
                if (operationName.equals(currentOperationName)) {
                    // last parsed operation was the right one, can just skip the rest
                    this.state.push(State.STRICT_DONE_SKIP);
                } else {
                    queryRoots.clear();
                }
            } else {
                queryRoots.clear();
                this.potentialError.addError(ParsingErrorMessages.noOperationChosen());
                this.state.push(State.STRICT_ERROR_SKIP);
            }
        }
        if (state.isEmpty() || (!state.peek().equals(State.STRICT_ERROR_SKIP) && !state.peek().equals(State.STRICT_DONE_SKIP))) {
            this.state.push(State.OPERATION);
        }
    }

    @Override
    public void exitOperationDefinition(no.hvl.past.gqlintegration.syntax.parser.GraphQLQueryParser.OperationDefinitionContext ctx) {
        if (state.peek().equals(State.STRICT_ERROR_SKIP)) {
            if (this.operationName != null && this.currentOperationName != null && !this.currentOperationName.equals(operationName)) {
                potentialError.clear();
                state.pop();
            }
        } else {
            if (state.peek().equals(State.OPERATION)) {
                state.pop();
                if (queryRoots.isEmpty()) {
                    potentialError.addError(ParsingErrorMessages.notRootProvided(currentOperationName, currentIsMutation));
                }
            }
        }
    }

    @Override
    public void enterVariableDefinition(no.hvl.past.gqlintegration.syntax.parser.GraphQLQueryParser.VariableDefinitionContext ctx) {
        this.state.push(State.VARIABLE);
    }


    private GraphQLQuery.InputObject translate(JsonNode jsonNode, String label, int line, int column) {
        Name type;
        if (contextTypingStack.isEmpty()) {
            type = contextType;
        } else {
            type = contextTypingStack.peek().getTarget();
        }
        if (jsonNode.isObject() || jsonNode.isArray()) {
            GraphQLQuery.InputObject io = new GraphQLQuery.InputObject(label, type);
            Iterator<String> it = jsonNode.fieldNames();
            while (it.hasNext()) {
                String property = it.next();
                Optional<Triple> triple = endpoint.lookupField(type, property);
                // TODO GraphQL Parsing 2.0: support for arrays
                // TODO GraphQL Parsing 2.0: checking that all constraints on field are fulfilled
                triple.ifPresent(t -> {
                    if (endpoint.isSimpleTypeNode(t.getTarget())) {
                        io.addSimpleArgument(property, jsonNode.get(property).toString(), translateSimple(jsonNode.get(property), t.getTarget(), property, line, column), t);
                    } else {
                        contextTypingStack.push(t);
                        io.addComplexArgument(property, translate(jsonNode.get(property), property, line, column), t);
                        contextTypingStack.pop();
                    }
                });

            }
            return io;
        } else {
            handleWrongInputForType(jsonNode.toPrettyString(), endpoint.displayName(type), label, line, column);
            return null;
        }

    }



    private Value translateSimple(JsonNode jsonNode, Name type, String lable, int line, int column) {
        if (jsonNode.isObject()) {
            handleWrongInputForType(jsonNode.toPrettyString(), endpoint.displayName(type), lable , line, column);
            return null;
        }

        // TODO GraphQL Parsing 2.0:  null-value ?!

        if (endpoint.isStringType(type) && jsonNode.isTextual()) {
            return Name.value(jsonNode.textValue());
        } else if (endpoint.isIntType(type) && jsonNode.isIntegralNumber()) {
            return Name.value(jsonNode.intValue());
        } else if (endpoint.isFloatType(type) && jsonNode.isFloatingPointNumber()) {
            return Name.value(jsonNode.doubleValue());
        } else if (endpoint.isBoolType(type) && jsonNode.isBoolean()) {
            return jsonNode.booleanValue() ? Name.trueValue() : Name.falseValue();
        } else if (endpoint.isSimpleTypeNode(type)) {
            // interpreting the rest as strings ?!
            if (jsonNode.isTextual()) {
                return Name.value(jsonNode.textValue());
            } else {
                return Name.value(jsonNode.toString());
            }
        } else {
            // TODO GraphQL Parsing 2.0: enum value and custom
            return ErrorValue.INSTANCE;
        }
    }

    @Override
    public void exitVariableDefinition(no.hvl.past.gqlintegration.syntax.parser.GraphQLQueryParser.VariableDefinitionContext ctx) {
        if (this.state.peek().equals(State.VARIABLE) || (this.state.peek().equals(State.ARRAY) && this.state.contains(State.VARIABLE))) {
            // TODO GraphQL Parsing 2.0: Better handling of arrays
            if (this.variables.has(this.currentVariableName)) {
                if (currentIsComplex) {
                    this.translatedComplexVariables.put(currentVariableName, translate(
                            variables.get(currentVariableName),
                            currentVariableName,
                            ctx.getStart().getLine(),
                            ctx.getStart().getCharPositionInLine()));
                } else {
                    this.translatedSimpelVariables.put(currentVariableName, translateSimple(
                            variables.get(currentVariableName),
                            contextType,
                            currentVariableName,
                            ctx.getStart().getLine(),
                            ctx.getStart().getCharPositionInLine()));
                }
            } else {
                if (currentIsComplex) {
                    if (parentNodeStack.isEmpty() && currentIsMandatory) {
                        handleErrorNoValueProvidedForVariable(currentVariableName, endpoint.displayName(contextType), ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
                    } else if (!parentNodeStack.isEmpty()) {
                        this.translatedComplexVariables.put(currentVariableName, (GraphQLQuery.InputObject) parentNodeStack.peek());
                    }
                } else {
                    if (currentValues.isEmpty() && currentIsMandatory) {
                        handleErrorNoValueProvidedForVariable(currentVariableName, endpoint.displayName(contextType), ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
                    } else {
                        for (Value v : currentValues) {
                            this.translatedSimpelVariables.put(currentVariableName, v);
                        }
                    }
                }
            }

            this.currentValues.clear();
            this.currentVariableName = null;
            this.contextType = null;
            this.currentIsComplex = false;
            this.currentIsSetValued = false;
            this.currentIsMandatory = false;
            this.parentNodeStack.clear();
            this.contextTypingStack.clear();
            if (this.state.peek().equals(State.ARRAY)) {
                this.state.pop();
            }
            if (this.state.peek().equals(State.VARIABLE)) {
                this.state.pop();
            }
        }

    }



    private void findContextType(int line, int column) {
        Optional<Triple> triple = endpoint.schema().carrier().get(Name.identifier(this.currentTypeReference));


        if (!triple.isPresent()) {
            handleErrorUnknownType(this.currentTypeReference, line, column);
            return;
        }

        triple.ifPresent(t -> {
            this.contextType = t.getLabel();
            this.currentIsComplex = !endpoint.isSimpleTypeNode(t.getLabel());
            this.contextTypingStack.push(t);

        });

    }

    private void handleErrorNoValueProvidedForVariable(String variableName, String variableType, int line , int column) {
        this.potentialError.addError(ParsingErrorMessages.notProvidedVariable(variableName, variableType), line, column);
        this.state.push(State.STRICT_ERROR_SKIP);
    }

    private void handleErrorUnknownType(String currentTypeReference, int line, int column) {
        this.potentialError.addError(ParsingErrorMessages.unknownVariableType(currentVariableName, currentTypeReference), line, column);
        this.state.push(State.STRICT_ERROR_SKIP);
    }

    private void handleErrorSubscriptionsArNotSupported(int line, int column) {
        this.potentialError.addError(ParsingErrorMessages.unsupportedSubscription(), line, column);
        this.state.push(State.STRICT_ERROR_SKIP);
    }

    private void handleErrorUnknownEntryMessage(String name, int column, int line) {
        this.potentialError.addError(ParsingErrorMessages.unkownRootField(currentIsMutation ? endpoint.mutationTypeName() : endpoint.queryTypeName(), name), line, column);
        this.state.push(State.STRICT_ERROR_SKIP);
    }

    private void handleErrorUnknownArgumentIn(String typeName, Name fieldName, String argumentName, int column, int line) {
        this.potentialError.addError(ParsingErrorMessages.unknownParam(typeName, endpoint.displayName(fieldName), argumentName),line, column);
        this.state.push(State.STRICT_ERROR_SKIP);
    }

    private void handleWrongInputForType(String input, String typeName, String label, int line, int column) {
        this.potentialError.addError(ParsingErrorMessages.wrongInputForVariableType(label, input, typeName), line, column);
        this.state.push(State.STRICT_ERROR_SKIP);
    }




    @Override
    public void enterOperationType(no.hvl.past.gqlintegration.syntax.parser.GraphQLQueryParser.OperationTypeContext ctx) {
        switch (ctx.getText()) {
            case "subscription":
                handleErrorSubscriptionsArNotSupported(ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
                // error
                break;
            case "mutation":
                this.currentIsMutation = true;
                break;
            case "query":
            default:
                this.currentIsMutation = false;
                break;
        }
    }


    @Override
    public void enterArgument(no.hvl.past.gqlintegration.syntax.parser.GraphQLQueryParser.ArgumentContext ctx) {
        if (this.state.peek().equals(State.ROOT_FIELD) || this.state.peek().equals(State.FIELD)) {
            this.state.push(State.ARGUMENT);
        }
    }

    @Override
    public void exitArgument(no.hvl.past.gqlintegration.syntax.parser.GraphQLQueryParser.ArgumentContext ctx) {
        if (this.state.peek().equals(State.ARGUMENT)) {
            this.contextTypingStack.pop();
            this.state.pop();
        }
    }



    @Override
    public void enterSelectionSet(no.hvl.past.gqlintegration.syntax.parser.GraphQLQueryParser.SelectionSetContext ctx) {
        if (state.peek().equals(State.OPERATION)) {
            this.state.push(State.ROOT_FIELD);
        } else if (state.peek().equals(State.ROOT_FIELD) || state.peek().equals(State.FIELD)) {
            this.state.push(State.AWAITING_CHILDREN);
        }
    }

    @Override
    public void exitSelectionSet(no.hvl.past.gqlintegration.syntax.parser.GraphQLQueryParser.SelectionSetContext ctx) {
        if (!this.state.peek().equals(State.STRICT_ERROR_SKIP) && !this.state.peek().equals(State.STRICT_DONE_SKIP)) {
            this.state.pop();
        }
        if (this.state.peek().equals(State.ROOT_FIELD)) {
            if (this.currentQueryRoot != null) {
                this.queryRoots.add(this.currentQueryRoot);
                this.currentQueryRoot = null;
            }
        }
    }

    @Override
    public void enterField(no.hvl.past.gqlintegration.syntax.parser.GraphQLQueryParser.FieldContext ctx) {
        if (this.state.peek().equals(State.AWAITING_CHILDREN)) {
            this.state.push(State.FIELD);
        }
    }

    @Override
    public void exitField(no.hvl.past.gqlintegration.syntax.parser.GraphQLQueryParser.FieldContext ctx) {
        if (this.state.peek().equals(State.FIELD)) {
            this.state.pop();
            this.contextTypingStack.pop();
            this.parentNodeStack.pop();
        }
    }
    @Override
    public void enterTypeRef(no.hvl.past.gqlintegration.syntax.parser.GraphQLQueryParser.TypeRefContext ctx) {
        this.currentIsMandatory = false;
        this.currentIsSetValued = false;
        if (ctx.getText().contains("!")) {
            this.currentIsMandatory = true;
        }
        this.state.push(State.TYPE_REF);
    }

    @Override
    public void enterListType(no.hvl.past.gqlintegration.syntax.parser.GraphQLQueryParser.ListTypeContext ctx) {
        this.currentIsSetValued = true;
    }

    @Override
    public void exitTypeRef(no.hvl.past.gqlintegration.syntax.parser.GraphQLQueryParser.TypeRefContext ctx) {
        if (this.state.peek().equals(State.TYPE_REF)) {
            this.state.pop();
        }
    }


    @Override
    public void enterVariable(no.hvl.past.gqlintegration.syntax.parser.GraphQLQueryParser.VariableContext ctx) {
        if (this.state.contains(State.ARGUMENT)) {
            this.state.push(State.VARIABL_REF);
        }
    }

    @Override
    public void exitVariable(no.hvl.past.gqlintegration.syntax.parser.GraphQLQueryParser.VariableContext ctx) {
        if (this.state.peek().equals(State.VARIABL_REF)) {
            this.state.pop();
        }
    }

    @Override
    public void enterStringValue(no.hvl.past.gqlintegration.syntax.parser.GraphQLQueryParser.StringValueContext ctx) {
        String raw = ctx.getText();
        if (raw.startsWith("\"")) {
            raw = raw.substring(1, raw.length() - 1);
        }
        handleValue(raw);

    }

    @Override
    public void enterFloatValue(no.hvl.past.gqlintegration.syntax.parser.GraphQLQueryParser.FloatValueContext ctx) {
        handleValue(ctx.getText());
    }

    @Override
    public void enterIntValue(no.hvl.past.gqlintegration.syntax.parser.GraphQLQueryParser.IntValueContext ctx) {
        handleValue(ctx.getText());
    }

    @Override
    public void enterBooleanValue(no.hvl.past.gqlintegration.syntax.parser.GraphQLQueryParser.BooleanValueContext ctx) {
        handleValue(ctx.getText());
    }

    @Override
    public void enterListValue(no.hvl.past.gqlintegration.syntax.parser.GraphQLQueryParser.ListValueContext ctx) {
        if (!this.state.peek().equals(State.STRICT_ERROR_SKIP) && !this.state.peek().equals(State.STRICT_DONE_SKIP)) {
            this.state.push(State.ARRAY);
        }
    }

    @Override
    public void exitListValue(no.hvl.past.gqlintegration.syntax.parser.GraphQLQueryParser.ListValueContext ctx) {
        if (this.state.peek().equals(State.ARRAY)) {
            this.state.pop();
        }
    }

    @Override
    public void enterObjectValue(no.hvl.past.gqlintegration.syntax.parser.GraphQLQueryParser.ObjectValueContext ctx) {
        if (!this.state.peek().equals(State.STRICT_ERROR_SKIP) && !this.state.peek().equals(State.STRICT_DONE_SKIP)) {
            GraphQLQuery.InputObject tn = new GraphQLQuery.InputObject(currentArgumentName.peek(), contextTypingStack.peek().getTarget());
            if (state.peek().equals(State.ARRAY)) {
                this.parentNodeStack.peek().addComplexArgument(currentArgumentName.peek(),tn, contextTypingStack.peek(), (int) parentNodeStack.peek().childNodesByKey(currentArgumentName.peek()).count());
            } else {
                this.parentNodeStack.peek().addComplexArgument(currentArgumentName.peek(),tn, contextTypingStack.peek());
            }
            parentNodeStack.push(tn);
            this.state.push(State.OBJECT);
        }
    }


    @Override
    public void exitObjectField(no.hvl.past.gqlintegration.syntax.parser.GraphQLQueryParser.ObjectFieldContext ctx) {
        if (!this.state.peek().equals(State.STRICT_ERROR_SKIP) && !this.state.peek().equals(State.STRICT_DONE_SKIP)) {

            this.currentArgumentName.pop();
            this.contextTypingStack.pop();
        }
    }

    @Override
    public void exitObjectValue(no.hvl.past.gqlintegration.syntax.parser.GraphQLQueryParser.ObjectValueContext ctx) {
        if (state.peek().equals(State.OBJECT)) {
            state.pop();
            parentNodeStack.pop();
        }
    }

    private void handleValue(String raw) {
        if (!this.state.peek().equals(State.STRICT_ERROR_SKIP) && !this.state.peek().equals(State.STRICT_DONE_SKIP)) {

            Name type = contextTypingStack.peek().getTarget();
            if (endpoint.isIntType(type)) {
                this.currentValues.add(Name.value(Long.parseLong(raw)));
            } else if (endpoint.isFloatType(type)) {
                this.currentValues.add(Name.value(Double.parseDouble(raw)));
            } else if (endpoint.isStringType(type)) {
                this.currentValues.add(Name.value(raw));
            } else if (endpoint.isBoolType(type)) {
                this.currentValues.add(Boolean.parseBoolean(raw) ? Name.trueValue() : Name.falseValue());
            }
            // TODO GraphQL Parsing 2.0: enum values

            if (state.peek().equals(State.ARRAY)) {
                state.pop();
                if (!state.peek().equals(State.VARIABLE)) {
                    for (Value v : currentValues) {
                        this.parentNodeStack.peek().addSimpleArgument(this.currentArgumentName.peek(), raw, v, contextTypingStack.peek(), (int) this.parentNodeStack.peek().childrenByKey(currentArgumentName.peek()).count());
                    }
                }
            } else {
                if (!state.peek().equals(State.VARIABLE)) {
                    this.parentNodeStack.peek().addSimpleArgument(this.currentArgumentName.peek(), raw, currentValues.get(0), contextTypingStack.peek());
                }
            }
        }
    }



    @Override
    public void enterName(no.hvl.past.gqlintegration.syntax.parser.GraphQLQueryParser.NameContext ctx) {
        if (!this.state.peek().equals(State.STRICT_ERROR_SKIP) && !this.state.peek().equals(State.STRICT_DONE_SKIP)) {
            switch (this.state.peek()) {
                case OPERATION:
                    this.currentOperationName = ctx.getText();
                    break;
                case VARIABLE:
                    this.currentVariableName = ctx.getText();
                    break;
                case TYPE_REF:
                    this.currentTypeReference = ctx.getText();
                    findContextType(ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
                    break;
                case ARGUMENT:
                    handleArgument(ctx.getText(), ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
                    break;
                case VARIABL_REF:
                    handleVariableRef(ctx.getText());
                    break;
                case FIELD:
                    handleField(ctx.getText(), ctx.getStart().getCharPositionInLine(), ctx.getStart().getLine());
                    break;
                case ROOT_FIELD:
                    handleRootField(ctx.getText(), ctx.getStart().getCharPositionInLine(), ctx.getStart().getLine());
                    break;
                case OBJECT:
                    handleObjectField(ctx.getText(), ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
                    break;
            }
        }

    }

    private void handleObjectField(String text, int line, int column) {
        Optional<Triple> triple = endpoint.lookupField(contextTypingStack.peek().getTarget(), text);
        if (!triple.isPresent()) {
            handleErrorChildField(contextTypingStack.peek().getTarget(), text, line, column);
        }
        triple.ifPresent(t -> {
            this.contextTypingStack.push(t);
            currentArgumentName.push(text);
        });
    }

    private void handleField(String field, int column, int line) {
        Optional<Triple> triple = endpoint.lookupField(contextTypingStack.peek().getTarget(), field);
        if (!triple.isPresent()) {
            handleErrorChildField(contextTypingStack.peek().getTarget(), field, line, column);
        }
        triple.ifPresent(t -> {
            this.contextTypingStack.push(t);
            GraphQLQuery.Node n = new GraphQLQuery.Node(field, t.getTarget());
            parentNodeStack.peek().addChild(
                    n,
                    t,
                    !endpoint.isSimpleTypeNode(t.getTarget()),
                    endpoint.isCollectionValued(t));
                    this.parentNodeStack.push(n);
        }
        );

    }

    private void handleErrorChildField(Name typeName, String field, int line, int column) {
        Stack<String> stack = new Stack<>();
        contextTypingStack.forEach(t -> stack.push(endpoint.displayName(t.getLabel())));
        this.potentialError.addError(ParsingErrorMessages.unknownField(endpoint.displayName(typeName), field), line, column, stack);
        this.state.push(State.STRICT_ERROR_SKIP);
    }

    private void handleArgument(String argumentName, int line, int column) {
        currentArgumentName.clear();
        this.currentArgumentName.push(argumentName);
        Name ownerType = contextTypingStack.size() == 1 ? this.contextTypingStack.peek().getSource() : this.contextTypingStack.peek().getLabel();
        Optional<Triple> currentArgumentTyping = endpoint.lookupField(ownerType, currentArgumentName.peek());
        if (!currentArgumentTyping.isPresent()) {
            if (contextTypingStack.size() == 1) {
                handleErrorUnknownArgumentIn(currentIsMutation ? endpoint.mutationTypeName() : endpoint.queryTypeName(), contextTypingStack.peek().getTarget(), currentArgumentName.peek(), column, line);
            } else {
                handleErrorUnknownArgumentIn(endpoint.displayName(contextTypingStack.peek().getSource()), contextTypingStack.peek().getLabel(), currentArgumentName.peek(), column, line);
            }
        }
        currentArgumentTyping.ifPresent(t ->
                contextTypingStack.push(t)
        );
    }

    private void handleVariableRef(String variable) {
            if (!endpoint.isSimpleTypeNode(contextTypingStack.peek().getTarget())) {
                if (translatedComplexVariables.containsKey(variable)) {
                    if (endpoint.isCollectionValued(contextTypingStack.peek())) {
                        for (GraphQLQuery.InputObject o : translatedComplexVariables.get(variable)) {
                            this.parentNodeStack.peek().addComplexArgument(
                                    currentArgumentName.peek(),
                                    o.copyFor(currentArgumentName.peek()),
                                    contextTypingStack.peek(),
                                    (int) parentNodeStack.peek().childrenByKey(currentArgumentName.peek()).count());
                        }
                    } else {
                        this.parentNodeStack.peek().addComplexArgument(
                                currentArgumentName.peek(),
                                translatedComplexVariables.get(variable).iterator().next().copyFor(currentArgumentName.peek()),
                                contextTypingStack.peek());
                    }
                }
            } else {
                if (translatedSimpelVariables.containsKey(variable)) {
                    if (endpoint.isCollectionValued(contextTypingStack.peek())) {
                        for (Value v : this.translatedSimpelVariables.get(variable)) {
                            this.parentNodeStack.peek().addSimpleArgument(currentArgumentName.peek(), v.printRaw(), v, contextTypingStack.peek(), (int) parentNodeStack.peek().childNodesByKey(currentArgumentName.peek()).count());
                        }
                    } else {
                        Value v = translatedSimpelVariables.get(variable).iterator().next();
                        this.parentNodeStack.peek().addSimpleArgument(currentArgumentName.peek(), v.printRaw(), v, contextTypingStack.peek());
                    }

                }
            }
    }

    private void handleRootField(String name, int column, int line) {
        Optional<MessageType> msg;
        if (currentIsMutation) {
            msg = endpoint.getMutationNMassage(name);
        } else {
            msg = endpoint.getQueryMessage(name);
        }
        if (!msg.isPresent()) {
            handleErrorUnknownEntryMessage(name, column, line);
        }
        msg.ifPresent(m -> {
            this.contextTypingStack.push(m.outputs().get(0).asEdge());
            currentQueryRoot = new GraphQLQuery.QueryRoot(name, currentIsMutation, this.contextTypingStack.peek());
            parentNodeStack.push(currentQueryRoot);
        });
    }

}
