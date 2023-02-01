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
 * LuceneGazetteer.java
 *
 *###################################################################*/

package com.novetta.clavin.gazetteer.query;

import static com.novetta.clavin.index.IndexField.*;
import static org.apache.lucene.queryparser.classic.QueryParserBase.escape;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BooleanQuery.Builder;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.novetta.clavin.ClavinException;
import com.novetta.clavin.extractor.LocationOccurrence;
import com.novetta.clavin.gazetteer.BasicGeoName;
import com.novetta.clavin.gazetteer.FeatureCode;
import com.novetta.clavin.gazetteer.GeoName;
import com.novetta.clavin.gazetteer.LazyAncestryGeoName;
import com.novetta.clavin.index.BinarySimilarity;
import com.novetta.clavin.index.IndexField;
import com.novetta.clavin.resolver.ResolvedLocation;

/**
 * An implementation of Gazetteer that uses Lucene to rapidly search
 * known locations.
 */
public class LuceneGazetteer implements Gazetteer {
    /**
     * The logger.
     */
    private static final Logger LOG = LoggerFactory.getLogger(LuceneGazetteer.class);

    /**
     * Index employs simple lower-casing & tokenizing on whitespace.
     */
    private static final Analyzer INDEX_ANALYZER;
    static {
    	Analyzer tmp = null;
    	try {
    		tmp = new StandardAnalyzer(Reader.nullReader());
    	}
    	catch (IOException e) {
    		LOG.error("Failed to instantiate StandardAnalyzer for Lucene index");
    	}
    	INDEX_ANALYZER = tmp;
    }

    /**
     * Custom Lucene sorting based on Lucene match score and the
     * population of the GeoNames gazetteer entry represented by the
     * matched index document.
     */
    private static final Sort POPULATION_SORT = new Sort(new SortField[] {
        SortField.FIELD_SCORE,
        new SortField(SORT_POP.key(), SortField.Type.LONG, true)
    });

    /**
     * The default number of results to return.
     */
    private static final int DEFAULT_MAX_RESULTS = 5;

    /**
     * The set of all FeatureCodes.
     */
    private static final Set<FeatureCode> ALL_CODES = Collections.unmodifiableSet(EnumSet.allOf(FeatureCode.class));

    /**
     * The format string for exact match queries.
     */
    private static final String EXACT_MATCH_FMT = "\"%s\"";

    /**
     * The format string for fuzzy queries.
     */
    private static final String FUZZY_FMT = "%s~";

    // Lucene index built from GeoNames gazetteer
    private final FSDirectory index;
    private final IndexSearcher indexSearcher;

    /**
     * Builds a {@link LuceneGazetteer} by loading a pre-built Lucene
     * index from disk and setting configuration parameters for
     * resolving location names to GeoName objects.
     *
     * @param indexDir              Lucene index directory to be loaded
     * @throws ClavinException      if an error occurs opening the index
     */
    public LuceneGazetteer(final File indexDir) throws ClavinException {
        try {
	        // load the Lucene index directory from disk
	        index = FSDirectory.open(indexDir.toPath());
	        indexSearcher = new IndexSearcher(DirectoryReader.open(index));
	
	        // override default TF/IDF score to ignore multiple appearances
	        indexSearcher.setSimilarity(new BinarySimilarity());
	
	        // run an initial throw-away query just to "prime the pump" for
	        // the cache, so we can accurately measure performance speed
	        // per: http://wiki.apache.org/lucene-java/ImproveSearchingSpeed
	        indexSearcher.search(new QueryParser(INDEX_NAME.key(), INDEX_ANALYZER).parse("Reston"),
	        		DEFAULT_MAX_RESULTS, POPULATION_SORT, true);		// double check that last arg...
        } catch (ParseException pe) {
            throw new ClavinException("Error executing priming query.", pe);
        } catch (IOException ioe) {
            throw new ClavinException("Error opening gazetteer index.", ioe);
        }
    }

