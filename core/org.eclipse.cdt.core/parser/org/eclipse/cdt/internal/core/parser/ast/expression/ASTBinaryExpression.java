/**********************************************************************
 * Copyright (c) 2002,2003 Rational Software Corporation and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Common Public License v0.5
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v05.html
 * 
 * Contributors: 
 * IBM Rational Software - Initial API and implementation */

package org.eclipse.cdt.internal.core.parser.ast.expression;

import org.eclipse.cdt.core.parser.ast.IASTExpression;

/**
 * @author jcamelon
 *
 */
public class ASTBinaryExpression extends ASTUnaryExpression
		implements
			IASTExpression {

	private final IASTExpression rhs;

	/**
	 * @param kind
	 * @param lhs
	 * @param rhs
	 */
	public ASTBinaryExpression(Kind kind, IASTExpression lhs, IASTExpression rhs) {
		super( kind, lhs );
		this.rhs = rhs;
	}
	/* (non-Javadoc)
	 * @see org.eclipse.cdt.core.parser.ast.IASTExpression#getRHSExpression()
	 */
	public IASTExpression getRHSExpression() {
		return rhs;
	}
}
