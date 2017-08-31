package org.scm4j.ai;

import org.scm4j.ai.DepCoords;

import junit.framework.TestCase;

public class DepCoordsTest extends TestCase {

	public void testCoords() {
		try {
			new DepCoords("");
			fail();
		} catch (IllegalArgumentException e) {
		}
	}

	DepCoords dc(String coords) {
		return new DepCoords(coords);
	}

	public void testComment() {
		assertEquals("", dc("com.myproject:c1").getComment());
		assertEquals("#", dc("com.myproject:c1#").getComment());
		assertEquals("#...$ #", dc("com.myproject:c1#...$ #").getComment());
	}

	public void testExtension() {
		assertEquals("", dc("com.myproject:c1").getExtension());
		assertEquals("@", dc("com.myproject:c1@").getExtension());
		assertEquals("@ext", dc("com.myproject:c1@ext#qw").getExtension());
		assertEquals("@ext@", dc("com.myproject:c1@ext@#qw").getExtension());
	}

	public void testClassifier() {
		assertEquals("", dc("com.myproject:c1").getClassifier());
		assertEquals(":", dc("com.myproject:c1::").getClassifier());
		assertEquals(":class", dc("com.myproject:c1::class:").getClassifier());
	}

	public void testToSting() {
		assertEquals("com.myproject:c1:1.0.0", dc("com.myproject:c1:1.0.0").toString());
		assertEquals("com.myproject:  c1:1.0.0", dc("com.myproject:  c1:1.0.0").toString());
		assertEquals("   com.myproject:  c1:1.0.0", dc("   com.myproject:  c1:1.0.0").toString());
		assertEquals("com.myproject:c1:1.0.0#comment", dc("com.myproject:c1:1.0.0#comment").toString());
		assertEquals("com.myproject:c1:1.0.0@ext #comment", dc("com.myproject:c1:1.0.0@ext #comment").toString());
		assertEquals("com.myproject:c1::dfgd@ext #comment", dc("com.myproject:c1::dfgd@ext #comment").toString());
	}

}
