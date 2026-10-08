package lox;

import java.util.List;

class LoxFunction implements LoxCallable {
    private final String name;
    private final List<Token> params;
    private final List<Stmt> body;
    private final Environment closure;
    private final int localCount;
    private final boolean isInitializer;

    LoxFunction(String name, List<Token> params, List<Stmt> body, Environment closure, int localCount) {
        this(name, params, body, closure, localCount, false);
    }

    LoxFunction(String name, List<Token> params, List<Stmt> body, Environment closure,
                int localCount, boolean isInitializer) {
        this.name = name;
        this.params = params;
        this.body = body;
        this.closure = closure;
        this.localCount = localCount;
        this.isInitializer = isInitializer;
    }

    @Override
    public int arity() {
        return params.size();
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        Environment callEnvironment = new Environment(closure, localCount);
        for (int i = 0; i < params.size(); i++) {
            callEnvironment.defineAt(i, arguments.get(i));
        }

        try {
            interpreter.executeBlock(body, callEnvironment);
        } catch (ReturnException returnValue) {
            if (isInitializer) return closure.getSlotAt(0, 0);
            return returnValue.value;
        }
        if (isInitializer) return closure.getSlotAt(0, 0);
        return null;
    }

    LoxFunction bind(Object receiver) {
        Environment boundEnvironment = new Environment(closure, 1);
        boundEnvironment.defineAt(0, receiver);
        return new LoxFunction(name, params, body, boundEnvironment, localCount, isInitializer);
    }

    @Override
    public String toString() {
        return name == null ? "<fn>" : "<fn " + name + ">";
    }
}