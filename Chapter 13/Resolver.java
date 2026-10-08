package lox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class Resolver implements Expr.Visitor<Void>, Stmt.Visitor<Void> {
    private enum FunctionType {
        NONE,
        FUNCTION,
        METHOD,
        INITIALIZER
    }

    private enum ClassType {
        NONE,
        CLASS,
        SUBCLASS
    }

    private enum SymbolType {
        VARIABLE,
        PARAMETER,
        FUNCTION
    }

    private static class Symbol {
        final Token declaration;
        final int slot;
        final SymbolType type;
        boolean defined;
        boolean used;

        Symbol(Token declaration, int slot, SymbolType type) {
            this.declaration = declaration;
            this.slot = slot;
            this.type = type;
        }
    }

    private static class Scope {
        final Map<String, Symbol> symbols = new HashMap<>();
        int nextSlot;
    }

    private final Interpreter interpreter;
    private final List<Scope> scopes = new ArrayList<>();
    private FunctionType currentFunction = FunctionType.NONE;
    private ClassType currentClass = ClassType.NONE;

    Resolver(Interpreter interpreter) {
        this.interpreter = interpreter;
    }

    void resolve(List<Stmt> statements) {
        for (Stmt statement : statements) {
            resolve(statement);
        }
    }

    private void resolve(Stmt stmt) {
        if (stmt != null) stmt.accept(this);
    }

    private void resolve(Expr expr) {
        if (expr != null) expr.accept(this);
    }

    private void beginScope() {
        scopes.add(new Scope());
    }

    private int endScope() {
        Scope scope = scopes.remove(scopes.size() - 1);
        for (Map.Entry<String, Symbol> entry : scope.symbols.entrySet()) {
            Symbol symbol = entry.getValue();
            if (symbol.type == SymbolType.VARIABLE && !symbol.used) {
                Lox.error(symbol.declaration, "Local variable '" + entry.getKey() + "' is never used.");
            }
        }
        return scope.nextSlot;
    }

    private int declare(Token name, SymbolType type) {
        if (scopes.isEmpty()) return -1;

        Scope scope = scopes.get(scopes.size() - 1);
        Symbol previous = scope.symbols.get(name.lexeme);
        if (previous != null) {
            Lox.error(name, "Already a variable with this name in this scope.");
            return previous.slot;
        }

        int slot = scope.nextSlot++;
        scope.symbols.put(name.lexeme, new Symbol(name, slot, type));
        return slot;
    }

    private void define(Token name) {
        if (scopes.isEmpty()) return;
        Symbol symbol = scopes.get(scopes.size() - 1).symbols.get(name.lexeme);
        if (symbol != null) symbol.defined = true;
    }

    private void resolveLocal(Expr expr, Token name, boolean read) {
        for (int i = scopes.size() - 1; i >= 0; i--) {
            Symbol symbol = scopes.get(i).symbols.get(name.lexeme);
            if (symbol != null) {
                if (read && symbol.type == SymbolType.VARIABLE) symbol.used = true;
                interpreter.resolve(expr, scopes.size() - 1 - i, symbol.slot);
                return;
            }
        }
    }

    private void resolveFunction(List<Token> params, List<Stmt> body, FunctionType type,
                                 java.util.function.IntConsumer localCountSetter) {
        FunctionType enclosingFunction = currentFunction;
        currentFunction = type;
        beginScope();
        for (Token parameter : params) {
            declare(parameter, SymbolType.PARAMETER);
            define(parameter);
        }
        for (Stmt statement : body) resolve(statement);
        localCountSetter.accept(endScope());
        currentFunction = enclosingFunction;
    }

    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
        beginScope();
        for (Stmt statement : stmt.statements) resolve(statement);
        stmt.localCount = endScope();
        return null;
    }

    @Override
    public Void visitBreakStmt(Stmt.Break stmt) {
        return null;
    }

    @Override
    public Void visitContinueStmt(Stmt.Continue stmt) {
        return null;
    }

    @Override
    public Void visitClassStmt(Stmt.Class stmt) {
        stmt.slot = declare(stmt.name, SymbolType.VARIABLE);
        define(stmt.name);

        ClassType enclosingClass = currentClass;
        currentClass = ClassType.CLASS;
        if (stmt.superclass != null) {
            currentClass = ClassType.SUBCLASS;
            if (stmt.name.lexeme.equals(stmt.superclass.name.lexeme)) {
                Lox.error(stmt.superclass.name, "A class can't inherit from itself.");
            }
            resolve(stmt.superclass);
            beginScope();
            Scope superScope = scopes.get(scopes.size() - 1);
            superScope.symbols.put("super", new Symbol(stmt.superclass.name,
                    superScope.nextSlot++, SymbolType.PARAMETER));
        }
        beginScope();
        Scope receiverScope = scopes.get(scopes.size() - 1);
        receiverScope.symbols.put("this", new Symbol(stmt.name, receiverScope.nextSlot++, SymbolType.PARAMETER));
        receiverScope.symbols.put("inner", new Symbol(stmt.name, receiverScope.nextSlot++, SymbolType.PARAMETER));

        for (Stmt.Function method : stmt.methods) {
            FunctionType type = method.name.lexeme.equals("init") && !method.isStatic
                    ? FunctionType.INITIALIZER : FunctionType.METHOD;
            resolveFunction(method.params, method.body, type, count -> method.localCount = count);
        }

        endScope();
        if (stmt.superclass != null) endScope();
        currentClass = enclosingClass;
        return null;
    }

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        resolve(stmt.expression);
        return null;
    }

    @Override
    public Void visitFunctionStmt(Stmt.Function stmt) {
        stmt.slot = declare(stmt.name, SymbolType.FUNCTION);
        define(stmt.name);
        resolveFunction(stmt.params, stmt.body, FunctionType.FUNCTION, count -> stmt.localCount = count);
        return null;
    }

    @Override
    public Void visitForStmt(Stmt.For stmt) {
        beginScope();
        resolve(stmt.initializer);
        resolve(stmt.condition);
        resolve(stmt.body);
        resolve(stmt.increment);
        stmt.localCount = endScope();
        return null;
    }

    @Override
    public Void visitIfStmt(Stmt.If stmt) {
        resolve(stmt.condition);
        resolve(stmt.thenBranch);
        resolve(stmt.elseBranch);
        return null;
    }

    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {
        resolve(stmt.expression);
        return null;
    }

    @Override
    public Void visitReturnStmt(Stmt.Return stmt) {
        if (currentFunction == FunctionType.NONE) {
            Lox.error(stmt.keyword, "Can't return from top-level code.");
        }
        if (currentFunction == FunctionType.INITIALIZER && stmt.value != null) {
            Lox.error(stmt.keyword, "Can't return a value from an initializer.");
        }
        resolve(stmt.value);
        return null;
    }

    @Override
    public Void visitVarStmt(Stmt.Var stmt) {
        stmt.slot = declare(stmt.name, SymbolType.VARIABLE);
        resolve(stmt.initializer);
        define(stmt.name);
        return null;
    }

    @Override
    public Void visitWhileStmt(Stmt.While stmt) {
        resolve(stmt.condition);
        resolve(stmt.body);
        return null;
    }

    @Override
    public Void visitAssignExpr(Expr.Assign expr) {
        resolve(expr.value);
        resolveLocal(expr, expr.name, false);
        return null;
    }

    @Override
    public Void visitBinaryExpr(Expr.Binary expr) {
        resolve(expr.left);
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitCallExpr(Expr.Call expr) {
        resolve(expr.callee);
        for (Expr argument : expr.arguments) resolve(argument);
        return null;
    }

    @Override
    public Void visitCommaExpr(Expr.Comma expr) {
        resolve(expr.left);
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitFunctionExpr(Expr.Function expr) {
        resolveFunction(expr.params, expr.body, FunctionType.FUNCTION, count -> expr.localCount = count);
        return null;
    }

    @Override
    public Void visitGetExpr(Expr.Get expr) {
        resolve(expr.object);
        return null;
    }

    @Override
    public Void visitSetExpr(Expr.Set expr) {
        resolve(expr.value);
        resolve(expr.object);
        return null;
    }

    @Override
    public Void visitThisExpr(Expr.This expr) {
        if (currentClass == ClassType.NONE) {
            Lox.error(expr.keyword, "Can't use 'this' outside of a class.");
            return null;
        }
        resolveLocal(expr, expr.keyword, true);
        return null;
    }

    @Override
    public Void visitSuperExpr(Expr.Super expr) {
        Lox.error(expr.keyword, "Use 'inner' to invoke the next method in the inheritance chain.");
        return null;
    }

    @Override
    public Void visitInnerExpr(Expr.Inner expr) {
        if (currentClass == ClassType.NONE) {
            Lox.error(expr.keyword, "Can't use 'inner' outside of a class method.");
        }
        resolveLocal(expr, expr.keyword, false);
        return null;
    }

    @Override
    public Void visitGroupingExpr(Expr.Grouping expr) {
        resolve(expr.expression);
        return null;
    }

    @Override
    public Void visitLiteralExpr(Expr.Literal expr) {
        return null;
    }

    @Override
    public Void visitLogicalExpr(Expr.Logical expr) {
        resolve(expr.left);
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitTernaryExpr(Expr.Ternary expr) {
        resolve(expr.condition);
        resolve(expr.thenBranch);
        resolve(expr.elseBranch);
        return null;
    }

    @Override
    public Void visitUnaryExpr(Expr.Unary expr) {
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitVariableExpr(Expr.Variable expr) {
        if (!scopes.isEmpty()) {
            Symbol current = scopes.get(scopes.size() - 1).symbols.get(expr.name.lexeme);
            if (current != null && !current.defined) {
                Lox.error(expr.name, "Can't read local variable in its own initializer.");
            }
        }
        resolveLocal(expr, expr.name, true);
        return null;
    }
}
