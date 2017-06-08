package org.scm4j.ai;

import org.junit.Test;
import org.mockito.Mock;

public class AIRunnerTest {
	
	@Mock
	ISource sourceMock;

	@Test
	public void testAIRunner() {
		AIRunner runner = new AIRunner("");
		runner.setFacade(facade);
	}
}
