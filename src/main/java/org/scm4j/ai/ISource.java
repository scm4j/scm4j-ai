package org.scm4j.ai;

import java.io.InputStream;
import java.net.URL;

public interface ISource {
	
	InputStream getContent(URL url);

}
