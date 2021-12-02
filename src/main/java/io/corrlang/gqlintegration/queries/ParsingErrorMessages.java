package io.corrlang.gqlintegration.queries;

public class ParsingErrorMessages {

    private ParsingErrorMessages() {
    }

    public static String unkownRootField(String queryType, String fieldName) {
        return "Entry '" + fieldName + "' is not a field in the root type '" + queryType + "'!";
    }

    public static String unknownField(String type, String field) {
        return "Input '" + field + "' is not a field in type '" + type  + "'!";
    }

    public static String unknownParam(String type, String field, String argument) {
        return "Input '" + argument + "' is not an argument of field '" + field  + "' in type '" + type + "'!";
    }

    public static String notProvidedVariable(String variableName, String variableType) {
        return "There was no value provided for the required variable '$" + variableName + "' of type '" + variableType + "'!" ;
    }

    public static String unknownVariableType(String variableName, String typeName) {
        return "The type '" + typeName + "' of variable '$" + variableName + "' does not exist!";
    }

    public static String wrongInputForVariableType(String variableName, String input, String type) {
        return "The given input '" + input + "' for variable '$" + variableName +"' is not of type '" + type + "'!";
    }

    public static String unsupportedSubscription() {
        return "Subscription queries are currently not supported! Sorry :-/ ...";
    }

    public static String notRootProvided(String operationName, boolean isMutation) {
        return "The provided " + (isMutation ? " mutation " : " query ") + (operationName == null ? "" : "with name '" + operationName + "'") + "does not contain any root field!";
    }

    public static String noOperationChosen() {
        return "The provided request contains several operation definitions but it was not specified, which operation should be executed! Please use 'operationName' in your request!";
    }

    public static String unknownOperationChosen(String operationName) {
        return "There is no operation definition with name '" + operationName + "'!";
    }
}
