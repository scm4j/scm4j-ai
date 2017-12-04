package org.scm4j.deployer.engine;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ArtifactoryReaderTest {

    @Test
    public void testGetByUrl() {
        ArtifactoryReader reader = ArtifactoryReader.getByUrl("http://1:2@google.com");
        assertEquals("http://google.com/", reader.toString());
        assertEquals("1", reader.getUserName());
        assertEquals("2", reader.getPassword());
    }
}
