/*******************************************************************************
 * Copyright (c) 2006, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.cdt.core.parser.upc.tests;

import junit.framework.Test;
import junit.framework.TestSuite;

public class UPCParserTestSuite extends TestSuite {
	
	public static Test suite() {
		return new TestSuite() {{
			addTestSuite(UPCCommentTests.class);
			addTestSuite(UPCCompletionBasicTest.class);
			addTestSuite(UPCCompletionParseTest.class);
			addTestSuite(UPCDOMLocationMacroTests.class);
			addTestSuite(UPCDOMLocationTests.class);
			addTestSuite(UPCDOMPreprocessorInformationTest.class);
			addTestSuite(UPCKnRTests.class);
			addTestSuite(UPCSelectionParseTest.class);
			addTestSuite(UPCCSpecTests.class);
			addTestSuite(UPCTests.class);
			addTestSuite(UPCLanguageExtensionTests.class);
			addTestSuite(UPCDigraphTrigraphTests.class);
			addTestSuite(UPCGCCTests.class);
			addTestSuite(UPCUtilOldTests.class);
			addTestSuite(UPCUtilTests.class);
			addTestSuite(UPCCompleteParser2Tests.class);
			addTestSuite(UPCTaskParserTest.class);
		}};
	}
}
