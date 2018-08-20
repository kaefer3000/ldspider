package com.ontologycentral.ldspider;

import java.util.HashSet;
import java.util.Set;

import org.apache.http.Header;
import org.apache.http.message.BasicHeader;

import com.ontologycentral.ldspider.hooks.content.ContentHandlerRdfXml;
import com.ontologycentral.ldspider.hooks.sink.TakingHopsIntoAccount;
import com.ontologycentral.ldspider.queue.DiskBreadthFirstQueue.CountLifeTime;

public class CrawlerConstants {
	public static final String USERAGENT_NAME = "ldspider";
	public static final String USERAGENT_URI = "http://code.google.com/p/ldspider/wiki/Robots";

	public static final String USERAGENT_LINE = USERAGENT_NAME + " (" + USERAGENT_URI + ")";
	
	// http://www.w3.org/TR/owl-ref/#MIMEType
	public static String[] MIMETYPES = { "application/rdf+xml", "application/xml" };
	public static String[] FILESUFFIXES = { ".rdf", ".owl", ".rdfs" };
	
	public static final Header[] HEADERS = {
		new BasicHeader("Accept", MIMETYPES[0] + ", " + MIMETYPES[1]),
		new BasicHeader("User-Agent", USERAGENT_LINE),
		new BasicHeader("Accept-Encoding", "gzip")
	};

	public static boolean AU_WHITELIST = false;

	public static String[] SUFFIX_BLACKLIST = { ".fr", ".de" };
	
	public static String[] BLACKLIST = { ".txt", ".html", ".xhtml", ".json", ".ttl", ".nt", ".jpg", ".pdf", ".htm", ".png", ".jpeg", ".gif" };
	
	public static String[] SITES_NO_RDF = { "wikipedia.org", "wikimedia.org", "slideshare.net", "imdb.com", "twimg.com", "dblp.uni-trier.de", "flickr.com", "amazon.com", "last.fm" };
	//public static String[] SITES_NO_RDF;

	public static String[] SITES_SLOW = { "l3s.de", "semantictweet.com", "kaufkauf.net", "rpi.edu", "uniprot.org", "geonames.org", "dbtune" };
	public static final int SLOW_DIV = 20;

	public static int CONNECTION_TIMEOUT = 2*16*1000;
	public static int SOCKET_TIMEOUT = 2*16*1000;

	public static final int MAX_CONNECTIONS_PER_THREAD = 32;
	
	public static final int RETRIES = 0;

	public static int MAX_REDIRECTS_DEFAULT_SEQUENTIALSTRATEGY = 1;
	public static int MAX_REDIRECTS_DEFAULT_OTHERSTRATEGY = 4;
	public static int MAX_REDIRECTS = MAX_REDIRECTS_DEFAULT_OTHERSTRATEGY;
	

	// default values
	public static final int DEFAULT_NB_THREADS = 2;
	public static final int DEFAULT_NB_ROUNDS = 2;
	public static final int DEFAULT_NB_URIS = Integer.MAX_VALUE;
	public static final char PREPEND = '<';
	public static final char APPEND = '>';	

	// avoid hammering plds
	public static long MIN_DELAY = 500;
	// for bfs queue: max time after plds get re-visited
	public static long MAX_DELAY = 2*MIN_DELAY;
	
	// close idle connections
	public static final int CLOSE_IDLE = 60000;
	
	// our status codes
	public static final int SKIP_SUFFIX = 497;
	public static final int SKIP_ROBOTS = 498;
	public static final int SKIP_MIMETYPE = 499;
	
	public static int URI_LIMIT_WITH_NON_EMPTY_RDF = 0;
	public static boolean URI_LIMIT_ENABLED = false;
	
	public static int NB_THREADS;
	public static boolean DISKFRONTIER_SORT_BEFORE_ITERATING = false;
	public static boolean DISKFRONTIER_GZIP_FRONTIER = false;
	
	public static CountLifeTime DISKBREADTHFIRSTQUEUE_COUNTLIFETIME = CountLifeTime.ETERNALLY;
	public static String DISKBREADTHFIRSTQUEUE_ETERNALCOUNTINPUTFILENAME = null;
	public static String DISKBREADTHFIRSTQUEUE_ETERNALCOUNTSAVEBASEFILENAME = null;
	
	public static boolean BREADTHFIRSTQUEUE_ONDISK = false;
	
	public static boolean DUMP_FRONTIER = false;
	public static String DUMP_FRONTIER_FILENAME = "";
	
	public static boolean DUMP_SEEN = false;
	public static String DUMP_SEEN_BASEFILENAME = "";
	public static String DUMP_SEEN_FILE_EXTENSION = ""; 

	public static boolean SPLIT_HOPWISE = false;
	
	public static final Set<TakingHopsIntoAccount> THOSE_WHO_TAKE_HOPS_INTO_ACCOUNT = new HashSet<TakingHopsIntoAccount>();
	
	/**
	 * Register your Closeables here for closing them if the JVM gets shut down
	 * (including CTRL+C'ed).
	 */
	public static final CloseablesCloser CLOSER = new CloseablesCloser();
}
