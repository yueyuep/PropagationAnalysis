package com.vmusco.smf.mutation.operators.pitest;

import java.util.ArrayList;
import java.util.List;

import com.vmusco.smf.mutation.MutationOperator;

import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtUnaryOperator;
import spoon.reflect.code.UnaryOperatorKind;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtTypedElement;
import spoon.reflect.factory.Factory;

public class NegationUnaryOperatorConditionOperator extends MutationOperator<CtTypedElement>{

	@Override
	public void process(CtTypedElement element) {
		addElement(element);
	}

	@Override
	public CtElement[] getMutatedEntries(CtTypedElement toMutate, Factory factory) {
		List<CtElement> result = new ArrayList<CtElement>();

		if (toMutate instanceof CtUnaryOperator<?>) {
			CtUnaryOperator<?> unary = (CtUnaryOperator<?>) toMutate;
			if (unary.getKind() == UnaryOperatorKind.NOT) {
				CtExpression expIF = factory.Core().clone(unary.getOperand());
				result.add(expIF);
			}
		}else{
			if (toMutate instanceof CtTypedElement<?>) {
				CtExpression<?> inv = (CtExpression<?>) toMutate;
				if (inv.getType()!= null && inv.getType().getSimpleName().equals(boolean.class.getSimpleName())) {
					CtExpression<?> invClone = factory.Core().clone(inv);
					CtUnaryOperator unary = factory.Core().createUnaryOperator();
					unary.setOperand(invClone);
					unary.setKind(UnaryOperatorKind.NOT);
					result.add(unary);
				}
			}
		}

		return result.toArray(new CtElement[0]);
	}

	@Override
	public String operatorId() {
		return "NegationUnaryOperatorConditionOperator";
	}
}
