package com.novetta.clavin.gazetteer.query;

import org.apache.lucene.search.TopTermsRewrite;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermStates;
import org.apache.lucene.search.DisjunctionMaxQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;


public final class UniqueFuzzyScoringRewrite extends TopTermsRewrite<DisjunctionMaxQuery> {
	private List<Query> disjuncts;

	public UniqueFuzzyScoringRewrite() {
		super(Integer.MAX_VALUE);
		disjuncts = new ArrayList<>();
	}
	
	public UniqueFuzzyScoringRewrite(int size) {
		super(size);
		disjuncts = new ArrayList<>();
	}

	@Override
	protected int getMaxSize() {
		return Integer.MAX_VALUE;
	}

	@Override
	protected DisjunctionMaxQuery getTopLevelBuilder() throws IOException {
		return new DisjunctionMaxQuery(disjuncts, 0);
	}

	@Override
	protected Query build(DisjunctionMaxQuery query) {
		return new DisjunctionMaxQuery(disjuncts, 0);
	}

	@Override
	protected void addClause(DisjunctionMaxQuery topLevel, Term term, int docCount, float boost, TermStates states)
			throws IOException {
		TermQuery query = new TermQuery(term, states);
		disjuncts.add(query);
	}
}
