/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.hive.ql.parse;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import org.apache.calcite.rel.RelNode;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.hadoop.hive.common.type.HiveChar;
import org.apache.hadoop.hive.common.type.HiveDecimal;
import org.apache.hadoop.hive.common.type.HiveIntervalDayTime;
import org.apache.hadoop.hive.common.type.HiveIntervalYearMonth;
import org.apache.hadoop.hive.ql.ErrorMsg;
import org.apache.hadoop.hive.ql.exec.ColumnInfo;
import org.apache.hadoop.hive.ql.exec.FunctionInfo;
import org.apache.hadoop.hive.ql.exec.FunctionRegistry;
import org.apache.hadoop.hive.ql.exec.UDFArgumentException;
import org.apache.hadoop.hive.ql.exec.UDFArgumentLengthException;
import org.apache.hadoop.hive.ql.exec.UDFArgumentTypeException;
import org.apache.hadoop.hive.ql.lib.DefaultRuleDispatcher;
import org.apache.hadoop.hive.ql.lib.Dispatcher;
import org.apache.hadoop.hive.ql.lib.GraphWalker;
import org.apache.hadoop.hive.ql.lib.Node;
import org.apache.hadoop.hive.ql.lib.NodeProcessor;
import org.apache.hadoop.hive.ql.lib.NodeProcessorCtx;
import org.apache.hadoop.hive.ql.lib.Rule;
import org.apache.hadoop.hive.ql.lib.RuleRegExp;
import org.apache.hadoop.hive.ql.lib.ExpressionWalker;
import org.apache.hadoop.hive.ql.optimizer.ConstantPropagateProcFactory;
import org.apache.hadoop.hive.ql.optimizer.calcite.CalciteSubquerySemanticException;
import org.apache.hadoop.hive.ql.optimizer.calcite.translator.TypeConverter;
import org.apache.hadoop.hive.ql.plan.ExprNodeColumnDesc;
import org.apache.hadoop.hive.ql.plan.ExprNodeColumnListDesc;
import org.apache.hadoop.hive.ql.plan.ExprNodeConstantDesc;
import org.apache.hadoop.hive.ql.plan.ExprNodeDesc;
import org.apache.hadoop.hive.ql.plan.ExprNodeDescUtils;
import org.apache.hadoop.hive.ql.plan.ExprNodeFieldDesc;
import org.apache.hadoop.hive.ql.plan.ExprNodeGenericFuncDesc;
import org.apache.hadoop.hive.ql.plan.ExprNodeSubQueryDesc;
import org.apache.hadoop.hive.ql.udf.SettableUDF;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDF;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDFBaseCompare;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDFNvl;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDFOPAnd;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDFOPEqual;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDFOPNot;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDFOPOr;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDFWhen;
import org.apache.hadoop.hive.serde.serdeConstants;
import org.apache.hadoop.hive.serde2.objectinspector.ConstantObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector.Category;
import org.apache.hadoop.hive.serde2.objectinspector.PrimitiveObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorUtils;
import org.apache.hadoop.hive.serde2.typeinfo.CharTypeInfo;
import org.apache.hadoop.hive.serde2.typeinfo.DecimalTypeInfo;
import org.apache.hadoop.hive.serde2.typeinfo.ListTypeInfo;
import org.apache.hadoop.hive.serde2.typeinfo.MapTypeInfo;
import org.apache.hadoop.hive.serde2.typeinfo.PrimitiveTypeInfo;
import org.apache.hadoop.hive.serde2.typeinfo.StructTypeInfo;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfo;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfoFactory;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfoUtils;
import org.apache.hadoop.hive.serde2.typeinfo.VarcharTypeInfo;
import org.apache.hadoop.io.NullWritable;
import org.apache.hive.common.util.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;


/**
 * The Factory for creating typecheck processors. The typecheck processors are
 * used to processes the syntax trees for expressions and convert them into
 * expression Node Descriptor trees. They also introduce the correct conversion
 * functions to do proper implicit conversion.
 */
public class TypeCheckProcFactory {

  protected static final Logger LOG = LoggerFactory.getLogger(TypeCheckProcFactory.class
      .getName());

  protected TypeCheckProcFactory() {
    // prevent instantiation
  }

  /**
   * Function to do groupby subexpression elimination. This is called by all the
   * processors initially. As an example, consider the query select a+b,
   * count(1) from T group by a+b; Then a+b is already precomputed in the group
   * by operators key, so we substitute a+b in the select list with the internal
   * column name of the a+b expression that appears in the in input row
   * resolver.
   *
   * @param nd
   *          The node that is being inspected.
   * @param procCtx
   *          The processor context.
   *
   * @return exprNodeColumnDesc.
   */
  public static ExprNodeDesc processGByExpr(Node nd, Object procCtx)
      throws SemanticException {
    // We recursively create the exprNodeDesc. Base cases: when we encounter
    // a column ref, we convert that into an exprNodeColumnDesc; when we
    // encounter
    // a constant, we convert that into an exprNodeConstantDesc. For others we
    // just
    // build the exprNodeFuncDesc with recursively built children.
    ASTNode expr = (ASTNode) nd;
    TypeCheckCtx ctx = (TypeCheckCtx) procCtx;

    // bypass only if outerRR is not null. Otherwise we need to look for expressions in outerRR for
    // subqueries e.g. select min(b.value) from table b group by b.key
    //                                  having key in (select .. where a = min(b.value)
    if (!ctx.isUseCaching() && ctx.getOuterRR() == null) {
      return null;
    }

    RowResolver input = ctx.getInputRR();
    ExprNodeDesc desc = null;

    if ((ctx == null) || (input == null) || (!ctx.getAllowGBExprElimination())) {
      return null;
    }

    // If the current subExpression is pre-calculated, as in Group-By etc.
    ColumnInfo colInfo = input.getExpression(expr);

    // try outer row resolver
    RowResolver outerRR = ctx.getOuterRR();
    if(colInfo == null && outerRR != null) {
        colInfo = outerRR.getExpression(expr);
    }
    if (colInfo != null) {
      desc = new ExprNodeColumnDesc(colInfo);
      ASTNode source = input.getExpressionSource(expr);
      if (source != null) {
        ctx.getUnparseTranslator().addCopyTranslation(expr, source);
      }
      return desc;
    }
    return desc;
  }

  public static Map<ASTNode, ExprNodeDesc> genExprNode(ASTNode expr, TypeCheckCtx tcCtx)
      throws SemanticException {
    return genExprNode(expr, tcCtx, new TypeCheckProcFactory());
  }

