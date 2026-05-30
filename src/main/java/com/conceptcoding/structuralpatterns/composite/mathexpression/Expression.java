package com.conceptcoding.structuralpatterns.composite.mathexpression;

// Composite
public class Expression implements ArithmeticExpression {

    ArithmeticExpression leftExpression;
    ArithmeticExpression rightExpression;
    OperationType operation;

    public Expression(ArithmeticExpression leftPart, ArithmeticExpression rightPart, OperationType operation) {
        this.leftExpression = leftPart;
        this.rightExpression = rightPart;
        this.operation = operation;
    }

    public int evaluate() {
        int value = 0;
        switch (operation) {
            case OperationType.ADD:
                value = leftExpression.evaluate() + rightExpression.evaluate();
                break;
            case OperationType.SUBTRACT:
                value = leftExpression.evaluate() - rightExpression.evaluate();
                break;
            case OperationType.DIVIDE:
                value = leftExpression.evaluate() / rightExpression.evaluate();
                break;
            case OperationType.MULTIPLY:
                value = leftExpression.evaluate() * rightExpression.evaluate();
                break;
        }

        System.out.println("Expression value is:" + value);
        return value;
    }
}
