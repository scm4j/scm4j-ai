package org.scm4j.ai;

import java.util.ArrayList;
import java.util.List;

public class AIRunner {
	
	private String localPath;
	private ISource facade = new DefaultSource();
	
	public AIRunner(String localPath) {
		this.localPath = localPath;
	}
	
	public List<String> listVersions(String productName) {
		List<String> res = new ArrayList<>();
		
		
		return res;
		
	}

	public void setFacade(ISource facade) {
		this.facade = facade;
	}

}