  protected static Map<ASTNode, ExprNodeDesc> genExprNode(ASTNode expr,
      TypeCheckCtx tcCtx, TypeCheckProcFactory tf) throws SemanticException {
    // Create the walker, the rules dispatcher and the context.
    // create a walker which walks the tree in a DFS manner while maintaining
    // the operator stack. The dispatcher
    // generates the plan from the operator tree
    Map<Rule, NodeProcessor> opRules = new LinkedHashMap<Rule, NodeProcessor>();

    opRules.put(new RuleRegExp("R1", HiveParser.TOK_NULL + "%"),
        tf.getNullExprProcessor());
    opRules.put(new RuleRegExp("R2", HiveParser.Number + "%|" +
        HiveParser.IntegralLiteral + "%|" +
        HiveParser.NumberLiteral + "%"),
        tf.getNumExprProcessor());
    opRules
        .put(new RuleRegExp("R3", HiveParser.Identifier + "%|"
        + HiveParser.StringLiteral + "%|" + HiveParser.TOK_CHARSETLITERAL + "%|"
        + HiveParser.TOK_STRINGLITERALSEQUENCE + "%|"
        + "%|" + HiveParser.KW_IF + "%|" + HiveParser.KW_CASE + "%|"
        + HiveParser.KW_WHEN + "%|" + HiveParser.KW_IN + "%|"
        + HiveParser.KW_ARRAY + "%|" + HiveParser.KW_MAP + "%|"
        + HiveParser.KW_STRUCT + "%|" + HiveParser.KW_EXISTS + "%|"
        + HiveParser.TOK_SUBQUERY_OP_NOTIN + "%"),
        tf.getStrExprProcessor());
    opRules.put(new RuleRegExp("R4", HiveParser.KW_TRUE + "%|"
        + HiveParser.KW_FALSE + "%"), tf.getBoolExprProcessor());
    opRules.put(new RuleRegExp("R5", HiveParser.TOK_DATELITERAL + "%|"
        + HiveParser.TOK_TIMESTAMPLITERAL + "%"), tf.getDateTimeExprProcessor());
    opRules.put(new RuleRegExp("R6", HiveParser.TOK_INTERVAL_YEAR_MONTH_LITERAL + "%|"
        + HiveParser.TOK_INTERVAL_DAY_TIME_LITERAL + "%|"
        + HiveParser.TOK_INTERVAL_YEAR_LITERAL + "%|"
        + HiveParser.TOK_INTERVAL_MONTH_LITERAL + "%|"
        + HiveParser.TOK_INTERVAL_DAY_LITERAL + "%|"
        + HiveParser.TOK_INTERVAL_HOUR_LITERAL + "%|"
        + HiveParser.TOK_INTERVAL_MINUTE_LITERAL + "%|"
        + HiveParser.TOK_INTERVAL_SECOND_LITERAL + "%"), tf.getIntervalExprProcessor());
    opRules.put(new RuleRegExp("R7", HiveParser.TOK_TABLE_OR_COL + "%"),
        tf.getColumnExprProcessor());
    opRules.put(new RuleRegExp("R8", HiveParser.TOK_SUBQUERY_EXPR + "%"),
        tf.getSubQueryExprProcessor());

    // The dispatcher fires the processor corresponding to the closest matching
    // rule and passes the context along
    Dispatcher disp = new DefaultRuleDispatcher(tf.getDefaultExprProcessor(),
        opRules, tcCtx);
    GraphWalker ogw = new ExpressionWalker(disp);

    // Create a list of top nodes
    ArrayList<Node> topNodes = Lists.<Node>newArrayList(expr);
    HashMap<Node, Object> nodeOutputs = new LinkedHashMap<Node, Object>();
    ogw.startWalking(topNodes, nodeOutputs);

    return convert(nodeOutputs);
  }

  // temporary type-safe casting
  private static Map<ASTNode, ExprNodeDesc> convert(Map<Node, Object> outputs) {
    Map<ASTNode, ExprNodeDesc> converted = new LinkedHashMap<ASTNode, ExprNodeDesc>();
    for (Map.Entry<Node, Object> entry : outputs.entrySet()) {
      if (entry.getKey() instanceof ASTNode &&
          (entry.getValue() == null || entry.getValue() instanceof ExprNodeDesc)) {
        converted.put((ASTNode)entry.getKey(), (ExprNodeDesc)entry.getValue());
      } else {
        LOG.warn("Invalid type entry " + entry);
      }
    }
    return converted;
  }

  /**
   * Processor for processing NULL expression.
   */
  public static class NullExprProcessor implements NodeProcessor {

    @Override
    public Object process(Node nd, Stack<Node> stack, NodeProcessorCtx procCtx,
        Object... nodeOutputs) throws SemanticException {

      TypeCheckCtx ctx = (TypeCheckCtx) procCtx;
      if (ctx.getError() != null) {
        return null;
      }

      ExprNodeDesc desc = TypeCheckProcFactory.processGByExpr(nd, procCtx);
      if (desc != null) {
        return desc;
      }

      return new ExprNodeConstantDesc(TypeInfoFactory.getPrimitiveTypeInfoFromPrimitiveWritable(NullWritable.class), null);
    }

  }

  /**
   * Factory method to get NullExprProcessor.
   *
   * @return NullExprProcessor.
   */
  public NullExprProcessor getNullExprProcessor() {
    return new NullExprProcessor();
  }

  /**
   * Processor for processing numeric constants.
   */
  public static class NumExprProcessor implements NodeProcessor {

    @Override
    public Object process(Node nd, Stack<Node> stack, NodeProcessorCtx procCtx,
        Object... nodeOutputs) throws SemanticException {

      TypeCheckCtx ctx = (TypeCheckCtx) procCtx;
      if (ctx.getError() != null) {
        return null;
      }

      ExprNodeDesc desc = TypeCheckProcFactory.processGByExpr(nd, procCtx);
      if (desc != null) {
        return desc;
      }

      Number v = null;
      ExprNodeConstantDesc decimalNode = null;
      ASTNode expr = (ASTNode) nd;
      // The expression can be any one of Double, Long and Integer. We
      // try to parse the expression in that order to ensure that the
      // most specific type is used for conversion.
      try {
        if (expr.getText().endsWith("L")) {
          // Literal bigint.
          v = Long.valueOf(expr.getText().substring(0, expr.getText().length() - 1));
        } else if (expr.getText().endsWith("S")) {
          // Literal smallint.
          v = Short.valueOf(expr.getText().substring(0, expr.getText().length() - 1));
        } else if (expr.getText().endsWith("Y")) {
          // Literal tinyint.
          v = Byte.valueOf(expr.getText().substring(0, expr.getText().length() - 1));
        } else if (expr.getText().endsWith("BD")) {
          // Literal decimal
          String strVal = expr.getText().substring(0, expr.getText().length() - 2);
          return createDecimal(strVal, false);
        } else if (expr.getText().endsWith("D")) {
          // Literal double.
          v = Double.valueOf(expr.getText().substring(0, expr.getText().length() - 1));
        } else {
          v = Double.valueOf(expr.getText());
          if (expr.getText() != null && !expr.getText().toLowerCase().contains("e")) {
            decimalNode = createDecimal(expr.getText(), true);
            if (decimalNode != null) {
              v = null; // We will use decimal if all else fails.
            }
          }
          v = Long.valueOf(expr.getText());
          v = Integer.valueOf(expr.getText());
        }
      } catch (NumberFormatException e) {
        // do nothing here, we will throw an exception in the following block
      }
      if (v == null && decimalNode == null) {
        throw new SemanticException(ErrorMsg.INVALID_NUMERICAL_CONSTANT.getMsg(expr));
      }
      return v != null ? new ExprNodeConstantDesc(v) : decimalNode;
    }

    public static ExprNodeConstantDesc createDecimal(String strVal, boolean notNull) {
      // Note: the normalize() call with rounding in HiveDecimal will currently reduce the
      //       precision and scale of the value by throwing away trailing zeroes. This may or may
      //       not be desirable for the literals; however, this used to be the default behavior
      //       for explicit decimal literals (e.g. 1.0BD), so we keep this behavior for now.
      HiveDecimal hd = HiveDecimal.create(strVal);
      if (notNull && hd == null) return null;
      int prec = 1;
      int scale = 0;
      if (hd != null) {
        prec = hd.precision();
        scale = hd.scale();
      }
      DecimalTypeInfo typeInfo = TypeInfoFactory.getDecimalTypeInfo(prec, scale);
      return new ExprNodeConstantDesc(typeInfo, hd);
    }

  }

  /**
   * Factory method to get NumExprProcessor.
   *
   * @return NumExprProcessor.
   */
  public NumExprProcessor getNumExprProcessor() {
    return new NumExprProcessor();
  }

  /**
   * Processor for processing string constants.
   */
  public static class StrExprProcessor implements NodeProcessor {

