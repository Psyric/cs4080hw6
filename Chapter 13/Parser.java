package lox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class Parser {
  private static class ParseError extends RuntimeException {}

  private final List<Token> tokens;
  private int current = 0;
  private int loopDepth = 0;
  private int functionDepth = 0;

  Parser(List<Token> tokens) {
    this.tokens = tokens;
  }

  List<Stmt> parse() {
    List<Stmt> statements = new ArrayList<>();
    while (!isAtEnd()) {
      statements.add(declaration());
    }
    return statements;
  }

  private Stmt declaration() {
    try {
      if (match(TokenType.CLASS)) return classDeclaration();
      if (check(TokenType.FUN) && checkNext(TokenType.IDENTIFIER)) {
        advance();
        return functionDeclaration();
      }
      if (match(TokenType.VAR)) return varDeclaration();
      return statement();
    } catch (ParseError error) {
      synchronize();
      return null;
    }
  }

  private Stmt classDeclaration() {
    Token name = consume(TokenType.IDENTIFIER, "Expect class name.");
    Expr.Variable superclass = null;
    if (match(TokenType.LESS)) {
      Token superclassName = consume(TokenType.IDENTIFIER, "Expect superclass name.");
      superclass = new Expr.Variable(superclassName);
    }
    consume(TokenType.LEFT_BRACE, "Expect '{' before class body.");

    List<Stmt.Function> methods = new ArrayList<>();
    while (!check(TokenType.RIGHT_BRACE) && !isAtEnd()) {
      boolean isStatic = match(TokenType.CLASS);
      methods.add(methodDeclaration(isStatic));
    }
    consume(TokenType.RIGHT_BRACE, "Expect '}' after class body.");
    return new Stmt.Class(name, superclass, methods);
  }

  private Stmt.Function methodDeclaration(boolean isStatic) {
    Token name = consume(TokenType.IDENTIFIER, "Expect method name.");
    List<Token> parameters = new ArrayList<>();
    boolean isGetter = !check(TokenType.LEFT_PAREN);
    if (!isGetter) {
      advance();
      parameters = parameters();
    }
    consume(TokenType.LEFT_BRACE, "Expect '{' before method body.");
    functionDepth++;
    int enclosingLoopDepth = loopDepth;
    loopDepth = 0;
    List<Stmt> body;
    try {
      body = block();
    } finally {
      loopDepth = enclosingLoopDepth;
      functionDepth--;
    }
    return new Stmt.Function(name, parameters, body, isStatic, isGetter);
  }

  private Stmt varDeclaration() {
    Token name = consume(TokenType.IDENTIFIER, "Expect variable name.");

    Expr initializer = null;
    if (match(TokenType.EQUAL)) {
      initializer = expression();
    }

    consume(TokenType.SEMICOLON, "Expect ';' after variable declaration.");
    return new Stmt.Var(name, initializer);
  }

  private Stmt statement() {
    if (match(TokenType.PRINT)) return printStatement();
    if (match(TokenType.RETURN)) return returnStatement();
    if (match(TokenType.IF)) return ifStatement();
    if (match(TokenType.WHILE)) return whileStatement();
    if (match(TokenType.FOR)) return forStatement();
    if (match(TokenType.BREAK)) return breakStatement();
    if (match(TokenType.CONTINUE)) return continueStatement();
    if (match(TokenType.LEFT_BRACE)) return new Stmt.Block(block());
    return expressionStatement();
  }

  private Stmt.Function functionDeclaration() {
    Token name = consume(TokenType.IDENTIFIER, "Expect function name.");
    consume(TokenType.LEFT_PAREN, "Expect '(' after function name.");
    List<Token> parameters = parameters();
    consume(TokenType.LEFT_BRACE, "Expect '{' before function body.");
    functionDepth++;
    int enclosingLoopDepth = loopDepth;
    loopDepth = 0;
    List<Stmt> body;
    try {
      body = block();
    } finally {
      loopDepth = enclosingLoopDepth;
      functionDepth--;
    }
    return new Stmt.Function(name, parameters, body);
  }

  private List<Token> parameters() {
    List<Token> parameters = new ArrayList<>();
    if (!check(TokenType.RIGHT_PAREN)) {
      do {
        if (parameters.size() >= 255) {
          error(peek(), "Can't have more than 255 parameters.");
        }
        parameters.add(consume(TokenType.IDENTIFIER, "Expect parameter name."));
      } while (match(TokenType.COMMA));
    }
    consume(TokenType.RIGHT_PAREN, "Expect ')' after parameters.");
    return parameters;
  }

  private Stmt returnStatement() {
    Token keyword = previous();
    if (functionDepth == 0) {
      throw error(keyword, "Can't return from top-level code.");
    }
    Expr value = null;
    if (!check(TokenType.SEMICOLON)) value = expression();
    consume(TokenType.SEMICOLON, "Expect ';' after return value.");
    return new Stmt.Return(keyword, value);
  }

  private Stmt breakStatement() {
    if (loopDepth == 0) {
      throw error(previous(), "Expect 'break' inside a loop.");
    }
    consume(TokenType.SEMICOLON, "Expect ';' after break.");
    return new Stmt.Break();
  }

  private Stmt continueStatement() {
    if (loopDepth == 0) throw error(previous(), "Expect 'continue' inside a loop.");
    consume(TokenType.SEMICOLON, "Expect ';' after continue.");
    return new Stmt.Continue();
  }

  private Stmt ifStatement() {
    consume(TokenType.LEFT_PAREN, "Expect '(' after 'if'.");
    Expr condition = expression();
    consume(TokenType.RIGHT_PAREN, "Expect ')' after if condition.");

    Stmt thenBranch = statement();
    Stmt elseBranch = null;
    if (match(TokenType.ELSE)) {
      elseBranch = statement();
    }

    return new Stmt.If(condition, thenBranch, elseBranch);
  }

  private Stmt whileStatement() {
    loopDepth++;
    try {
      consume(TokenType.LEFT_PAREN, "Expect '(' after 'while'.");
      Expr condition = expression();
      consume(TokenType.RIGHT_PAREN, "Expect ')' after condition.");
      Stmt body = statement();
      return new Stmt.While(condition, body);
    } finally {
      loopDepth--;
    }
  }

  private Stmt forStatement() {
    consume(TokenType.LEFT_PAREN, "Expect '(' after 'for'.");

    Stmt initializer;
    if (match(TokenType.SEMICOLON)) {
      initializer = null;
    } else if (match(TokenType.VAR)) {
      initializer = varDeclaration();
    } else {
      initializer = expressionStatement();
    }

    Expr condition = null;
    if (!check(TokenType.SEMICOLON)) {
      condition = expression();
    }
    consume(TokenType.SEMICOLON, "Expect ';' after loop condition.");

    Expr increment = null;
    if (!check(TokenType.RIGHT_PAREN)) {
      increment = expression();
    }
    consume(TokenType.RIGHT_PAREN, "Expect ')' after for clauses.");

    loopDepth++;
    Stmt body;
    try {
      body = statement();
    } finally {
      loopDepth--;
    }
    return new Stmt.For(initializer, condition, increment, body);
  }

  private Stmt printStatement() {
    Expr value = expression();
    consume(TokenType.SEMICOLON, "Expect ';' after value.");
    return new Stmt.Print(value);
  }

  private Stmt expressionStatement() {
    Expr expr = expression();
    if (check(TokenType.SEMICOLON)) {
      advance();
    }
    return new Stmt.Expression(expr);
  }

  private List<Stmt> block() {
    List<Stmt> statements = new ArrayList<>();

    while (!check(TokenType.RIGHT_BRACE) && !isAtEnd()) {
      statements.add(declaration());
    }

    consume(TokenType.RIGHT_BRACE, "Expect '}' after block.");
    return statements;
  }

  private Expr expression() {
    return assignment();
  }

  private Expr comma() {
    Expr expr = conditional();

    while (match(TokenType.COMMA)) {
      Token operator = previous();
      Expr right = conditional();
      expr = new Expr.Comma(expr, right);
    }

    return expr;
  }

  private Expr conditional() {
    Expr expr = or();

    if (match(TokenType.QUESTION)) {
      Token question = previous();
      Expr thenBranch = expression();
      consume(TokenType.COLON, "Expect ':' after then branch of conditional operator.");
      Expr elseBranch = conditional();
      expr = new Expr.Ternary(expr, thenBranch, elseBranch);
    }

    return expr;
  }

  private Expr assignment() {
    Expr expr = or();

    if (match(TokenType.EQUAL)) {
      Token equals = previous();
      Expr value = assignment();

      if (expr instanceof Expr.Variable) {
        Token name = ((Expr.Variable)expr).name;
        return new Expr.Assign(name, value);
      } else if (expr instanceof Expr.Get) {
        Expr.Get get = (Expr.Get) expr;
        return new Expr.Set(get.object, get.name, value);
      }

      error(equals, "Invalid assignment target.");
    }

    return expr;
  }

  private Expr or() {
    Expr expr = and();

    while (match(TokenType.OR)) {
      Token operator = previous();
      Expr right = and();
      expr = new Expr.Logical(expr, operator, right);
    }

    return expr;
  }

  private Expr and() {
    Expr expr = equality();

    while (match(TokenType.AND)) {
      Token operator = previous();
      Expr right = equality();
      expr = new Expr.Logical(expr, operator, right);
    }

    return expr;
  }

  private Expr equality() {
    Expr expr = comparison();

    while (match(TokenType.BANG_EQUAL, TokenType.EQUAL_EQUAL)) {
      Token operator = previous();
      Expr right = comparison();
      expr = new Expr.Binary(expr, operator, right);
    }

    return expr;
  }

  private Expr comparison() {
    Expr expr = term();

    while (match(TokenType.GREATER, TokenType.GREATER_EQUAL, TokenType.LESS, TokenType.LESS_EQUAL)) {
      Token operator = previous();
      Expr right = term();
      expr = new Expr.Binary(expr, operator, right);
    }

    return expr;
  }

  private Expr term() {
    Expr expr = factor();

    while (match(TokenType.MINUS, TokenType.PLUS)) {
      Token operator = previous();
      Expr right = factor();
      expr = new Expr.Binary(expr, operator, right);
    }

    return expr;
  }

  private Expr factor() {
    Expr expr = unary();

    while (match(TokenType.SLASH, TokenType.STAR)) {
      Token operator = previous();
      Expr right = unary();
      expr = new Expr.Binary(expr, operator, right);
    }

    return expr;
  }

  private Expr unary() {
    if (match(TokenType.PLUS, TokenType.STAR, TokenType.SLASH, TokenType.BANG_EQUAL, TokenType.EQUAL_EQUAL, TokenType.GREATER, TokenType.GREATER_EQUAL, TokenType.LESS, TokenType.LESS_EQUAL)) {
      Token operator = previous();
      Lox.error(operator, "Binary operator '" + operator.lexeme + "' missing left-hand operand.");
      unary();
      return null;
    }

    if (match(TokenType.BANG, TokenType.MINUS)) {
      Token operator = previous();
      Expr right = unary();
      return new Expr.Unary(operator, right);
    }

    return call();
  }

  private Expr call() {
    Expr expr = primary();
    while (true) {
      if (match(TokenType.LEFT_PAREN)) {
        expr = finishCall(expr);
      } else if (match(TokenType.DOT)) {
        Token name = consume(TokenType.IDENTIFIER, "Expect property name after '.'.");
        expr = new Expr.Get(expr, name);
      } else {
        break;
      }
    }
    return expr;
  }

  private Expr finishCall(Expr callee) {
    List<Expr> arguments = new ArrayList<>();
    if (!check(TokenType.RIGHT_PAREN)) {
      do {
        if (arguments.size() >= 255) {
          error(peek(), "Can't have more than 255 arguments.");
        }
        arguments.add(expression());
      } while (match(TokenType.COMMA));
    }
    Token paren = consume(TokenType.RIGHT_PAREN, "Expect ')' after arguments.");
    return new Expr.Call(callee, paren, arguments);
  }

  private Expr primary() {
    if (match(TokenType.FUN)) {
      consume(TokenType.LEFT_PAREN, "Expect '(' after 'fun'.");
      List<Token> parameters = parameters();
      consume(TokenType.LEFT_BRACE, "Expect '{' before function body.");
      functionDepth++;
      int enclosingLoopDepth = loopDepth;
      loopDepth = 0;
      List<Stmt> body;
      try {
        body = block();
      } finally {
        loopDepth = enclosingLoopDepth;
        functionDepth--;
      }
      return new Expr.Function(parameters, body);
    }
    if (match(TokenType.SUPER)) {
      Token keyword = previous();
      consume(TokenType.DOT, "Expect '.' after 'super'.");
      Token method = consume(TokenType.IDENTIFIER, "Expect superclass method name.");
      return new Expr.Super(keyword, method);
    }
    if (match(TokenType.INNER)) return new Expr.Inner(previous());
    if (match(TokenType.THIS)) return new Expr.This(previous());
    if (match(TokenType.FALSE)) return new Expr.Literal(false);
    if (match(TokenType.TRUE)) return new Expr.Literal(true);
    if (match(TokenType.NIL)) return new Expr.Literal(null);

    if (match(TokenType.NUMBER, TokenType.STRING)) {
      return new Expr.Literal(previous().literal);
    }

    if (match(TokenType.IDENTIFIER)) {
      return new Expr.Variable(previous());
    }

    if (match(TokenType.LEFT_PAREN)) {
      Expr expr = expression();
      consume(TokenType.RIGHT_PAREN, "Expect ')' after expression.");
      return new Expr.Grouping(expr);
    }

    throw error(peek(), "Expect expression.");
  }

  private boolean match(TokenType... types) {
    for (TokenType type : types) {
      if (check(type)) {
        advance();
        return true;
      }
    }
    return false;
  }

  private Token consume(TokenType type, String message) {
    if (check(type)) return advance();
    throw error(peek(), message);
  }

  private boolean check(TokenType type) {
    if (isAtEnd()) return false;
    return peek().type == type;
  }

  private boolean checkNext(TokenType type) {
    if (current + 1 >= tokens.size()) return false;
    return tokens.get(current + 1).type == type;
  }

  private Token advance() {
    if (!isAtEnd()) current++;
    return previous();
  }

  private boolean isAtEnd() {
    return peek().type == TokenType.EOF;
  }

  private Token peek() {
    return tokens.get(current);
  }

  private Token previous() {
    return tokens.get(current - 1);
  }

  private ParseError error(Token token, String message) {
    Lox.error(token, message);
    return new ParseError();
  }

  private void synchronize() {
    advance();

    while (!isAtEnd()) {
      if (previous().type == TokenType.SEMICOLON) return;

      switch (peek().type) {
        case CLASS:
        case FUN:
        case VAR:
        case FOR:
        case IF:
        case WHILE:
        case PRINT:
        case RETURN:
          return;
        default:
          break;
      }

      advance();
    }
  }
}