    /**
     * Execute a query against the Lucene gazetteer index using the provided configuration,
     * returning the top matches as {@link ResolvedLocation}s.
     *
     * @param query              the configuration parameters for the query
     * @return                   the list of ResolvedLocations as potential matches
     * @throws ClavinException   if an error occurs
     */
    @Override
    public List<ResolvedLocation> getClosestLocations(final GazetteerQuery query) throws ClavinException {
        // sanitize the query input
        String sanitizedLocationName = sanitizeQueryText(query);

        // if there is no location to query, return no results
        if ("".equals(sanitizedLocationName)) {
            return Collections.emptyList();
        }

        LocationOccurrence location = query.getOccurrence();	//NOSONAR
        int maxResults = query.getMaxResults() > 0 ? query.getMaxResults() : DEFAULT_MAX_RESULTS;
        List<ResolvedLocation> matches;
        try {
            // attempt to find an exact match for the query
            matches = executeQuery(
            		location, sanitizedLocationName, query, maxResults, false, null);
            if (LOG.isDebugEnabled()) {
                for (ResolvedLocation loc : matches) {
                    LOG.debug("{}", loc);
                }
            }
            // check to see if we should run a fuzzy query based on the configured FuzzyMode
            if (query.getFuzzyMode().useFuzzyMatching(maxResults, matches.size())) {
                // provide any exact matches if we are running a fuzzy query so they can be considered for deduplication
                // and result count
                matches = executeQuery(
                		location, sanitizedLocationName, query, maxResults, true, matches);
                if (LOG.isDebugEnabled()) {
                    for (ResolvedLocation loc : matches) {
                        LOG.debug("{}[fuzzy]", loc);
                    }
                }
            }
            if (matches.isEmpty()) {
                LOG.debug("No match found for: '{}'", location.getText());
            }
        } catch (ParseException pe) {
            throw new ClavinException(String.format("Error parsing query for: '%s'}", location.getText()), pe);
        } catch (IOException ioe) {
            throw new ClavinException(String.format("Error executing query for: '%s'}", location.getText()), ioe);
        }
        return matches;
    }

    /**
     * Executes a query against the Lucene index, processing the results and returning
     * at most maxResults ResolvedLocations with ancestry resolved.
     * @param location the location occurrence
     * @param sanitizedName the sanitized name of the search location
     * @param filterQuery base query for determining how to handle duplicates, ancestors, historical locations, and code restrictions 
     * @param filter the filter used to restrict the search results
     * @param maxResults the maximum number of results
     * @param fuzzy is this a fuzzy query
     * @param previousResults the results of a previous query that should be used for duplicate filtering and appended to until
     *                        no additional matches are found or maxResults has been reached; the input list will not be modified
     *                        and may be <code>null</code>
     * @return the ResolvedLocations with ancestry resolved matching the query
     * @throws ParseException if an error occurs generating the query
     * @throws IOException if an error occurs executing the query
     */
    private List<ResolvedLocation> executeQuery(final LocationOccurrence location, final String sanitizedName,
    		GazetteerQuery filterQuery, final int maxResults, final boolean fuzzy,
            final List<ResolvedLocation> previousResults) throws ParseException, IOException {
    	// combine filters with search term query
    	QueryParser queryParser = new QueryParser(INDEX_NAME.key(), INDEX_ANALYZER);
    	Query query = queryParser.parse(String.format(fuzzy ? FUZZY_FMT : EXACT_MATCH_FMT, sanitizedName));
    	
    	// fuzzy queries use a boolean rewrite that adds all unique fuzzy matches together
    	// instead, only consider the best individual matching term in the document
    	// i.e. search "Bstn~2" should score "Boston Basin" as though it had only one match, not two  
    	if (query instanceof FuzzyQuery) {
    		FuzzyQuery fuzzyQuery = (FuzzyQuery)query;
    		fuzzyQuery.setRewriteMethod(new UniqueFuzzyScoringRewrite());
    		fuzzyQuery.rewrite(indexSearcher.getIndexReader());
    		query = fuzzyQuery;
    	}
    	
    	Builder builder = buildFilters(filterQuery);
    	builder.add(query, Occur.MUST);
    	query = builder.build();

        List<ResolvedLocation> matches = new ArrayList<>(maxResults);
        Map<Integer, Set<GeoName>> parentMap = new HashMap<>();

        // reuse GeoName instances so all ancestry is correctly resolved if multiple names for
        // the same GeoName match the query
        Map<Integer, GeoName> geonameMap = new HashMap<>();
        // if we are filling previous results, add them to the match list and the geoname map
        // so they can be used for deduplication or re-used if additional matches are found
        if (previousResults != null) {
            matches.addAll(previousResults);
            for (ResolvedLocation loc : previousResults) {
                geonameMap.put(loc.getGeoname().getGeonameID(), loc.getGeoname());
            }
        }

        // short circuit if we were provided enough previous results to satisfy maxResults
        // we do this here because the query loop condition is evaluated after the query
        // is executed and results are processed to support de-duplication
        if (matches.size() >= maxResults) {
            return matches;
        }

        // track the last discovered hit so we can re-execute the query if we are
        // deduping and need to fill results
        ScoreDoc lastDoc = null;
        do {
            // collect all the hits up to maxResults, and sort them based
            // on Lucene match score and population for the associated
            // GeoNames record
        	//TopDocs results = indexSearcher.
            TopDocs results = indexSearcher.searchAfter(lastDoc, query, maxResults, POPULATION_SORT, true);	// double check last arg
            // set lastDoc to null so we don't infinite loop if results is empty
            lastDoc = null;
            // populate results if matches were discovered
            for (ScoreDoc scoreDoc : results.scoreDocs) {
                lastDoc = scoreDoc;
                Document doc = indexSearcher.doc(scoreDoc.doc);
                // reuse GeoName instances so all ancestry is correctly resolved if multiple names for
                // the same GeoName match the query
                int geonameID = GEONAME_ID.getValue(doc);
                GeoName geoname = geonameMap.get(geonameID);
                if (geoname == null) {
                    geoname = BasicGeoName.parseFromGeoNamesRecord((String) GEONAME.getValue(doc), (String) PREFERRED_NAME.getValue(doc));
                    geonameMap.put(geonameID, geoname);
                } else if (filterQuery.isFilterDupes()) {
                    // if we have already seen this GeoName and we are removing duplicates, skip to the next doc
                    continue;
                }
                
                String matchedName = INDEX_NAME.getValue(doc);
                if (!geoname.isAncestryResolved()) {
                    IndexableField parentIdField = doc.getField(IndexField.PARENT_ID.key());
                    Integer parentId = parentIdField != null && parentIdField.numericValue() != null ?
                            parentIdField.numericValue().intValue() : null;
                    if (parentId != null) {
                        // if we are lazily or manually loading ancestry, replace GeoName with a LazyAncestryGeoName
                        // otherwise, build the parent resolution map
                        switch (filterQuery.getAncestryMode()) {
                            case LAZY:
                                geoname = new LazyAncestryGeoName(geoname, parentId, this);
                                break;
                            case MANUAL:
                                geoname = new LazyAncestryGeoName(geoname, parentId);
                                break;
                            case ON_CREATE:
                                Set<GeoName> geos = parentMap.computeIfAbsent(parentId, k -> new HashSet<>());
                                geos.add(geoname);
                                break;
                        }
                    }
                }
                matches.add(new ResolvedLocation(location, geoname, matchedName, fuzzy));
                // stop processing results if we have reached maxResults matches
                if (matches.size() >= maxResults) {
                    break;
                }
            }
        } while (filterQuery.isFilterDupes() && lastDoc != null && matches.size() < maxResults);
        // if any results need ancestry resolution, resolve parents
        // this map should only contain GeoNames if ancestryMode == ON_CREATE
        if (!parentMap.isEmpty()) {
            resolveParents(parentMap);
        }
        //Explanation explanation1 = indexSearcher.explain(query, 17254382);	// compare incorrect score
        //Explanation explanation2 = indexSearcher.explain(query, 20381356);	// compare correct score
        return matches;
    }