    @Override
    public Object process(Node nd, Stack<Node> stack, NodeProcessorCtx procCtx,
        Object... nodeOutputs) throws SemanticException {

      TypeCheckCtx ctx = (TypeCheckCtx) procCtx;
      if (ctx.getError() != null) {
        return null;
      }

      ExprNodeDesc desc = TypeCheckProcFactory.processGByExpr(nd, procCtx);
      if (desc != null) {
        return desc;
      }

      ASTNode expr = (ASTNode) nd;
      String str = null;

      switch (expr.getToken().getType()) {
      case HiveParser.StringLiteral:
        str = BaseSemanticAnalyzer.unescapeSQLString(expr.getText());
        break;
      case HiveParser.TOK_STRINGLITERALSEQUENCE:
        StringBuilder sb = new StringBuilder();
        for (Node n : expr.getChildren()) {
          sb.append(
              BaseSemanticAnalyzer.unescapeSQLString(((ASTNode)n).getText()));
        }
        str = sb.toString();
        break;
      case HiveParser.TOK_CHARSETLITERAL:
        str = BaseSemanticAnalyzer.charSetString(expr.getChild(0).getText(),
            expr.getChild(1).getText());
        break;
      default:
        // HiveParser.identifier | HiveParse.KW_IF | HiveParse.KW_LEFT |
        // HiveParse.KW_RIGHT
        str = BaseSemanticAnalyzer.unescapeIdentifier(expr.getText().toLowerCase());
        break;
      }
      return new ExprNodeConstantDesc(TypeInfoFactory.stringTypeInfo, str);
    }

  }

  /**
   * Factory method to get StrExprProcessor.
   *
   * @return StrExprProcessor.
   */
  public StrExprProcessor getStrExprProcessor() {
    return new StrExprProcessor();
  }

  /**
   * Processor for boolean constants.
   */
  public static class BoolExprProcessor implements NodeProcessor {

    @Override
    public Object process(Node nd, Stack<Node> stack, NodeProcessorCtx procCtx,
        Object... nodeOutputs) throws SemanticException {

      TypeCheckCtx ctx = (TypeCheckCtx) procCtx;
      if (ctx.getError() != null) {
        return null;
      }

      ExprNodeDesc desc = TypeCheckProcFactory.processGByExpr(nd, procCtx);
      if (desc != null) {
        return desc;
      }

      ASTNode expr = (ASTNode) nd;
      Boolean bool = null;

      switch (expr.getToken().getType()) {
      case HiveParser.KW_TRUE:
        bool = Boolean.TRUE;
        break;
      case HiveParser.KW_FALSE:
        bool = Boolean.FALSE;
        break;
      default:
        assert false;
      }
      return new ExprNodeConstantDesc(TypeInfoFactory.booleanTypeInfo, bool);
    }

  }

  /**
   * Factory method to get BoolExprProcessor.
   *
   * @return BoolExprProcessor.
   */
  public BoolExprProcessor getBoolExprProcessor() {
    return new BoolExprProcessor();
  }

  /**
   * Processor for date constants.
   */
  public static class DateTimeExprProcessor implements NodeProcessor {

    @Override
    public Object process(Node nd, Stack<Node> stack, NodeProcessorCtx procCtx,
        Object... nodeOutputs) throws SemanticException {

      TypeCheckCtx ctx = (TypeCheckCtx) procCtx;
      if (ctx.getError() != null) {
        return null;
      }

      ExprNodeDesc desc = TypeCheckProcFactory.processGByExpr(nd, procCtx);
      if (desc != null) {
        return desc;
      }

      ASTNode expr = (ASTNode) nd;
      String timeString = BaseSemanticAnalyzer.stripQuotes(expr.getText());

      // Get the string value and convert to a Date value.
      try {
        // todo replace below with joda-time, which supports timezone
        if (expr.getType() == HiveParser.TOK_DATELITERAL) {
          PrimitiveTypeInfo typeInfo = TypeInfoFactory.dateTypeInfo;
          return new ExprNodeConstantDesc(typeInfo,
              Date.valueOf(timeString));
        }
        if (expr.getType() == HiveParser.TOK_TIMESTAMPLITERAL) {
          return new ExprNodeConstantDesc(TypeInfoFactory.timestampTypeInfo,
              Timestamp.valueOf(timeString));
        }
        throw new IllegalArgumentException("Invalid time literal type " + expr.getType());
      } catch (Exception err) {
        throw new SemanticException(
            "Unable to convert time literal '" + timeString + "' to time value.", err);
      }
    }
  }

  /**
   * Processor for interval constants.
   */
  public static class IntervalExprProcessor implements NodeProcessor {

    private static final BigDecimal NANOS_PER_SEC_BD = new BigDecimal(DateUtils.NANOS_PER_SEC);
    @Override
    public Object process(Node nd, Stack<Node> stack, NodeProcessorCtx procCtx,
        Object... nodeOutputs) throws SemanticException {

      TypeCheckCtx ctx = (TypeCheckCtx) procCtx;
      if (ctx.getError() != null) {
        return null;
      }

      ExprNodeDesc desc = TypeCheckProcFactory.processGByExpr(nd, procCtx);
      if (desc != null) {
        return desc;
      }

      ASTNode expr = (ASTNode) nd;
      String intervalString = BaseSemanticAnalyzer.stripQuotes(expr.getText());

      // Get the string value and convert to a Interval value.
      try {
        switch (expr.getType()) {
          case HiveParser.TOK_INTERVAL_YEAR_MONTH_LITERAL:
            return new ExprNodeConstantDesc(TypeInfoFactory.intervalYearMonthTypeInfo,
                HiveIntervalYearMonth.valueOf(intervalString));
          case HiveParser.TOK_INTERVAL_DAY_TIME_LITERAL:
            return new ExprNodeConstantDesc(TypeInfoFactory.intervalDayTimeTypeInfo,
                HiveIntervalDayTime.valueOf(intervalString));
          case HiveParser.TOK_INTERVAL_YEAR_LITERAL:
            return new ExprNodeConstantDesc(TypeInfoFactory.intervalYearMonthTypeInfo,
                new HiveIntervalYearMonth(Integer.parseInt(intervalString), 0));
          case HiveParser.TOK_INTERVAL_MONTH_LITERAL:
            return new ExprNodeConstantDesc(TypeInfoFactory.intervalYearMonthTypeInfo,
                new HiveIntervalYearMonth(0, Integer.parseInt(intervalString)));
          case HiveParser.TOK_INTERVAL_DAY_LITERAL:
            return new ExprNodeConstantDesc(TypeInfoFactory.intervalDayTimeTypeInfo,
                new HiveIntervalDayTime(Integer.parseInt(intervalString), 0, 0, 0, 0));
          case HiveParser.TOK_INTERVAL_HOUR_LITERAL:
            return new ExprNodeConstantDesc(TypeInfoFactory.intervalDayTimeTypeInfo,
                new HiveIntervalDayTime(0, Integer.parseInt(intervalString), 0, 0, 0));
          case HiveParser.TOK_INTERVAL_MINUTE_LITERAL:
            return new ExprNodeConstantDesc(TypeInfoFactory.intervalDayTimeTypeInfo,
                new HiveIntervalDayTime(0, 0, Integer.parseInt(intervalString), 0, 0));
          case HiveParser.TOK_INTERVAL_SECOND_LITERAL:
            BigDecimal bd = new BigDecimal(intervalString);
            BigDecimal bdSeconds = new BigDecimal(bd.toBigInteger());
            BigDecimal bdNanos = bd.subtract(bdSeconds);
            return new ExprNodeConstantDesc(TypeInfoFactory.intervalDayTimeTypeInfo,
                new HiveIntervalDayTime(0, 0, 0, bdSeconds.intValueExact(),
                    bdNanos.multiply(NANOS_PER_SEC_BD).intValue()));
          default:
            throw new IllegalArgumentException("Invalid time literal type " + expr.getType());
        }
      } catch (Exception err) {
        throw new SemanticException(
            "Unable to convert interval literal '" + intervalString + "' to interval value.", err);
      }
    }
  }

  /**
   * Factory method to get IntervalExprProcessor.
   *
   * @return IntervalExprProcessor.
   */
  public IntervalExprProcessor getIntervalExprProcessor() {
    return new IntervalExprProcessor();
  }

  /**
   * Factory method to get DateExprProcessor.
   *
   * @return DateExprProcessor.
   */
  public DateTimeExprProcessor getDateTimeExprProcessor() {
    return new DateTimeExprProcessor();
  }

  /**
   * Processor for table columns.
   */
  public static class ColumnExprProcessor implements NodeProcessor {

