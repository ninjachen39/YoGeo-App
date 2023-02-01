package com.novetta.clavin.extractor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.junit.Ignore;
import org.junit.Test;

import com.novetta.clavin.util.TextUtils;

public class AdaptNlpExtractorTest {
	/**
	 * Exact same unit test as the one in ApacheExtractorTest but using AdaptNlpExtractor instead.
	 * At the level this kind of unit test is concerned about, behavior should be the same.
	 * Obviously that would not be the case if things like accuracy were being considered.
	 * This test requires a local instance of AdaptNLP to be running, so ignore it
	 * @throws IOException
	 */
	@Ignore
	@Test
	public void testExtractLocationNames() throws IOException {
		// instantiate the extractor
		AdaptNlpExtractor extractor = new AdaptNlpExtractor();
        
        // a sample input file with some text about Somalia
        File inputFile = new File("src/test/resources/sample-docs/Somalia-doc.txt");
        
        // slurp the contents of the file into a String
        String inputString = TextUtils.fileToString(inputFile);
		//String inputString = "France decided to annex all its petty vassals one day, and the Holy Roman Empire was terrified.";
        
        // extract named location entities from the input String
        List<LocationOccurrence> locationNames1 = extractor.extractLocationNames(inputString);
        
        // make sure we're getting valid output from the extractor
        // (testing the *correctness* of the output is really the
        // responsibility of the Apache OpenNLP NameFinder developers!)
        assertNotNull("Null location name list received from extractor.", locationNames1);
        assertFalse("Empty location name list received from extractor.", locationNames1.isEmpty());
        assertTrue("Extractor choked/quit after first LOCATION.", locationNames1.size() > 1);
        
        // make sure that if we run the extractor on the same input a
        // second time, we get the same output
        List<LocationOccurrence> locationNames2 = extractor.extractLocationNames(inputString);
        assertEquals("Different extractor results for subsequent identical document.", locationNames1, locationNames2);
	}
}
