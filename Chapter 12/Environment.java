package lox;

import java.util.HashMap;
import java.util.Map;

class Environment {
    static final Object UNINITIALIZED = new Object();

    final Environment enclosing;
    private final Map<String, Object> values = new HashMap<>();
    private final Object[] slots;

    Environment() {
        this(null, 0);
    }

    Environment(Environment enclosing) {
        this(enclosing, 0);
    }

    Environment(Environment enclosing, int localCount) {
        this.enclosing = enclosing;
        this.slots = new Object[localCount];
        java.util.Arrays.fill(this.slots, UNINITIALIZED);
    }

    void define(String name, Object value) {
        values.put(name, value);
    }

    void defineAt(int slot, Object value) {
        slots[slot] = value;
    }

    Object getAt(int distance, int slot, Token name) {
        Object value = ancestor(distance).slots[slot];
        if (value == UNINITIALIZED) {
            throw new RuntimeError(name, "Variable '" + name.lexeme + "' is not initialized.");
        }
        return value;
    }

    Object getSlotAt(int distance, int slot) {
        return ancestor(distance).slots[slot];
    }

    void assignAt(int distance, int slot, Object value) {
        ancestor(distance).slots[slot] = value;
    }

    private Environment ancestor(int distance) {
        Environment environment = this;
        for (int i = 0; i < distance; i++) {
            environment = environment.enclosing;
        }
        return environment;
    }

    Object get(Token name) {
        if (!values.containsKey(name.lexeme)) {
            if (enclosing != null) return enclosing.get(name);
            throw new RuntimeError(name, "Undefined variable '" + name.lexeme + "'.");
        }

        Object value = values.get(name.lexeme);
        if (value == UNINITIALIZED) {
            throw new RuntimeError(name, "Variable '" + name.lexeme + "' is not initialized.");
        }

        return value;
    }

    void assign(Token name, Object value) {
        if (!values.containsKey(name.lexeme)) {
            if (enclosing != null) {
                enclosing.assign(name, value);
                return;
            }
            throw new RuntimeError(name, "Undefined variable '" + name.lexeme + "'.");
        }

        values.put(name.lexeme, value);
    }
}