    @Override
    public Object process(Node nd, Stack<Node> stack, NodeProcessorCtx procCtx,
        Object... nodeOutputs) throws SemanticException {

      TypeCheckCtx ctx = (TypeCheckCtx) procCtx;
      if (ctx.getError() != null) {
        return null;
      }

      ExprNodeDesc desc = TypeCheckProcFactory.processGByExpr(nd, procCtx);
      if (desc != null) {
        return desc;
      }

      ASTNode expr = (ASTNode) nd;
      ASTNode parent = stack.size() > 1 ? (ASTNode) stack.get(stack.size() - 2) : null;
      RowResolver input = ctx.getInputRR();

      if (expr.getType() != HiveParser.TOK_TABLE_OR_COL) {
        ctx.setError(ErrorMsg.INVALID_COLUMN.getMsg(expr), expr);
        return null;
      }

      assert (expr.getChildCount() == 1);
      String tableOrCol = BaseSemanticAnalyzer.unescapeIdentifier(expr
          .getChild(0).getText());

      boolean isTableAlias = input.hasTableAlias(tableOrCol);
      ColumnInfo colInfo = input.get(null, tableOrCol);

      // try outer row resolver
      if(ctx.getOuterRR() != null && colInfo == null && !isTableAlias) {
        RowResolver outerRR = ctx.getOuterRR();
        isTableAlias = outerRR.hasTableAlias(tableOrCol);
        colInfo = outerRR.get(null, tableOrCol);
      }

      if (isTableAlias) {
        if (colInfo != null) {
          if (parent != null && parent.getType() == HiveParser.DOT) {
            // It's a table alias.
            return null;
          }
          // It's a column.
          return toExprNodeDesc(colInfo);
        } else {
          // It's a table alias.
          // We will process that later in DOT.
          return null;
        }
      } else {
        if (colInfo == null) {
          // It's not a column or a table alias.
          if (input.getIsExprResolver()) {
            ASTNode exprNode = expr;
            if (!stack.empty()) {
              ASTNode tmp = (ASTNode) stack.pop();
              if (!stack.empty()) {
                exprNode = (ASTNode) stack.peek();
              }
              stack.push(tmp);
            }
            ctx.setError(ErrorMsg.NON_KEY_EXPR_IN_GROUPBY.getMsg(exprNode), expr);
            return null;
          } else {
            List<String> possibleColumnNames = input.getReferenceableColumnAliases(tableOrCol, -1);
            String reason = String.format("(possible column names are: %s)",
                StringUtils.join(possibleColumnNames, ", "));
            ctx.setError(ErrorMsg.INVALID_TABLE_OR_COLUMN.getMsg(expr.getChild(0), reason),
                expr);
            LOG.debug(ErrorMsg.INVALID_TABLE_OR_COLUMN.toString() + ":"
                + input.toString());
            return null;
          }
        } else {
          // It's a column.
          return toExprNodeDesc(colInfo);
        }
      }

    }

  }

  private static ExprNodeDesc toExprNodeDesc(ColumnInfo colInfo) {
    ObjectInspector inspector = colInfo.getObjectInspector();
    if (inspector instanceof ConstantObjectInspector &&
        inspector instanceof PrimitiveObjectInspector) {
      PrimitiveObjectInspector poi = (PrimitiveObjectInspector) inspector;
      Object constant = ((ConstantObjectInspector) inspector).getWritableConstantValue();
      return new ExprNodeConstantDesc(colInfo.getType(), poi.getPrimitiveJavaObject(constant));
    }
    // non-constant or non-primitive constants
    ExprNodeColumnDesc column = new ExprNodeColumnDesc(colInfo);
    column.setSkewedCol(colInfo.isSkewedCol());
    return column;
  }

  /**
   * Factory method to get ColumnExprProcessor.
   *
   * @return ColumnExprProcessor.
   */
  public ColumnExprProcessor getColumnExprProcessor() {
    return new ColumnExprProcessor();
  }

  /**
   * The default processor for typechecking.
   */
  public static class DefaultExprProcessor implements NodeProcessor {

    static HashMap<Integer, String> specialUnaryOperatorTextHashMap;
    static HashMap<Integer, String> specialFunctionTextHashMap;
    static HashMap<Integer, String> conversionFunctionTextHashMap;
    static HashSet<Integer> windowingTokens;
    static {
      specialUnaryOperatorTextHashMap = new HashMap<Integer, String>();
      specialUnaryOperatorTextHashMap.put(HiveParser.PLUS, "positive");
      specialUnaryOperatorTextHashMap.put(HiveParser.MINUS, "negative");
      specialFunctionTextHashMap = new HashMap<Integer, String>();
      specialFunctionTextHashMap.put(HiveParser.TOK_ISNULL, "isnull");
      specialFunctionTextHashMap.put(HiveParser.TOK_ISNOTNULL, "isnotnull");
      conversionFunctionTextHashMap = new HashMap<Integer, String>();
      conversionFunctionTextHashMap.put(HiveParser.TOK_BOOLEAN,
          serdeConstants.BOOLEAN_TYPE_NAME);
      conversionFunctionTextHashMap.put(HiveParser.TOK_TINYINT,
          serdeConstants.TINYINT_TYPE_NAME);
      conversionFunctionTextHashMap.put(HiveParser.TOK_SMALLINT,
          serdeConstants.SMALLINT_TYPE_NAME);
      conversionFunctionTextHashMap.put(HiveParser.TOK_INT,
          serdeConstants.INT_TYPE_NAME);
      conversionFunctionTextHashMap.put(HiveParser.TOK_BIGINT,
          serdeConstants.BIGINT_TYPE_NAME);
      conversionFunctionTextHashMap.put(HiveParser.TOK_FLOAT,
          serdeConstants.FLOAT_TYPE_NAME);
      conversionFunctionTextHashMap.put(HiveParser.TOK_DOUBLE,
          serdeConstants.DOUBLE_TYPE_NAME);
      conversionFunctionTextHashMap.put(HiveParser.TOK_STRING,
          serdeConstants.STRING_TYPE_NAME);
      conversionFunctionTextHashMap.put(HiveParser.TOK_CHAR,
          serdeConstants.CHAR_TYPE_NAME);
      conversionFunctionTextHashMap.put(HiveParser.TOK_VARCHAR,
          serdeConstants.VARCHAR_TYPE_NAME);
      conversionFunctionTextHashMap.put(HiveParser.TOK_BINARY,
          serdeConstants.BINARY_TYPE_NAME);
      conversionFunctionTextHashMap.put(HiveParser.TOK_DATE,
          serdeConstants.DATE_TYPE_NAME);
      conversionFunctionTextHashMap.put(HiveParser.TOK_TIMESTAMP,
          serdeConstants.TIMESTAMP_TYPE_NAME);
      conversionFunctionTextHashMap.put(HiveParser.TOK_INTERVAL_YEAR_MONTH,
          serdeConstants.INTERVAL_YEAR_MONTH_TYPE_NAME);
      conversionFunctionTextHashMap.put(HiveParser.TOK_INTERVAL_DAY_TIME,
          serdeConstants.INTERVAL_DAY_TIME_TYPE_NAME);
      conversionFunctionTextHashMap.put(HiveParser.TOK_DECIMAL,
          serdeConstants.DECIMAL_TYPE_NAME);

      windowingTokens = new HashSet<Integer>();
      windowingTokens.add(HiveParser.KW_OVER);
      windowingTokens.add(HiveParser.TOK_PARTITIONINGSPEC);
      windowingTokens.add(HiveParser.TOK_DISTRIBUTEBY);
      windowingTokens.add(HiveParser.TOK_SORTBY);
      windowingTokens.add(HiveParser.TOK_CLUSTERBY);
      windowingTokens.add(HiveParser.TOK_WINDOWSPEC);
      windowingTokens.add(HiveParser.TOK_WINDOWRANGE);
      windowingTokens.add(HiveParser.TOK_WINDOWVALUES);
      windowingTokens.add(HiveParser.KW_UNBOUNDED);
      windowingTokens.add(HiveParser.KW_PRECEDING);
      windowingTokens.add(HiveParser.KW_FOLLOWING);
      windowingTokens.add(HiveParser.KW_CURRENT);
      windowingTokens.add(HiveParser.TOK_TABSORTCOLNAMEASC);
      windowingTokens.add(HiveParser.TOK_TABSORTCOLNAMEDESC);
      windowingTokens.add(HiveParser.TOK_NULLS_FIRST);
      windowingTokens.add(HiveParser.TOK_NULLS_LAST);
    }

