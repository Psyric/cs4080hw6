package lox;

class AstPrinter implements Expr.Visitor<String> {
  String print(Expr expr) {
    if (expr == null) return "";
    return expr.accept(this);
  }

  @Override
  public String visitCommaExpr(Expr.Comma expr) {
    return parenthesize("comma", expr.left, expr.right);
  }

  @Override
  public String visitTernaryExpr(Expr.Ternary expr) {
    return parenthesize("?:", expr.condition, expr.thenBranch, expr.elseBranch);
  }

  @Override
  public String visitBinaryExpr(Expr.Binary expr) {
    return parenthesize(expr.operator.lexeme, expr.left, expr.right);
  }

  @Override
  public String visitLogicalExpr(Expr.Logical expr) {
    return parenthesize(expr.operator.lexeme, expr.left, expr.right);
  }

  @Override
  public String visitGroupingExpr(Expr.Grouping expr) {
    return parenthesize("group", expr.expression);
  }

  @Override
  public String visitLiteralExpr(Expr.Literal expr) {
    if (expr.value == null) return "nil";
    return expr.value.toString();
  }

  @Override
  public String visitUnaryExpr(Expr.Unary expr) {
    return parenthesize(expr.operator.lexeme, expr.right);
  }

  @Override
  public String visitVariableExpr(Expr.Variable expr) {
    return expr.name.lexeme;
  }

  @Override
  public String visitAssignExpr(Expr.Assign expr) {
    return parenthesize("=", new Expr.Variable(expr.name), expr.value);
  }

  @Override
  public String visitCallExpr(Expr.Call expr) {
    return parenthesize("call", expr.callee);
  }

  @Override
  public String visitFunctionExpr(Expr.Function expr) {
    return "<fn>";
  }

  @Override
  public String visitGetExpr(Expr.Get expr) {
    return parenthesize(".", expr.object);
  }

  @Override
  public String visitSetExpr(Expr.Set expr) {
    return parenthesize("set " + expr.name.lexeme, expr.object, expr.value);
  }

  @Override
  public String visitThisExpr(Expr.This expr) {
    return "this";
  }

  private String parenthesize(String name, Expr... exprs) {
    StringBuilder builder = new StringBuilder();

    builder.append("(").append(name);
    for (Expr expr : exprs) {
      builder.append(" ");
      builder.append(expr.accept(this));
    }
    builder.append(")");

    return builder.toString();
  }
}