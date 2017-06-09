package org.scm4j.ai;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

public class ProductsTest {
	
	private static final String TEST_ENVIRONMENT_PATH = "org/scm4j/ai/TestEnvironment";
	private File workingFolder;
	private ClassLoader cl;
	
	@Before
	public void setUp() throws IOException {
		MockitoAnnotations.initMocks(this);
		cl = Thread.currentThread().getContextClassLoader();
		workingFolder = Files.createTempDirectory(new File(System.getProperty("java.io.tmpdir")).toPath(), "scm4j-ai-test-").toFile();
		FileUtils.copyDirectory(getResourceFolder(), workingFolder);
	}
	
	private File getResourceFolder() throws IOException {
	    final URL url = cl.getResource(TEST_ENVIRONMENT_PATH);
	    return new File(url.getFile());
	}

	@Test
	public void testProductsReading() throws Exception {
		Products products = new Products(workingFolder);
		assertTrue(products.getProductUrl("guava", "20.0", ".jar").equals("com/google/guava/guava/20.0/guava-20.0.jar"));
	}
	
	@Test
	public void testGetMetadataUrl() throws Exception {
		Products products = new Products(workingFolder);
		assertTrue(products.getMetaDataUrl("guava").equals("com/google/guava/guava/maven-metadata.xml"));
		
	}
}