    protected static boolean isRedundantConversionFunction(ASTNode expr,
        boolean isFunction, ArrayList<ExprNodeDesc> children) {
      if (!isFunction) {
        return false;
      }
      // conversion functions take a single parameter
      if (children.size() != 1) {
        return false;
      }
      String funcText = conversionFunctionTextHashMap.get(((ASTNode) expr
          .getChild(0)).getType());
      // not a conversion function
      if (funcText == null) {
        return false;
      }
      // return true when the child type and the conversion target type is the
      // same
      return ((PrimitiveTypeInfo) children.get(0).getTypeInfo()).getTypeName()
          .equalsIgnoreCase(funcText);
    }

    public static String getFunctionText(ASTNode expr, boolean isFunction) {
      String funcText = null;
      if (!isFunction) {
        // For operator, the function name is the operator text, unless it's in
        // our special dictionary
        if (expr.getChildCount() == 1) {
          funcText = specialUnaryOperatorTextHashMap.get(expr.getType());
        }
        if (funcText == null) {
          funcText = expr.getText();
        }
      } else {
        // For TOK_FUNCTION, the function name is stored in the first child,
        // unless it's in our
        // special dictionary.
        assert (expr.getChildCount() >= 1);
        int funcType = ((ASTNode) expr.getChild(0)).getType();
        funcText = specialFunctionTextHashMap.get(funcType);
        if (funcText == null) {
          funcText = conversionFunctionTextHashMap.get(funcType);
        }
        if (funcText == null) {
          funcText = ((ASTNode) expr.getChild(0)).getText();
        }
      }
      return BaseSemanticAnalyzer.unescapeIdentifier(funcText);
    }

    /**
     * This function create an ExprNodeDesc for a UDF function given the
     * children (arguments). It will insert implicit type conversion functions
     * if necessary.
     *
     * @throws UDFArgumentException
     */
    static ExprNodeDesc getFuncExprNodeDescWithUdfData(String udfName, TypeInfo typeInfo,
        ExprNodeDesc... children) throws UDFArgumentException {

      FunctionInfo fi;
      try {
        fi = FunctionRegistry.getFunctionInfo(udfName);
      } catch (SemanticException e) {
        throw new UDFArgumentException(e);
      }
      if (fi == null) {
        throw new UDFArgumentException(udfName + " not found.");
      }

      GenericUDF genericUDF = fi.getGenericUDF();
      if (genericUDF == null) {
        throw new UDFArgumentException(udfName
            + " is an aggregation function or a table function.");
      }

      // Add udfData to UDF if necessary
      if (typeInfo != null) {
        if (genericUDF instanceof SettableUDF) {
          ((SettableUDF)genericUDF).setTypeInfo(typeInfo);
        }
      }

      List<ExprNodeDesc> childrenList = new ArrayList<ExprNodeDesc>(children.length);

      childrenList.addAll(Arrays.asList(children));
      return ExprNodeGenericFuncDesc.newInstance(genericUDF,
          childrenList);
    }

    public static ExprNodeDesc getFuncExprNodeDesc(String udfName,
        ExprNodeDesc... children) throws UDFArgumentException {
      return getFuncExprNodeDescWithUdfData(udfName, null, children);
    }

    protected void validateUDF(ASTNode expr, boolean isFunction, TypeCheckCtx ctx, FunctionInfo fi,
        List<ExprNodeDesc> children, GenericUDF genericUDF) throws SemanticException {
      // Detect UDTF's in nested SELECT, GROUP BY, etc as they aren't
      // supported
      if (fi.getGenericUDTF() != null) {
        throw new SemanticException(ErrorMsg.UDTF_INVALID_LOCATION.getMsg());
      }
      // UDAF in filter condition, group-by caluse, param of funtion, etc.
      if (fi.getGenericUDAFResolver() != null) {
        if (isFunction) {
          throw new SemanticException(ErrorMsg.UDAF_INVALID_LOCATION.getMsg((ASTNode) expr
              .getChild(0)));
        } else {
          throw new SemanticException(ErrorMsg.UDAF_INVALID_LOCATION.getMsg(expr));
        }
      }
      if (!ctx.getAllowStatefulFunctions() && (genericUDF != null)) {
        if (FunctionRegistry.isStateful(genericUDF)) {
          throw new SemanticException(ErrorMsg.UDF_STATEFUL_INVALID_LOCATION.getMsg());
        }
      }
    }