    /**
     * Sanitizes the text of the LocationOccurrence in the query parameters for
     * use in a Lucene query, returning an empty string if no text is found.
     * @param query the query configuration
     * @return the santitized query text or the empty string if there is no query text
     */
    private String sanitizeQueryText(final GazetteerQuery query) {
        String sanitized = "";
        if (query != null && query.getOccurrence() != null) {
            String text = query.getOccurrence().getText();
            if (text != null) {
                sanitized = escape(text.trim().toLowerCase());
            }
        }
        return sanitized;
    }

    /**
     * Builds a Lucene search filter based on the provided parameters.
     * @param params the query configuration parameters
     * @return a Lucene search filter that will restrict the returned documents to the criteria provided or <code>null</code>
     *         if no filtering is necessary
     */
    private Builder buildFilters(final GazetteerQuery params) {
        List<Query> queryParts = new ArrayList<>();

        // create the historical locations restriction if we are not including historical locations
        if (!params.isIncludeHistorical()) {
        	int val = IndexField.getBooleanIndexValue(false);
        	queryParts.add(IntPoint.newExactQuery(HISTORICAL.key(), val));
        }

        // create the parent ID restrictions if we were provided at least one parent ID
        Set<Integer> parentIds = params.getParentIds();
        if (!parentIds.isEmpty()) {
        	// locations must descend from at least one of the specified parents (OR)
            queryParts.add(IntPoint.newSetQuery(ANCESTOR_IDS.key(), parentIds));
        }

        // create the feature code restrictions if we were provided some, but not all, feature codes
        Set<FeatureCode> codes = params.getFeatureCodes();
        if (!(codes.isEmpty() || ALL_CODES.equals(codes))) {
            Builder codeQuery = new BooleanQuery.Builder();
            // locations must be one of the specified feature codes (OR)
            for (FeatureCode code : codes) {
                codeQuery.add(new TermQuery(new Term(FEATURE_CODE.key(), code.name())), Occur.SHOULD);
            }
            queryParts.add(codeQuery.build());
        }

        // combine all query parts
        Builder builder = new BooleanQuery.Builder();
        for (Query part : queryParts) {
        	builder.add(part, Occur.MUST);
        }
        return builder;
    }

