// Generated from GraphQLQuery.g4 by ANTLR 4.8
package no.hvl.past.gqlintegration.syntax.parser;
import org.antlr.v4.runtime.tree.ParseTreeListener;

/**
 * This interface defines a complete listener for a parse tree produced by
 * {@link GraphQLQueryParser}.
 */
public interface GraphQLQueryListener extends ParseTreeListener {
	/**
	 * Enter a parse tree produced by {@link GraphQLQueryParser#document}.
	 * @param ctx the parse tree
	 */
	void enterDocument(GraphQLQueryParser.DocumentContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphQLQueryParser#document}.
	 * @param ctx the parse tree
	 */
	void exitDocument(GraphQLQueryParser.DocumentContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphQLQueryParser#executableDefinition}.
	 * @param ctx the parse tree
	 */
	void enterExecutableDefinition(GraphQLQueryParser.ExecutableDefinitionContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphQLQueryParser#executableDefinition}.
	 * @param ctx the parse tree
	 */
	void exitExecutableDefinition(GraphQLQueryParser.ExecutableDefinitionContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphQLQueryParser#operationDefinition}.
	 * @param ctx the parse tree
	 */
	void enterOperationDefinition(GraphQLQueryParser.OperationDefinitionContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphQLQueryParser#operationDefinition}.
	 * @param ctx the parse tree
	 */
	void exitOperationDefinition(GraphQLQueryParser.OperationDefinitionContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphQLQueryParser#operationType}.
	 * @param ctx the parse tree
	 */
	void enterOperationType(GraphQLQueryParser.OperationTypeContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphQLQueryParser#operationType}.
	 * @param ctx the parse tree
	 */
	void exitOperationType(GraphQLQueryParser.OperationTypeContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphQLQueryParser#selectionSet}.
	 * @param ctx the parse tree
	 */
	void enterSelectionSet(GraphQLQueryParser.SelectionSetContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphQLQueryParser#selectionSet}.
	 * @param ctx the parse tree
	 */
	void exitSelectionSet(GraphQLQueryParser.SelectionSetContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphQLQueryParser#selection}.
	 * @param ctx the parse tree
	 */
	void enterSelection(GraphQLQueryParser.SelectionContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphQLQueryParser#selection}.
	 * @param ctx the parse tree
	 */
	void exitSelection(GraphQLQueryParser.SelectionContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphQLQueryParser#field}.
	 * @param ctx the parse tree
	 */
	void enterField(GraphQLQueryParser.FieldContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphQLQueryParser#field}.
	 * @param ctx the parse tree
	 */
	void exitField(GraphQLQueryParser.FieldContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphQLQueryParser#arguments}.
	 * @param ctx the parse tree
	 */
	void enterArguments(GraphQLQueryParser.ArgumentsContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphQLQueryParser#arguments}.
	 * @param ctx the parse tree
	 */
	void exitArguments(GraphQLQueryParser.ArgumentsContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphQLQueryParser#argument}.
	 * @param ctx the parse tree
	 */
	void enterArgument(GraphQLQueryParser.ArgumentContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphQLQueryParser#argument}.
	 * @param ctx the parse tree
	 */
	void exitArgument(GraphQLQueryParser.ArgumentContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphQLQueryParser#alias}.
	 * @param ctx the parse tree
	 */
	void enterAlias(GraphQLQueryParser.AliasContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphQLQueryParser#alias}.
	 * @param ctx the parse tree
	 */
	void exitAlias(GraphQLQueryParser.AliasContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphQLQueryParser#fragmentSpread}.
	 * @param ctx the parse tree
	 */
	void enterFragmentSpread(GraphQLQueryParser.FragmentSpreadContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphQLQueryParser#fragmentSpread}.
	 * @param ctx the parse tree
	 */
	void exitFragmentSpread(GraphQLQueryParser.FragmentSpreadContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphQLQueryParser#fragmentDefinition}.
	 * @param ctx the parse tree
	 */
	void enterFragmentDefinition(GraphQLQueryParser.FragmentDefinitionContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphQLQueryParser#fragmentDefinition}.
	 * @param ctx the parse tree
	 */
	void exitFragmentDefinition(GraphQLQueryParser.FragmentDefinitionContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphQLQueryParser#fragmentName}.
	 * @param ctx the parse tree
	 */
	void enterFragmentName(GraphQLQueryParser.FragmentNameContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphQLQueryParser#fragmentName}.
	 * @param ctx the parse tree
	 */
	void exitFragmentName(GraphQLQueryParser.FragmentNameContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphQLQueryParser#typeCondition}.
	 * @param ctx the parse tree
	 */
	void enterTypeCondition(GraphQLQueryParser.TypeConditionContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphQLQueryParser#typeCondition}.
	 * @param ctx the parse tree
	 */
	void exitTypeCondition(GraphQLQueryParser.TypeConditionContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphQLQueryParser#inlineFragment}.
	 * @param ctx the parse tree
	 */
	void enterInlineFragment(GraphQLQueryParser.InlineFragmentContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphQLQueryParser#inlineFragment}.
	 * @param ctx the parse tree
	 */
	void exitInlineFragment(GraphQLQueryParser.InlineFragmentContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphQLQueryParser#value}.
	 * @param ctx the parse tree
	 */
	void enterValue(GraphQLQueryParser.ValueContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphQLQueryParser#value}.
	 * @param ctx the parse tree
	 */
	void exitValue(GraphQLQueryParser.ValueContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphQLQueryParser#intValue}.
	 * @param ctx the parse tree
	 */
	void enterIntValue(GraphQLQueryParser.IntValueContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphQLQueryParser#intValue}.
	 * @param ctx the parse tree
	 */
	void exitIntValue(GraphQLQueryParser.IntValueContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphQLQueryParser#floatValue}.
	 * @param ctx the parse tree
	 */
	void enterFloatValue(GraphQLQueryParser.FloatValueContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphQLQueryParser#floatValue}.
	 * @param ctx the parse tree
	 */
	void exitFloatValue(GraphQLQueryParser.FloatValueContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphQLQueryParser#booleanValue}.
	 * @param ctx the parse tree
	 */
	void enterBooleanValue(GraphQLQueryParser.BooleanValueContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphQLQueryParser#booleanValue}.
	 * @param ctx the parse tree
	 */
	void exitBooleanValue(GraphQLQueryParser.BooleanValueContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphQLQueryParser#stringValue}.
	 * @param ctx the parse tree
	 */
	void enterStringValue(GraphQLQueryParser.StringValueContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphQLQueryParser#stringValue}.
	 * @param ctx the parse tree
	 */
	void exitStringValue(GraphQLQueryParser.StringValueContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphQLQueryParser#nullValue}.
	 * @param ctx the parse tree
	 */
	void enterNullValue(GraphQLQueryParser.NullValueContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphQLQueryParser#nullValue}.
	 * @param ctx the parse tree
	 */
	void exitNullValue(GraphQLQueryParser.NullValueContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphQLQueryParser#enumValue}.
	 * @param ctx the parse tree
	 */
	void enterEnumValue(GraphQLQueryParser.EnumValueContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphQLQueryParser#enumValue}.
	 * @param ctx the parse tree
	 */
	void exitEnumValue(GraphQLQueryParser.EnumValueContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphQLQueryParser#listValue}.
	 * @param ctx the parse tree
	 */
	void enterListValue(GraphQLQueryParser.ListValueContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphQLQueryParser#listValue}.
	 * @param ctx the parse tree
	 */
	void exitListValue(GraphQLQueryParser.ListValueContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphQLQueryParser#objectValue}.
	 * @param ctx the parse tree
	 */
	void enterObjectValue(GraphQLQueryParser.ObjectValueContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphQLQueryParser#objectValue}.
	 * @param ctx the parse tree
	 */
	void exitObjectValue(GraphQLQueryParser.ObjectValueContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphQLQueryParser#objectField}.
	 * @param ctx the parse tree
	 */
	void enterObjectField(GraphQLQueryParser.ObjectFieldContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphQLQueryParser#objectField}.
	 * @param ctx the parse tree
	 */
	void exitObjectField(GraphQLQueryParser.ObjectFieldContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphQLQueryParser#variable}.
	 * @param ctx the parse tree
	 */
	void enterVariable(GraphQLQueryParser.VariableContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphQLQueryParser#variable}.
	 * @param ctx the parse tree
	 */
	void exitVariable(GraphQLQueryParser.VariableContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphQLQueryParser#variableDefinitions}.
	 * @param ctx the parse tree
	 */
	void enterVariableDefinitions(GraphQLQueryParser.VariableDefinitionsContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphQLQueryParser#variableDefinitions}.
	 * @param ctx the parse tree
	 */
	void exitVariableDefinitions(GraphQLQueryParser.VariableDefinitionsContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphQLQueryParser#variableDefinition}.
	 * @param ctx the parse tree
	 */
	void enterVariableDefinition(GraphQLQueryParser.VariableDefinitionContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphQLQueryParser#variableDefinition}.
	 * @param ctx the parse tree
	 */
	void exitVariableDefinition(GraphQLQueryParser.VariableDefinitionContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphQLQueryParser#defaultValue}.
	 * @param ctx the parse tree
	 */
	void enterDefaultValue(GraphQLQueryParser.DefaultValueContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphQLQueryParser#defaultValue}.
	 * @param ctx the parse tree
	 */
	void exitDefaultValue(GraphQLQueryParser.DefaultValueContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphQLQueryParser#typeRef}.
	 * @param ctx the parse tree
	 */
	void enterTypeRef(GraphQLQueryParser.TypeRefContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphQLQueryParser#typeRef}.
	 * @param ctx the parse tree
	 */
	void exitTypeRef(GraphQLQueryParser.TypeRefContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphQLQueryParser#namedType}.
	 * @param ctx the parse tree
	 */
	void enterNamedType(GraphQLQueryParser.NamedTypeContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphQLQueryParser#namedType}.
	 * @param ctx the parse tree
	 */
	void exitNamedType(GraphQLQueryParser.NamedTypeContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphQLQueryParser#listType}.
	 * @param ctx the parse tree
	 */
	void enterListType(GraphQLQueryParser.ListTypeContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphQLQueryParser#listType}.
	 * @param ctx the parse tree
	 */
	void exitListType(GraphQLQueryParser.ListTypeContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphQLQueryParser#directives}.
	 * @param ctx the parse tree
	 */
	void enterDirectives(GraphQLQueryParser.DirectivesContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphQLQueryParser#directives}.
	 * @param ctx the parse tree
	 */
	void exitDirectives(GraphQLQueryParser.DirectivesContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphQLQueryParser#directive}.
	 * @param ctx the parse tree
	 */
	void enterDirective(GraphQLQueryParser.DirectiveContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphQLQueryParser#directive}.
	 * @param ctx the parse tree
	 */
	void exitDirective(GraphQLQueryParser.DirectiveContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphQLQueryParser#name}.
	 * @param ctx the parse tree
	 */
	void enterName(GraphQLQueryParser.NameContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphQLQueryParser#name}.
	 * @param ctx the parse tree
	 */
	void exitName(GraphQLQueryParser.NameContext ctx);
}