    protected ExprNodeDesc getXpathOrFuncExprNodeDesc(ASTNode expr,
        boolean isFunction, ArrayList<ExprNodeDesc> children, TypeCheckCtx ctx)
        throws SemanticException, UDFArgumentException {
      // return the child directly if the conversion is redundant.
      if (isRedundantConversionFunction(expr, isFunction, children)) {
        assert (children.size() == 1);
        assert (children.get(0) != null);
        return children.get(0);
      }
      String funcText = getFunctionText(expr, isFunction);
      ExprNodeDesc desc;
      if (funcText.equals(".")) {
        // "." : FIELD Expression

        assert (children.size() == 2);
        // Only allow constant field name for now
        assert (children.get(1) instanceof ExprNodeConstantDesc);
        ExprNodeDesc object = children.get(0);
        ExprNodeConstantDesc fieldName = (ExprNodeConstantDesc) children.get(1);
        assert (fieldName.getValue() instanceof String);

        // Calculate result TypeInfo
        String fieldNameString = (String) fieldName.getValue();
        TypeInfo objectTypeInfo = object.getTypeInfo();

        // Allow accessing a field of list element structs directly from a list
        boolean isList = (object.getTypeInfo().getCategory() == ObjectInspector.Category.LIST);
        if (isList) {
          objectTypeInfo = ((ListTypeInfo) objectTypeInfo).getListElementTypeInfo();
        }
        if (objectTypeInfo.getCategory() != Category.STRUCT) {
          throw new SemanticException(ErrorMsg.INVALID_DOT.getMsg(expr));
        }
        TypeInfo t = ((StructTypeInfo) objectTypeInfo).getStructFieldTypeInfo(fieldNameString);
        if (isList) {
          t = TypeInfoFactory.getListTypeInfo(t);
        }

        desc = new ExprNodeFieldDesc(t, children.get(0), fieldNameString, isList);
      } else if (funcText.equals("[")) {
        // "[]" : LSQUARE/INDEX Expression
        if (!ctx.getallowIndexExpr())
          throw new SemanticException(ErrorMsg.INVALID_FUNCTION.getMsg(expr));

        assert (children.size() == 2);

        // Check whether this is a list or a map
        TypeInfo myt = children.get(0).getTypeInfo();

        if (myt.getCategory() == Category.LIST) {
          // Only allow integer index for now
          if (!TypeInfoUtils.implicitConvertible(children.get(1).getTypeInfo(),
              TypeInfoFactory.intTypeInfo)) {
            throw new SemanticException(SemanticAnalyzer.generateErrorMessage(
                  expr, ErrorMsg.INVALID_ARRAYINDEX_TYPE.getMsg()));
          }

          // Calculate TypeInfo
          TypeInfo t = ((ListTypeInfo) myt).getListElementTypeInfo();
          desc = new ExprNodeGenericFuncDesc(t, FunctionRegistry.getGenericUDFForIndex(), children);
        } else if (myt.getCategory() == Category.MAP) {
          if (!TypeInfoUtils.implicitConvertible(children.get(1).getTypeInfo(),
              ((MapTypeInfo) myt).getMapKeyTypeInfo())) {
            throw new SemanticException(ErrorMsg.INVALID_MAPINDEX_TYPE
                .getMsg(expr));
          }
          // Calculate TypeInfo
          TypeInfo t = ((MapTypeInfo) myt).getMapValueTypeInfo();
          desc = new ExprNodeGenericFuncDesc(t, FunctionRegistry.getGenericUDFForIndex(), children);
        } else {
          throw new SemanticException(ErrorMsg.NON_COLLECTION_TYPE.getMsg(expr, myt.getTypeName()));
        }
      } else {
        // other operators or functions
        FunctionInfo fi = FunctionRegistry.getFunctionInfo(funcText);

        if (fi == null) {
          if (isFunction) {
            throw new SemanticException(ErrorMsg.INVALID_FUNCTION
                .getMsg((ASTNode) expr.getChild(0)));
          } else {
            throw new SemanticException(ErrorMsg.INVALID_FUNCTION.getMsg(expr));
          }
        }

        // getGenericUDF() actually clones the UDF. Just call it once and reuse.
        GenericUDF genericUDF = fi.getGenericUDF();

        if (!fi.isNative()) {
          ctx.getUnparseTranslator().addIdentifierTranslation(
              (ASTNode) expr.getChild(0));
        }

        // Handle type casts that may contain type parameters
        if (isFunction) {
          ASTNode funcNameNode = (ASTNode)expr.getChild(0);
          switch (funcNameNode.getType()) {
            case HiveParser.TOK_CHAR:
              // Add type params
              CharTypeInfo charTypeInfo = ParseUtils.getCharTypeInfo(funcNameNode);
              if (genericUDF != null) {
                ((SettableUDF)genericUDF).setTypeInfo(charTypeInfo);
              }
              break;
            case HiveParser.TOK_VARCHAR:
              VarcharTypeInfo varcharTypeInfo = ParseUtils.getVarcharTypeInfo(funcNameNode);
              if (genericUDF != null) {
                ((SettableUDF)genericUDF).setTypeInfo(varcharTypeInfo);
              }
              break;
            case HiveParser.TOK_DECIMAL:
              DecimalTypeInfo decTypeInfo = ParseUtils.getDecimalTypeTypeInfo(funcNameNode);
              if (genericUDF != null) {
                ((SettableUDF)genericUDF).setTypeInfo(decTypeInfo);
              }
              break;
            default:
              // Do nothing
              break;
          }
        }

        validateUDF(expr, isFunction, ctx, fi, children, genericUDF);

        // Try to infer the type of the constant only if there are two
        // nodes, one of them is column and the other is numeric const
        if (genericUDF instanceof GenericUDFBaseCompare
            && children.size() == 2
            && ((children.get(0) instanceof ExprNodeConstantDesc
                && children.get(1) instanceof ExprNodeColumnDesc)
                || (children.get(0) instanceof ExprNodeColumnDesc
                    && children.get(1) instanceof ExprNodeConstantDesc))) {
          int constIdx =
              children.get(0) instanceof ExprNodeConstantDesc ? 0 : 1;

          String constType = children.get(constIdx).getTypeString().toLowerCase();
          String columnType = children.get(1 - constIdx).getTypeString().toLowerCase();
          final PrimitiveTypeInfo colTypeInfo = TypeInfoFactory.getPrimitiveTypeInfo(columnType);
          // Try to narrow type of constant
          Object constVal = ((ExprNodeConstantDesc) children.get(constIdx)).getValue();
          try {
            if (PrimitiveObjectInspectorUtils.intTypeEntry.equals(colTypeInfo.getPrimitiveTypeEntry()) && (constVal instanceof Number || constVal instanceof String)) {
              children.set(constIdx, new ExprNodeConstantDesc(new Integer(constVal.toString())));
            } else if (PrimitiveObjectInspectorUtils.longTypeEntry.equals(colTypeInfo.getPrimitiveTypeEntry()) && (constVal instanceof Number || constVal instanceof String)) {
              children.set(constIdx, new ExprNodeConstantDesc(new Long(constVal.toString())));
            }else if (PrimitiveObjectInspectorUtils.doubleTypeEntry.equals(colTypeInfo.getPrimitiveTypeEntry()) && (constVal instanceof Number || constVal instanceof String)) {
              children.set(constIdx, new ExprNodeConstantDesc(new Double(constVal.toString())));
            } else if (PrimitiveObjectInspectorUtils.floatTypeEntry.equals(colTypeInfo.getPrimitiveTypeEntry()) && (constVal instanceof Number || constVal instanceof String)) {
              children.set(constIdx, new ExprNodeConstantDesc(new Float(constVal.toString())));
            } else if (PrimitiveObjectInspectorUtils.byteTypeEntry.equals(colTypeInfo.getPrimitiveTypeEntry()) && (constVal instanceof Number || constVal instanceof String)) {
              children.set(constIdx, new ExprNodeConstantDesc(new Byte(constVal.toString())));
            } else if (PrimitiveObjectInspectorUtils.shortTypeEntry.equals(colTypeInfo.getPrimitiveTypeEntry()) && (constVal instanceof Number || constVal instanceof String)) {
              children.set(constIdx, new ExprNodeConstantDesc(new Short(constVal.toString())));
            }
          } catch (NumberFormatException nfe) {
            LOG.trace("Failed to narrow type of constant", nfe);
            if ((genericUDF instanceof GenericUDFOPEqual && !NumberUtils.isNumber(constVal.toString()))) {
              return new ExprNodeConstantDesc(false);
            }
          }

          // if column type is char and constant type is string, then convert the constant to char
          // type with padded spaces.
          if (constType.equalsIgnoreCase(serdeConstants.STRING_TYPE_NAME) &&
              colTypeInfo instanceof CharTypeInfo) {
            final Object originalValue = ((ExprNodeConstantDesc) children.get(constIdx)).getValue();
            final String constValue = originalValue.toString();
            final int length = TypeInfoUtils.getCharacterLengthForType(colTypeInfo);
            final HiveChar newValue = new HiveChar(constValue, length);
            children.set(constIdx, new ExprNodeConstantDesc(colTypeInfo, newValue));
          }
        }
        if (genericUDF instanceof GenericUDFOPOr) {
          // flatten OR
          List<ExprNodeDesc> childrenList = new ArrayList<ExprNodeDesc>(
              children.size());
          for (ExprNodeDesc child : children) {
            if (FunctionRegistry.isOpOr(child)) {
              childrenList.addAll(child.getChildren());
            } else {
              childrenList.add(child);
            }
          }
          desc = ExprNodeGenericFuncDesc.newInstance(genericUDF, funcText,
              childrenList);
        } else if (genericUDF instanceof GenericUDFOPAnd) {
          // flatten AND
          List<ExprNodeDesc> childrenList = new ArrayList<ExprNodeDesc>(
              children.size());
          for (ExprNodeDesc child : children) {
            if (FunctionRegistry.isOpAnd(child)) {
              childrenList.addAll(child.getChildren());
            } else {
              childrenList.add(child);
            }
          }
          desc = ExprNodeGenericFuncDesc.newInstance(genericUDF, funcText,
              childrenList);
        } else if (ctx.isFoldExpr() && canConvertIntoNvl(genericUDF, children)) {
          // Rewrite CASE into NVL
          desc = ExprNodeGenericFuncDesc.newInstance(new GenericUDFNvl(),
                  Lists.newArrayList(children.get(0), new ExprNodeConstantDesc(false)));
          if (Boolean.FALSE.equals(((ExprNodeConstantDesc) children.get(1)).getValue())) {
            desc = ExprNodeGenericFuncDesc.newInstance(new GenericUDFOPNot(),
                    Lists.newArrayList(desc));
          }
        } else {
          desc = ExprNodeGenericFuncDesc.newInstance(genericUDF, funcText,
              children);
        }

        // If the function is deterministic and the children are constants,
        // we try to fold the expression to remove e.g. cast on constant
        if (ctx.isFoldExpr() && desc instanceof ExprNodeGenericFuncDesc &&
                FunctionRegistry.isDeterministic(genericUDF) &&
                ExprNodeDescUtils.isAllConstants(children)) {
          ExprNodeDesc constantExpr = ConstantPropagateProcFactory.foldExpr((ExprNodeGenericFuncDesc)desc);
          if (constantExpr != null) {
            desc = constantExpr;
          }
        }
      }
      // UDFOPPositive is a no-op.
      // However, we still create it, and then remove it here, to make sure we
      // only allow
      // "+" for numeric types.
      if (FunctionRegistry.isOpPositive(desc)) {
        assert (desc.getChildren().size() == 1);
        desc = desc.getChildren().get(0);
      }
      assert (desc != null);
      return desc;
    }

