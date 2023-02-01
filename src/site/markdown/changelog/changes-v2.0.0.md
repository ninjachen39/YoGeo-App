# Changelog v2.0.0

Version 2.0.0 of CLAVIN includes some restructuring of the API, adds some new functionality, and offers some minor performance-enhancing tweaks to the core resolution algorithm.

This list details most of the major and minor changes made in CLAVIN v2.0.0:

- changed API for location resolver to be simpler & easier to use
    - ClavinLocationResolver is now the primary class for resolving location names
    - LocationResolver interface and LuceneLocationResolver class are now deprecated
- new API enabling direct queries of CLAVIN's internal gazetteer index
    - enables quick lookup of details for known locations in gazetteer
    - see package clavin.gazetteer.query for details
- new IndexDirectory structure supporting the following:
    - hierarchical "ancestry" of GeoName records (i.e., city --> province --> country)
    - using common/short place names from the GeoNames.org alternate names file
    - see clavin/index/IndexDirectoryBuilder.java for details
- new MultipartLocationResolver for resolving location names in structured data
    - designed to handle multipart location names, such as those often seen in spreadsheets or databases (e.g., `[Reston][Virginia][United States]`)
    - see package clavin.resolver.multipart for details
- fixed subtle bug in core resolution algorithm that mistakenly treated admin1 codes from different countries as the same thing (doh!)
- boosted cities & countries in core resolution algorithm, since these are the things mentioned most often in text
    - for example, this helps Washington DC win over Washington State, and the city of Cairo win over Cairo Governorate

These changes, especially the algorithmic modifications, have yielded a modest -- *yet noticeable and welcome* -- improvement to resolution accuracy.
