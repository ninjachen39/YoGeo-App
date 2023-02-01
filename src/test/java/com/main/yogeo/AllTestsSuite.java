package com.novetta.clavin;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import com.novetta.clavin.gazetteer.BasicGeoNameTest;

/*#####################################################################
 *
 * CLAVIN (Cartographic Location And Vicinity INdexer)
 * ---------------------------------------------------
 *
 * Copyright (C) 2012-2013 Berico Technologies
 * http://clavin.bericotechnologies.com
 *
 * ====================================================================
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * ====================================================================
 *
 * AllTestsSuite.java
 *
 *###################################################################*/

/**
 * Convenience class for running all CLAVIN JUnit tests.
 *
 */
@RunWith(Suite.class)
@SuiteClasses({
    com.novetta.clavin.GeoParserFactoryTest.class,
    com.novetta.clavin.extractor.ApacheExtractorTest.class,
    com.novetta.clavin.extractor.LocationOccurrenceTest.class,
    BasicGeoNameTest.class,
    com.novetta.clavin.index.BinarySimilarityTest.class,
    com.novetta.clavin.resolver.ResolvedLocationTest.class,
    com.novetta.clavin.resolver.ClavinLocationResolverTest.class,
    com.novetta.clavin.resolver.ClavinLocationResolverHeuristicsTest.class,
    com.novetta.clavin.resolver.multipart.MultipartLocationResolverTest.class,
    com.novetta.clavin.resolver.multipart.MultiLevelMultipartLocationResolverTest.class,
    com.novetta.clavin.util.DamerauLevenshteinTest.class,
    com.novetta.clavin.util.ListUtilsTest.class,
    com.novetta.clavin.util.TextUtilsTest.class,
    com.novetta.clavin.gazetteer.query.LuceneGazetteerTest.class,
    // this one comes last as it's more of an integration test
    com.novetta.clavin.GeoParserTest.class
})
public class AllTestsSuite {
    // THIS CLASS INTENTIONALLY LEFT BLANK
}