    private boolean canConvertIntoNvl(GenericUDF genericUDF, ArrayList<ExprNodeDesc> children) {
      if (genericUDF instanceof GenericUDFWhen && children.size() == 3 &&
              children.get(1) instanceof ExprNodeConstantDesc &&
              children.get(2) instanceof ExprNodeConstantDesc) {
        ExprNodeConstantDesc constThen = (ExprNodeConstantDesc) children.get(1);
        ExprNodeConstantDesc constElse = (ExprNodeConstantDesc) children.get(2);
        Object thenVal = constThen.getValue();
        Object elseVal = constElse.getValue();
        if (thenVal instanceof Boolean && elseVal instanceof Boolean) {
          return true;
        }
      }
      return false;
    }

    /**
     * Returns true if des is a descendant of ans (ancestor)
     */
    private boolean isDescendant(Node ans, Node des) {
      if (ans.getChildren() == null) {
        return false;
      }
      for (Node c : ans.getChildren()) {
        if (c == des) {
          return true;
        }
        if (isDescendant(c, des)) {
          return true;
        }
      }
      return false;
    }

    protected ExprNodeDesc processQualifiedColRef(TypeCheckCtx ctx, ASTNode expr,
        Object... nodeOutputs) throws SemanticException {
      RowResolver input = ctx.getInputRR();
      String tableAlias = BaseSemanticAnalyzer.unescapeIdentifier(expr.getChild(0).getChild(0)
          .getText());
      // NOTE: tableAlias must be a valid non-ambiguous table alias,
      // because we've checked that in TOK_TABLE_OR_COL's process method.
      String colName;
      if (nodeOutputs[1] instanceof ExprNodeConstantDesc) {
        colName = ((ExprNodeConstantDesc) nodeOutputs[1]).getValue().toString();
      }  else if (nodeOutputs[1] instanceof ExprNodeColumnDesc) {
        colName = ((ExprNodeColumnDesc)nodeOutputs[1]).getColumn();
      } else {
        throw new SemanticException("Unexpected ExprNode : " + nodeOutputs[1]);
      }
      ColumnInfo colInfo = input.get(tableAlias, colName);

      // Try outer Row resolver
      if(colInfo == null && ctx.getOuterRR() != null) {
        RowResolver outerRR = ctx.getOuterRR();
        colInfo = outerRR.get(tableAlias, colName);
      }

      if (colInfo == null) {
        ctx.setError(ErrorMsg.INVALID_COLUMN.getMsg(expr.getChild(1)), expr);
        return null;
      }
      return toExprNodeDesc(colInfo);
    }

    @Override
    public Object process(Node nd, Stack<Node> stack, NodeProcessorCtx procCtx,
        Object... nodeOutputs) throws SemanticException {

      TypeCheckCtx ctx = (TypeCheckCtx) procCtx;

      ExprNodeDesc desc = TypeCheckProcFactory.processGByExpr(nd, procCtx);
      if (desc != null) {
        // Here we know nd represents a group by expression.

        // During the DFS traversal of the AST, a descendant of nd likely set an
        // error because a sub-tree of nd is unlikely to also be a group by
        // expression. For example, in a query such as
        // SELECT *concat(key)* FROM src GROUP BY concat(key), 'key' will be
        // processed before 'concat(key)' and since 'key' is not a group by
        // expression, an error will be set in ctx by ColumnExprProcessor.

        // We can clear the global error when we see that it was set in a
        // descendant node of a group by expression because
        // processGByExpr() returns a ExprNodeDesc that effectively ignores
        // its children. Although the error can be set multiple times by
        // descendant nodes, DFS traversal ensures that the error only needs to
        // be cleared once. Also, for a case like
        // SELECT concat(value, concat(value))... the logic still works as the
        // error is only set with the first 'value'; all node processors quit
        // early if the global error is set.

        if (isDescendant(nd, ctx.getErrorSrcNode())) {
          ctx.setError(null, null);
        }
        return desc;
      }

      if (ctx.getError() != null) {
        return null;
      }

      ASTNode expr = (ASTNode) nd;

      /*
       * A Windowing specification get added as a child to a UDAF invocation to distinguish it
       * from similar UDAFs but on different windows.
       * The UDAF is translated to a WindowFunction invocation in the PTFTranslator.
       * So here we just return null for tokens that appear in a Window Specification.
       * When the traversal reaches up to the UDAF invocation its ExprNodeDesc is build using the
       * ColumnInfo in the InputRR. This is similar to how UDAFs are handled in Select lists.
       * The difference is that there is translation for Window related tokens, so we just
       * return null;
       */
      if (windowingTokens.contains(expr.getType())) {
        if (!ctx.getallowWindowing())
          throw new SemanticException(SemanticAnalyzer.generateErrorMessage(expr,
              ErrorMsg.INVALID_FUNCTION.getMsg("Windowing is not supported in the context")));

        return null;
      }

      if(expr.getType() == HiveParser.TOK_SUBQUERY_OP || expr.getType() == HiveParser.TOK_QUERY) {
        return null;
      }

      if (expr.getType() == HiveParser.TOK_TABNAME) {
        return null;
      }

      if (expr.getType() == HiveParser.TOK_ALLCOLREF) {
        if (!ctx.getallowAllColRef())
          throw new SemanticException(SemanticAnalyzer.generateErrorMessage(expr,
              ErrorMsg.INVALID_COLUMN
                  .getMsg("All column reference is not supported in the context")));

        RowResolver input = ctx.getInputRR();
        ExprNodeColumnListDesc columnList = new ExprNodeColumnListDesc();
        assert expr.getChildCount() <= 1;
        if (expr.getChildCount() == 1) {
          // table aliased (select a.*, for example)
          ASTNode child = (ASTNode) expr.getChild(0);
          assert child.getType() == HiveParser.TOK_TABNAME;
          assert child.getChildCount() == 1;
          String tableAlias = BaseSemanticAnalyzer.unescapeIdentifier(child.getChild(0).getText());
          HashMap<String, ColumnInfo> columns = input.getFieldMap(tableAlias);
          if (columns == null) {
            throw new SemanticException(ErrorMsg.INVALID_TABLE_ALIAS.getMsg(child));
          }
          for (Map.Entry<String, ColumnInfo> colMap : columns.entrySet()) {
            ColumnInfo colInfo = colMap.getValue();
            if (!colInfo.getIsVirtualCol()) {
              columnList.addColumn(toExprNodeDesc(colInfo));
            }
          }
        } else {
          // all columns (select *, for example)
          for (ColumnInfo colInfo : input.getColumnInfos()) {
            if (!colInfo.getIsVirtualCol()) {
              columnList.addColumn(toExprNodeDesc(colInfo));
            }
          }
        }
        return columnList;
      }

      // If the first child is a TOK_TABLE_OR_COL, and nodeOutput[0] is NULL,
      // and the operator is a DOT, then it's a table column reference.
      if (expr.getType() == HiveParser.DOT
          && expr.getChild(0).getType() == HiveParser.TOK_TABLE_OR_COL
          && nodeOutputs[0] == null) {
        return processQualifiedColRef(ctx, expr, nodeOutputs);
      }

      // Return nulls for conversion operators
      if (conversionFunctionTextHashMap.keySet().contains(expr.getType())
          || specialFunctionTextHashMap.keySet().contains(expr.getType())
          || expr.getToken().getType() == HiveParser.CharSetName
          || expr.getToken().getType() == HiveParser.CharSetLiteral) {
        return null;
      }

      boolean isFunction = (expr.getType() == HiveParser.TOK_FUNCTION ||
          expr.getType() == HiveParser.TOK_FUNCTIONSTAR ||
          expr.getType() == HiveParser.TOK_FUNCTIONDI);

      if (!ctx.getAllowDistinctFunctions() && expr.getType() == HiveParser.TOK_FUNCTIONDI) {
        throw new SemanticException(
            SemanticAnalyzer.generateErrorMessage(expr, ErrorMsg.DISTINCT_NOT_SUPPORTED.getMsg()));
      }

      // Create all children
      int childrenBegin = (isFunction ? 1 : 0);
      ArrayList<ExprNodeDesc> children = new ArrayList<ExprNodeDesc>(
          expr.getChildCount() - childrenBegin);
      for (int ci = childrenBegin; ci < expr.getChildCount(); ci++) {
        if (nodeOutputs[ci] instanceof ExprNodeColumnListDesc) {
          children.addAll(((ExprNodeColumnListDesc) nodeOutputs[ci]).getChildren());
        } else {
          children.add((ExprNodeDesc) nodeOutputs[ci]);
        }
      }

      if (expr.getType() == HiveParser.TOK_FUNCTIONSTAR) {
        if (!ctx.getallowFunctionStar())
        throw new SemanticException(SemanticAnalyzer.generateErrorMessage(expr,
            ErrorMsg.INVALID_COLUMN
                .getMsg(".* reference is not supported in the context")));

        RowResolver input = ctx.getInputRR();
        for (ColumnInfo colInfo : input.getColumnInfos()) {
          if (!colInfo.getIsVirtualCol()) {
            children.add(toExprNodeDesc(colInfo));
          }
        }
      }

      // If any of the children contains null, then return a null
      // this is a hack for now to handle the group by case
      if (children.contains(null)) {
        List<String> possibleColumnNames = getReferenceableColumnAliases(ctx);
        String reason = String.format("(possible column names are: %s)",
            StringUtils.join(possibleColumnNames, ", "));
        ctx.setError(ErrorMsg.INVALID_COLUMN.getMsg(expr.getChild(0), reason),
            expr);
        return null;
      }

      // Create function desc
      try {
        return getXpathOrFuncExprNodeDesc(expr, isFunction, children, ctx);
      } catch (UDFArgumentTypeException e) {
        throw new SemanticException(ErrorMsg.INVALID_ARGUMENT_TYPE.getMsg(expr
            .getChild(childrenBegin + e.getArgumentId()), e.getMessage()), e);
      } catch (UDFArgumentLengthException e) {
        throw new SemanticException(ErrorMsg.INVALID_ARGUMENT_LENGTH.getMsg(
            expr, e.getMessage()), e);
      } catch (UDFArgumentException e) {
        throw new SemanticException(ErrorMsg.INVALID_ARGUMENT.getMsg(expr, e
            .getMessage()), e);
      }
    }