    /**
     * Retrieves and sets the parents of the provided children.
     * @param childMap the map of parent geonameID to the set of children that belong to it
     * @throws IOException if an error occurs during parent resolution
     */
    private void resolveParents(final Map<Integer, Set<GeoName>> childMap) throws IOException {
        Map<Integer, GeoName> parentMap = new HashMap<>();
        Map<Integer, Set<GeoName>> grandParentMap = new HashMap<>();
        for (Integer parentId : childMap.keySet()) {
            // Lucene query used to look for exact match on the "geonameID" field
            Query q = IntPoint.newExactQuery(GEONAME_ID.key(), parentId);
            TopDocs results = indexSearcher.search(q, 1, POPULATION_SORT, true);		// another mystery bool
            if (results.scoreDocs.length > 0) {
                Document doc = indexSearcher.doc(results.scoreDocs[0].doc);
                GeoName parent = BasicGeoName.parseFromGeoNamesRecord(doc.get(GEONAME.key()), doc.get(PREFERRED_NAME.key()));
                parentMap.put(parent.getGeonameID(), parent);
                if (!parent.isAncestryResolved()) {
                    Integer grandParentId = PARENT_ID.getValue(doc);
                    if (grandParentId != null) {
                        Set<GeoName> geos = grandParentMap.computeIfAbsent(grandParentId, k -> new HashSet<>());
                        geos.add(parent);
                    }
                }
            } else {
                LOG.error("Unable to find parent GeoName [{}]", parentId);
            }
        }

        // find all parents of the parents
        if (!grandParentMap.isEmpty()) {
            resolveParents(grandParentMap);
        }

        // set parents of children
        for (Map.Entry<Integer, Set<GeoName>> entry : childMap.entrySet()) {
            final Integer parentId = entry.getKey();

            GeoName parent = parentMap.get(parentId);
            if (parent == null) {
                LOG.info("Unable to find parent with ID [{}]", parentId);
                continue;
            }
            for (GeoName child : entry.getValue()) {
                child.setParent(parent);
            }
        }
    }

    @Override
    public GeoName getGeoName(final int geonameId) throws ClavinException {
        return getGeoName(geonameId, AncestryMode.LAZY);
    }

    @Override
    public GeoName getGeoName(final int geonameId, final AncestryMode ancestryMode) throws ClavinException {
        try {
            GeoName geoName = null;
            // Lucene query used to look for exact match on the "geonameID" field
            Query q = IntPoint.newExactQuery(GEONAME_ID.key(), geonameId);
            // retrieve only one matching document
            TopDocs results = indexSearcher.search(q, 1);
            if (results.scoreDocs.length > 0) {
                Document doc = indexSearcher.doc(results.scoreDocs[0].doc);
                geoName = BasicGeoName.parseFromGeoNamesRecord(doc.get(GEONAME.key()), doc.get(PREFERRED_NAME.key()));
                if (!geoName.isAncestryResolved()) {
                    Integer parentId = PARENT_ID.getValue(doc);
                    if (parentId != null) {
                        switch (ancestryMode) {
                            case ON_CREATE:
                                Map<Integer, Set<GeoName>> childMap = new HashMap<>();
                                childMap.put(parentId, Collections.singleton(geoName));
                                resolveParents(childMap);
                                break;
                            case LAZY:
                                // ancestry will be loaded on request
                                geoName = new LazyAncestryGeoName(geoName, parentId, this);
                                break;
                            case MANUAL:
                                // ancestry must be loaded manually
                                geoName = new LazyAncestryGeoName(geoName, parentId);
                                break;
                        }
                    }
                }
            } else {
                LOG.debug("No geoname found for ID: {}", geonameId);
            }
            return geoName;
        } catch (IOException e) {		// NOSONAR	
            String msg = String.format("Error retrieving geoname with ID : %d", geonameId);
            LOG.error(msg, e);
            throw new ClavinException(msg, e);
        }
    }

    @Override
    public void loadAncestry(GeoName... geoNames) throws ClavinException {
        loadAncestry(Arrays.asList(geoNames));
    }

    @Override
    public void loadAncestry(Collection<GeoName> geoNames) throws ClavinException {
        Map<Integer, Set<GeoName>> parentMap = new HashMap<>();
        for (GeoName geoName : geoNames) {
            Integer parentId = geoName.getParentId();
            if (!geoName.isAncestryResolved() && parentId != null) {
                Set<GeoName> geos = parentMap.computeIfAbsent(parentId, k -> new HashSet<>());
                geos.add(geoName);
            }
        }
        if (!parentMap.isEmpty()) {
            try {
                resolveParents(parentMap);
            } catch (IOException ioe) {
                throw new ClavinException("Error loading ancestry.", ioe);
            }
        }
    }
}