    protected List<String> getReferenceableColumnAliases(TypeCheckCtx ctx) {
      return ctx.getInputRR().getReferenceableColumnAliases(null, -1);
    }
  }

  /**
   * Factory method to get DefaultExprProcessor.
   *
   * @return DefaultExprProcessor.
   */
  public DefaultExprProcessor getDefaultExprProcessor() {
    return new DefaultExprProcessor();
  }

  /**
   * Processor for subquery expressions..
   */
  public static class SubQueryExprProcessor implements NodeProcessor {

    @Override
    public Object process(Node nd, Stack<Node> stack, NodeProcessorCtx procCtx,
        Object... nodeOutputs) throws SemanticException {

      TypeCheckCtx ctx = (TypeCheckCtx) procCtx;
      if (ctx.getError() != null) {
        return null;
      }

      ASTNode expr = (ASTNode) nd;
      ASTNode sqNode = (ASTNode) expr.getParent().getChild(1);

      if (!ctx.getallowSubQueryExpr())
        throw new CalciteSubquerySemanticException(SemanticAnalyzer.generateErrorMessage(sqNode,
            ErrorMsg.UNSUPPORTED_SUBQUERY_EXPRESSION.getMsg("Currently SubQuery expressions are only allowed as " +
                    "Where and Having Clause predicates")));

      ExprNodeDesc desc = TypeCheckProcFactory.processGByExpr(nd, procCtx);
      if (desc != null) {
        return desc;
      }

      //TOK_SUBQUERY_EXPR should have either 2 or 3 children
      assert(expr.getChildren().size() == 3 || expr.getChildren().size() == 2);
      //First child should be operand
      assert(expr.getChild(0).getType() == HiveParser.TOK_SUBQUERY_OP);

      ASTNode subqueryOp = (ASTNode) expr.getChild(0);

      boolean isIN = (subqueryOp.getChildCount() > 0) && (subqueryOp.getChild(0).getType() == HiveParser.KW_IN
              || subqueryOp.getChild(0).getType() == HiveParser.TOK_SUBQUERY_OP_NOTIN);
      boolean isEXISTS = (subqueryOp.getChildCount() > 0) && (subqueryOp.getChild(0).getType() == HiveParser.KW_EXISTS
              || subqueryOp.getChild(0).getType() == HiveParser.TOK_SUBQUERY_OP_NOTEXISTS);
      boolean isScalar = subqueryOp.getChildCount() == 0 ;

      // subqueryToRelNode might be null if subquery expression anywhere other than
      //  as expected in filter (where/having). We should throw an appropriate error
      // message

      Map<ASTNode, RelNode> subqueryToRelNode = ctx.getSubqueryToRelNode();
      if(subqueryToRelNode == null) {
        throw new CalciteSubquerySemanticException(ErrorMsg.UNSUPPORTED_SUBQUERY_EXPRESSION.getMsg(
                        " Currently SubQuery expressions are only allowed as " +
                                "Where and Having Clause predicates"));
      }

      RelNode subqueryRel = subqueryToRelNode.get(expr);

      //For now because subquery is only supported in filter
      // we will create subquery expression of boolean type
      if(isEXISTS) {
        return new ExprNodeSubQueryDesc(TypeInfoFactory.booleanTypeInfo, subqueryRel,
                ExprNodeSubQueryDesc.SubqueryType.EXISTS);
      }
      else if(isIN) {
        assert(nodeOutputs[2] != null);
        ExprNodeDesc lhs = (ExprNodeDesc)nodeOutputs[2];
        return new ExprNodeSubQueryDesc(TypeInfoFactory.booleanTypeInfo, subqueryRel,
                ExprNodeSubQueryDesc.SubqueryType.IN, lhs);
      }
      else if(isScalar){
        // only single subquery expr is supported
        if(subqueryRel.getRowType().getFieldCount() != 1) {
            throw new CalciteSubquerySemanticException(ErrorMsg.INVALID_SUBQUERY_EXPRESSION.getMsg(
                    "More than one column expression in subquery"));
        }
        // figure out subquery expression column's type
        TypeInfo subExprType = TypeConverter.convert(subqueryRel.getRowType().getFieldList().get(0).getType());
        return new ExprNodeSubQueryDesc(subExprType, subqueryRel,
                ExprNodeSubQueryDesc.SubqueryType.SCALAR);
      }

      /*
       * Restriction.1.h :: SubQueries only supported in the SQL Where Clause.
       */
      ctx.setError(ErrorMsg.UNSUPPORTED_SUBQUERY_EXPRESSION.getMsg(sqNode,
              "Currently only IN & EXISTS SubQuery expressions are allowed"),
              sqNode);
      return null;
    }
  }

  /**
   * Factory method to get SubQueryExprProcessor.
   *
   * @return DateExprProcessor.
   */
  public SubQueryExprProcessor getSubQueryExprProcessor() {
    return new SubQueryExprProcessor();
  }
}
