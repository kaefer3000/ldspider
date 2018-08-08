package com.ontologycentral.ldspider;


import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
//import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
//import java.io.StringWriter;
//import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.any23.http.AcceptHeaderBuilder;
import org.apache.any23.mime.MIMEType;
import org.apache.any23.writer.TripleHandler;
import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.http.message.BasicHeader;
import org.semanticweb.yars.nx.Node;
import org.semanticweb.yars.nx.Resource;
import org.semanticweb.yars.nx.parser.Callback;
import org.semanticweb.yars.nx.parser.NxParser;
import org.semanticweb.yars.util.CallbackNxAppender;
//import org.semanticweb.yars.util.CallbackNxOutputStream; @deprecated
import org.semanticweb.yars.util.CallbackNxBufferedWriter;
import org.semanticweb.yars.util.Node2uriConvertingIterator;
import org.semanticweb.yars.util.PleaseCloseTheDoorWhenYouLeaveIterator;

import com.ontologycentral.ldspider.any23.CallbackNQuadTripleHandler;
import com.ontologycentral.ldspider.any23.ContentHandlerAny23;
import com.ontologycentral.ldspider.frontier.BasicFrontier;
import com.ontologycentral.ldspider.frontier.DiskFrontier;
import com.ontologycentral.ldspider.frontier.Frontier;
import com.ontologycentral.ldspider.frontier.RankedFrontier;
import com.ontologycentral.ldspider.frontier.SortingDiskFrontier;
import com.ontologycentral.ldspider.hooks.content.AllrounderPretendingContentHandler;
import com.ontologycentral.ldspider.hooks.content.ContentHandler;
import com.ontologycentral.ldspider.hooks.content.ContentHandlerRdfXml;
import com.ontologycentral.ldspider.hooks.content.ContentHandlers;
import com.ontologycentral.ldspider.hooks.content.ZipContentHandler;
import com.ontologycentral.ldspider.hooks.error.ErrorHandler;
import com.ontologycentral.ldspider.hooks.error.ErrorHandlerLogger;
import com.ontologycentral.ldspider.hooks.error.ErrorHandlerRounds;
import com.ontologycentral.ldspider.hooks.error.ObjectThrowable;
import com.ontologycentral.ldspider.hooks.fetch.FetchFilterSuffix;
import com.ontologycentral.ldspider.hooks.links.LinkFilter;
import com.ontologycentral.ldspider.hooks.links.LinkFilterDefault;
import com.ontologycentral.ldspider.hooks.links.LinkFilterDomain;
import com.ontologycentral.ldspider.hooks.links.LinkFilterDummy;
import com.ontologycentral.ldspider.hooks.links.LinkFilterMakeNoDifferenceBetweenAandTbox;
import com.ontologycentral.ldspider.hooks.links.LinkFilterSelect;
import com.ontologycentral.ldspider.hooks.sink.Sink;
import com.ontologycentral.ldspider.hooks.sink.SinkCallback;
import com.ontologycentral.ldspider.hooks.sink.SinkSparul;
import com.ontologycentral.ldspider.http.Headers;
import com.ontologycentral.ldspider.queue.DummyRedirects;
import com.ontologycentral.ldspider.queue.HashTableRedirects;
import com.ontologycentral.ldspider.queue.Redirects;
import com.ontologycentral.ldspider.seen.HashSetSeen;
import com.ontologycentral.ldspider.seen.Seen;
import com.ontologycentral.ldspider.seen.WrappingCallbackSeen;

public class Main {
	private final static Logger _log = Logger.getLogger(Main.class.getSimpleName());

	public static void main(String[] args) {
		Options options = new Options();

//		OptionGroup input = new OptionGroup();

		Option seeds = OptionBuilder.withArgName("file")
		.hasArgs(1)
		.withDescription("location of seed list")
		.create("s");
		seeds.setRequired(true);
		options.addOption(seeds);

//		Option uri = OptionBuilder.withArgName("uri")
//		.hasArgs(1)
//		.withDescription("uri of an instance")
//		.create("u");
//		uri.setRequired(true);
//		input.addOption(uri);
//		options.addOptionGroup(input);

		//Strategy
		OptionGroup strategy = new OptionGroup();

		/*
		Option ondisk = OptionBuilder.withArgName("directory max-uris")
		.hasArgs(1)
		.withDescription("use on-disk queue with URI selection based on frequency")
		.create("d");
		strategy.addOption(ondisk);
		 */
		//		Option simple = new Option("a", false, "just fetch URIs from list");
		//		strategy.addOption(simple);
		
		Option bfs = new Option("b", false, "do strict breadth-first (uri-limit and pld-limit optional)");
		bfs.setArgs(3);
		bfs.setArgName("depth uri-limit pld-limit");
		strategy.addOption(bfs);

		Option opti = new Option("c", false, "use load balanced crawling strategy");
		opti.setArgs(1);
		opti.setArgName("max-uris");
		strategy.addOption(opti);

		Option raw = new Option("d", false, "download seed URIs and archive raw data");
		raw.setArgs(1);
		raw.setArgName("directory");
		//options.addOption(raw);
		strategy.addOption(raw);

		strategy.setRequired(true);

		options.addOptionGroup(strategy);

		Option header = new Option("e", false, "omit header triple in data");
		header.setArgs(0);
		options.addOption(header);
		
		options.addOption(OptionBuilder.hasArg().withArgName("filename")
				.withDescription("Dump header information to a separate file. It makes no sense to set -e at the same time.")
				.create("dh"));
		
		options.addOption(OptionBuilder
				.hasArg()
				.withArgName("base filename")
				.withDescription(
						"Dump frontier after each round to file (only breadth-first). File name format: <base filename>-<round number>")
				.create("df"));

		Option memory = new Option("m", false, "memory-optimised (puts frontier on disk)");
		memory.setArgs(1);
		memory.setArgName("frontier-file");
		options.addOption(memory);

		Option threads = OptionBuilder.withArgName("threads")
		.hasArgs(1)
		.withDescription("number of threads (default "+CrawlerConstants.DEFAULT_NB_THREADS+")")
		.create("t");
		options.addOption(threads);

		//Link Filters
		OptionGroup linkFilterOptions = new OptionGroup();

		// Option stay = new Option("y", "stay", true,
		// "stay on hostnames of seed uris");
		// linkFilterOptions.addOption(stay);

		Option stay = OptionBuilder
				.withDescription("stay on host with given name").hasArg()
				.withArgName("host").withLongOpt("stay").create("y");
		linkFilterOptions.addOption(stay);

		Option noLinks = new Option("n", false, "do not extract links - just follow redirects");
		linkFilterOptions.addOption(noLinks);

		Option follow = new Option("f", "follow", true, "only follow specific predicates");
		follow.setArgs(1);
		follow.setArgName("uris");
		linkFilterOptions.addOption(follow);

		options.addOptionGroup(linkFilterOptions);

		//Redirects
		Option redirs = OptionBuilder.withArgName("filename[.gz]")
		.hasArgs(1)
		.withDescription("write redirects.nx file")
		.create("r");
		options.addOption(redirs);
		
		Option redirsInternal = OptionBuilder.withDescription("Don't use Redirects.class for Redirects handling").create("dr");
		options.addOption(redirsInternal);

		//Output
		OptionGroup output = new OptionGroup();

		Option outputFile = OptionBuilder.withArgName("filename[.gz]")
		.hasArgs(1)
		.withDescription("name of NQuad file with output")
		.create("o");
		output.addOption(outputFile);
		
		Option uriLimit = OptionBuilder
				.withArgName("number")
				.hasArgs(1)
				.withDescription(
						"Sets a limit for the Uris downloaded overall containing >0 stmts. Hits the interval [limit;limit+#threads]. Not necessarily intended for load-balanced crawling.")
				.create("ul");
		options.addOption(uriLimit);

		Option outputEndpoint = OptionBuilder.withArgName("uri")
		.hasArgs(1)
		.withDescription("SPARQL/Update endpoint for output")
		.create("oe");
		output.addOption(outputEndpoint);

		options.addOptionGroup(output);

		//Logging
		Option log = OptionBuilder.withArgName("filename[.gz]")
		.hasArgs(1)
		.withDescription("name of access log file")
		.create("a");
		options.addOption(log);

		Option vis = OptionBuilder.withArgName("filename[.gz]")
		.hasArgs(1)
		.withDescription("name of file logging rounds")
		.create("v");
		options.addOption(vis);
		
		Option blist = OptionBuilder.withArgName("extensions")
		.hasOptionalArgs()
		.withDescription("overwrite default suffixes of files that are to be ignored in the crawling with the ones supplied. Note: no suffix is also an option. Default: "
			+ Arrays.toString(CrawlerConstants.BLACKLIST))
		.create("bl");
		options.addOption(blist);
		
		Option ctIgnore = new Option("ctIgnore","Parse and fetch disrespective of content-type");
		options.addOption(ctIgnore);
		
		Option any23ExtNames = OptionBuilder.withArgName("any23 extractor names")
		.hasOptionalArgs()
		.withDescription("Override the defaultly selected extractors that are to be loaded with any23. Leave empty to use all any23 has available. Default: "
			+ Arrays.toString(ContentHandlerAny23.getDefaultExtractorNames()))
		.create("any23ext");
		options.addOption(any23ExtNames);
		
		Option any23 = new Option("any23", false, "Use any23 for extending the formats available to parse.");
		options.addOption(any23);
		
		Option accept = OptionBuilder.withArgName("MIME Types").withDescription("Change the HTTP Accept header to the one supplied").withValueSeparator(',').hasOptionalArgs().create("accept");
		options.addOption(accept);

		Option helpO = new Option("h", "help", false, "print help");
		options.addOption(helpO);
		
		Option rankO = new Option(
				"rf",
				"rankFrontier",
				false,
				"If set, the URIs in frontier are ranked according to their number of in-links, and alphabetically as second ordering. Use this option for something like a priority queue.");
		options.addOption(rankO);
		
		Option sortDF = OptionBuilder
				.withArgName("sort gzip")
				.hasOptionalArgs(2)
				.withDescription(
						"Use SortingDiskFrontier as frontier. If URIs are to be returned sorted, add \"sort\" as value, if all temp files involved are to be gzipped, add \"gzip\".")
				.create("sdf");
		options.addOption(sortDF);
		
		Option ctoo = OptionBuilder.withArgName("time in ms").hasArg()
				.withDescription("Set connection timeout. Default: " + CrawlerConstants.CONNECTION_TIMEOUT + "ms")
				.withLongOpt("connection-timeout").create("cto");
		options.addOption(ctoo);
		
		Option stoo = OptionBuilder.withArgName("time in ms").hasArg()
				.withDescription("Set socket timeout. Default: " + CrawlerConstants.SOCKET_TIMEOUT + "ms")
				.withLongOpt("socket-timeout").create("sto");
		options.addOption(stoo);
		
		Option starvLim = OptionBuilder
				.withArgName("min. # of active PLDs")
				.hasArg()
				.withDescription(
						"In order to avoid PLD starvation, set the minimum number of active plds for each breadth first queue round. The seedlist is always downloaded completely (see -mapseed).")
				.create("minpld");
		options.addOption(starvLim);
		
		Option bfqOD = OptionBuilder
				.withDescription(
						"Uses the on-disk BreadthFirstQueue if crawling breadth-first. Doesn't support uri-limit and pld-limit (see -b). Needs -sdf sort to be set. Ranks URIs on the PLDs according to their in-link count.")
				.create("dbfq");
		options.addOption(bfqOD);
		
		Option dbfqInput = OptionBuilder
				.withDescription(
						"For the on-disk breadth-first queue (-dbfq), use this file as input for the eternal in-link count.")
				.hasArg().withArgName("filename").create("dbfqInput");
		options.addOption(dbfqInput);
		
		Option dbfqSave = OptionBuilder
				.withDescription(
						"For the on-disk breadth-first queue (-dbfq), save the eternal in-link count per URI after each hop to this file.")
				.hasArg().withArgName("basefilename").create("dbfqSave");
		options.addOption(dbfqSave);

		Option maxRedirs = OptionBuilder.withArgName("max. # of redirects")
				.hasArg()
				.withDescription(
						"Specify the length a redirects (30x) is allowed to have at max. (default: "
								+ CrawlerConstants.MAX_REDIRECTS_DEFAULT_OTHERSTRATEGY
								+ "; with seq.strategy: "
								+ CrawlerConstants.MAX_REDIRECTS_DEFAULT_SEQUENTIALSTRATEGY
								+ ").")
				.create("mr");
		options.addOption(maxRedirs);
		
		Option minApldSeedlist = OptionBuilder
				.withDescription("Sets if the minimum active plds should already be taken into account downloading the seedlist.")
				.create("mapseed");
		options.addOption(minApldSeedlist);
		
		Option resumebfc = OptionBuilder
				.withDescription(
						"Resume an interrupted breadth-first crawl. Requires a seen file and a redirects file. The old frontier should be the seedlist.")
				.hasArgs(2).withArgName("seenfile redirectsfile").create("resumebfc");
		options.addOption(resumebfc);
		
		Option diskSeen = OptionBuilder
				.withDescription(
						"Choose the on-disk Seen implementation. Argument: basefilename")
				.hasArg().withArgName("filename").create("ds");
		options.addOption(diskSeen);
		
		Option hopWiseSplit = OptionBuilder.withDescription(
				"split output hopwise").create("hopsplit");
		options.addOption(hopWiseSplit);
		
		Option lfMakeNoDifference = OptionBuilder.withDescription(
				"Make No difference between A and T box in crawling").create(
				"lfADignore");
		options.addOption(lfMakeNoDifference);
		
		Option politenessDelay = OptionBuilder
				.withDescription("Time to wait between two requests to a PLD.")
				.hasArg(true).withArgName("time in ms").create("polite");
		options.addOption(politenessDelay);

		CommandLineParser parser = new BasicParser();
		HelpFormatter formatter = new HelpFormatter();
		CommandLine cmd = null;
		try {
			cmd = parser.parse(options, args,true);
			if (cmd.hasOption("h") || cmd.hasOption("help")) {
				formatter.printHelp(80," ","Crawling and lookups on the linked data web\n", options,"\nFeedback and comments are welcome",true );
				System.exit(0);
//			} else if (!cmd.hasOption("s") && !cmd.hasOption("u")) {
//				formatter.printHelp(80," ","ERROR: Missing required option: s or u \n", options,"\nError occured! Please see the error message above",true );
//				System.exit(-1);    
			}

			run(cmd);
		} catch (org.apache.commons.cli.ParseException e) {
			formatter.printHelp(80," ","ERROR: "+e.getMessage()+"\n", options,"\nError occured! Please see the error message above",true );
			System.exit(-1);
		} catch (IOException e) {
			formatter.printHelp(80," ","ERROR: "+e.getMessage()+"\n", options,"\nError occured! Please see the error message above",true );
			System.exit(-1);
		} catch (NumberFormatException e) {
			formatter.printHelp(80," ","ERROR: "+e.getMessage()+"\n", options,"\nError occured! Please see the error message above",true );
			System.exit(-1);
		}
	}

	private static void run(CommandLine cmd) throws FileNotFoundException, IOException {
		// check seed file
		Iterable<URI> seeds = null;
//		if (cmd.hasOption("s")) {
			File seedList = new File(cmd.getOptionValue("s"));
			_log.info("reading seeds from " + seedList.getAbsolutePath());
			if (!seedList.exists()) {
				throw new FileNotFoundException("No file found at "+seedList.getAbsolutePath());
			}
			seeds = prepareSeedsIterable(seedList);
//		} else if (cmd.hasOption("u")) {
//			seeds = new HashSet<URI>();
//			try {
//				seeds.add(new URL(cmd.getOptionValue("u").trim()).toURI());
//			} catch (Exception e) {
//				_log.warning("Discard invalid uri "+e.getMessage()+" for "+cmd.hasOption("u"));
//				e.printStackTrace();
//				System.exit(-1);
//			}
//		}

//		_log.info("no of seed uris " + seeds.size());

		if (cmd.hasOption("hopsplit"))
			CrawlerConstants.SPLIT_HOPWISE = true;
		
		Headers.Treatment headerTreatment = Headers.Treatment.INCLUDE;
		
		if (cmd.hasOption("e")) {
			headerTreatment = Headers.Treatment.DROP;
		}
		
		if (cmd.hasOption("dh"))
			headerTreatment = Headers.Treatment.DUMP;

		Sink sink;
		BufferedWriter os = new BufferedWriter(new OutputStreamWriter(System.out));
		Callback cbData = null;
		Callback cbHeader = null;
		
		if (cmd.hasOption("oe")) {
			sink = new SinkSparul(cmd.getOptionValue("oe"),
					headerTreatment == Headers.Treatment.INCLUDE);
		} else {
			if (cmd.hasOption("o")) {
				String path = cmd.getOptionValue("o");

				if (CrawlerConstants.SPLIT_HOPWISE)
					cbData = new CallbackNxAppender(
							new HopwiseSplittingFileOutputter(path));
				else {
					if (path.endsWith(".gz"))
						os = new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(
							new FileOutputStream(path))));
					else
						os = new BufferedWriter(new OutputStreamWriter(
								new FileOutputStream(path)));
					CrawlerConstants.CLOSER.add(os);
				}
			}

			if (cbData == null)
				cbData = new CallbackNxBufferedWriter(os, false);

			OutputStream headerOS = null;

			if (headerTreatment == Headers.Treatment.DUMP) {
				String path = cmd.getOptionValue("dh");
				if (CrawlerConstants.SPLIT_HOPWISE)
					cbHeader = new CallbackNxAppender(
							new HopwiseSplittingFileOutputter(path));
				else {
					headerOS = new FileOutputStream(path);
					cbHeader = new CallbackNxBufferedWriter(
							new BufferedWriter(new OutputStreamWriter(headerOS)), false);
				}
			}
			if (headerOS != null)
				CrawlerConstants.CLOSER.add(headerOS);


			sink = new SinkCallback(cbData,
						headerTreatment != Headers.Treatment.DROP, cbHeader);
		}

		if (cmd.hasOption("cto")) {
			// overriding the default value which has already been set.
			CrawlerConstants.CONNECTION_TIMEOUT = Integer.parseInt(cmd
					.getOptionValue("cto"));
		}

		if (cmd.hasOption("sto")) {
			// overriding the default value which has already been set.
			CrawlerConstants.SOCKET_TIMEOUT = Integer.parseInt(cmd
					.getOptionValue("sto"));
		}

		// access.log
		Appendable ps = System.out;
		if (cmd.hasOption("a")) {
			if (CrawlerConstants.SPLIT_HOPWISE) {
				ps = new HopwiseSplittingFileOutputter(cmd.getOptionValue("a"));
			} else {
				OutputStream accOs = cmd.getOptionValue("a").endsWith(".gz") ? new GZIPOutputStream(
						new FileOutputStream(cmd.getOptionValue("a")))
						: new FileOutputStream(cmd.getOptionValue("a"));
				ps = new PrintStream(new BufferedOutputStream(accOs));
				if (ps instanceof Closeable)
					CrawlerConstants.CLOSER.add((Closeable) ps);
			}
		}

		// roundslogging
		Appendable rounds = null;
		if (cmd.hasOption("v")) {
			String path = cmd.getOptionValue("v");
			if (CrawlerConstants.SPLIT_HOPWISE) {
				rounds = new HopwiseSplittingFileOutputter(path);
			} else {
				if (path.endsWith(".gz"))
					rounds = new PrintStream(new BufferedOutputStream(
							new GZIPOutputStream(new FileOutputStream(path))));
				else
					rounds = new PrintStream(new FileOutputStream(path));
				if (rounds instanceof Closeable)
					CrawlerConstants.CLOSER.add((Closeable) rounds);
			}
		}

		// redirects
		Callback rcb = null;
		if (cmd.hasOption("r")) {
			if (CrawlerConstants.SPLIT_HOPWISE) {
				rcb = new CallbackNxAppender(new HopwiseSplittingFileOutputter(
						cmd.getOptionValue("r"), true));
			} else {
				BufferedWriter ros = new BufferedWriter(new OutputStreamWriter(
						cmd.getOptionValue("r").endsWith(".gz") ? new GZIPOutputStream(
						(new FileOutputStream(cmd.getOptionValue("r"))))
						: new FileOutputStream(cmd.getOptionValue("r"))));
				BufferedWriter fos = new BufferedWriter(ros);
				CrawlerConstants.CLOSER.add(fos);
				rcb = new CallbackNxBufferedWriter(fos, false);
			}
			rcb.startDocument();
		}

		ErrorHandler eh = null;

		if (rounds != null) {
			eh = new ErrorHandlerRounds(ps, rounds, rcb);			
		} else {
			eh = new ErrorHandlerLogger(ps, rcb, false);
		}

//		Frontier frontier = new RankedFrontier();
//		frontier.setErrorHandler(eh);
//		frontier.addAll(seeds);

		Frontier frontier = new BasicFrontier();
		
		if (cmd.hasOption("rf"))
			frontier = new RankedFrontier();
		else if (cmd.hasOption("m")) 
			frontier = new DiskFrontier(new File(cmd.getOptionValue("m")));
		else if (cmd.hasOption("sdf")) {
			List<String> l = Arrays.asList(cmd.getOptionValues("sdf"));
			if (l.contains("gzip"))
				CrawlerConstants.DISKFRONTIER_GZIP_FRONTIER = true;
			if (l.contains("sort"))
				CrawlerConstants.DISKFRONTIER_SORT_BEFORE_ITERATING = true;
			frontier = new SortingDiskFrontier();
			
			_log.info("Frontier is a SortingDiskFrontier that "
					+ (CrawlerConstants.DISKFRONTIER_SORT_BEFORE_ITERATING ? "sorts"
							: "doesn't sort")
					+ ", "
					+ (CrawlerConstants.DISKFRONTIER_GZIP_FRONTIER ? "gzips"
							: "doesn't gzip") + ".");
		}
			
		frontier.setErrorHandler(eh);
		
		_log.info("loading seedlist into frontier");
		
		for (URI u: seeds)
			frontier.add(u);

		_log.info("frontier done");

		LinkFilter links = null;

		if (cmd.hasOption("y")) {
			LinkFilterDomain lfd = new LinkFilterDomain(frontier);
			lfd.addHost(cmd.getOptionValue("y"));
			// does not work any more because of streaming seed list
			// for (URI u : seeds) {
			// lfd.addHost(u.getHost());
			// }

			links = lfd;
		} else if (cmd.hasOption("n")) {
			LinkFilterDummy d = new LinkFilterDummy();
			links = d;
		} else if(cmd.hasOption("f")) {
			List<Node> predicates = new ArrayList<Node>();
			for(String uri : cmd.getOptionValues("f")) {
				predicates.add(new Resource(uri));
			}
			links = new LinkFilterSelect(frontier, predicates, true);
		} else if (cmd.hasOption("lfADignore")) {
			links = new LinkFilterMakeNoDifferenceBetweenAandTbox(frontier);
		} else {
			links = new LinkFilterDefault(frontier);
		}

		links.setErrorHandler(eh);

		CrawlerConstants.NB_THREADS = CrawlerConstants.DEFAULT_NB_THREADS;
		if (cmd.hasOption("polite"))
			CrawlerConstants.MIN_DELAY = Long.parseLong(cmd.getOptionValue("polite"));

		if (cmd.hasOption("t")) {
			CrawlerConstants.NB_THREADS = Integer.parseInt(cmd.getOptionValue("t"));
		}
		
		if (cmd.hasOption("ul")) {
			CrawlerConstants.URI_LIMIT_WITH_NON_EMPTY_RDF = Integer.parseInt(cmd.getOptionValue("ul"));
			CrawlerConstants.URI_LIMIT_ENABLED = true;
		}
		
		if (cmd.hasOption("dbfq")) {
			CrawlerConstants.BREADTHFIRSTQUEUE_ONDISK = true;
			
			if (cmd.hasOption("dbfqSave"))
				CrawlerConstants.DISKBREADTHFIRSTQUEUE_ETERNALCOUNTSAVEBASEFILENAME = cmd
						.getOptionValue("dbfqSave");
			if (cmd.hasOption("dbfqInput"))
				CrawlerConstants.DISKBREADTHFIRSTQUEUE_ETERNALCOUNTINPUTFILENAME = cmd
						.getOptionValue("dbfqInput");

		}
		
		// Max redirects. Setting appropriate defaults and values.
		if (cmd.hasOption("d"))
			CrawlerConstants.MAX_REDIRECTS = CrawlerConstants.MAX_REDIRECTS_DEFAULT_SEQUENTIALSTRATEGY;
		if (cmd.hasOption("mr"))
			CrawlerConstants.MAX_REDIRECTS = Integer.parseInt(cmd.getOptionValue("mr"));

		long time = System.currentTimeMillis();

		
		// setting up the blacklist of file extensions.
		FetchFilterSuffix blacklist;
		if (cmd.hasOption("bl")) {
			String[] bListFromCli = {};
			if (cmd.getOptionValues("bl")!= null)
				bListFromCli = cmd.getOptionValues("bl");
			for (int i = 0; i < bListFromCli.length; ++i)
				if (!bListFromCli[i].startsWith("."))
					bListFromCli[i] = "." + bListFromCli[i];
			blacklist = new FetchFilterSuffix(bListFromCli);
		} else
			blacklist = new FetchFilterSuffix(CrawlerConstants.BLACKLIST);

		_log.info("init crawler");
		
		Runtime.getRuntime().addShutdownHook(CrawlerConstants.CLOSER);

		Crawler c = new Crawler(CrawlerConstants.NB_THREADS);

		TripleHandler headerTripleHandler = null;
		if (headerTreatment == Headers.Treatment.DUMP)
			headerTripleHandler = new CallbackNQuadTripleHandler(cbHeader);

		ContentHandler ch;
		// luckily, the interpretations of the null pointer from commons cli and
		// any23 fit together.
		if (cmd.hasOption("any23")) {
			ContentHandler chAny23;
			if (cmd.hasOption("any23ext"))
				chAny23 = new ContentHandlerAny23(headerTripleHandler,
						headerTreatment, cmd.getOptionValues("any23ext"));
			else
				chAny23 = new ContentHandlerAny23(headerTripleHandler,
						headerTreatment,
						ContentHandlerAny23.getDefaultExtractorNames());
			if (cmd.hasOption("ctIgnore"))
				chAny23 = new AllrounderPretendingContentHandler(chAny23);
			ch = new ContentHandlers(new ContentHandlerRdfXml(), chAny23);
		} else {
			ch = new ContentHandlerRdfXml();
			if (cmd.hasOption("ctIgnore"))
				ch = new AllrounderPretendingContentHandler(ch);
		}
		c.setContentHandler(ch);

		// changing the accept header
		/** null means keep default. */
		Collection<MIMEType> mimetypes = null;
		if(cmd.hasOption("accept")) {
			String[] mTypes = cmd.getOptionValues("accept");
			if (mTypes == null)
				mimetypes = Collections.emptyList();
			else {
				mimetypes = new LinkedList<MIMEType>();
				for (String s : mTypes)
					if (!s.equals("") && !s.equals(" "))
						try {
							mimetypes.add(MIMEType.parse(s));
						} catch (IllegalArgumentException e) {
							_log.warning(e.getMessage());
							continue;
						}
			}
		} else {
			mimetypes = new LinkedList<MIMEType>();
			for (String s : ch.getMimeTypes())
				try {
					mimetypes.add(MIMEType.parse(s));
				} catch (IllegalArgumentException e) {
					_log.warning(e.getMessage());
					continue;
				}
			if (mimetypes.isEmpty())
				mimetypes = null;
		}

		for (int i = 0; i < CrawlerConstants.HEADERS.length; ++i)
			if (CrawlerConstants.HEADERS[i].getName().equals("Accept")) 
				if (mimetypes != null)
					CrawlerConstants.HEADERS[i] = new BasicHeader("Accept",
							new AcceptHeaderBuilder(mimetypes)
									.getAcceptHeader());
			
		
//		FetchFilter ffrdf = null;
//		if (!cmd.hasOption("ctIgnore")) {
//			if (ch instanceof ContentHandlerRdfXml)
//				ffrdf = new FetchFilterRdfXml();
//			ffrdf.setErrorHandler(eh);
//		}
		
		
		Seen seen = null;
		if (cmd.hasOption("ds")) {
			seen = new WrappingCallbackSeen(new HashSetSeen(),
					new CallbackNxAppender(new HopwiseSplittingFileOutputter(
							cmd.getOptionValue("ds"), true)));

//			String[] fNameExt = Util.determineFnameAndExtension(cmd.getOptionValue("ds"));
//			
//			CrawlerConstants.DUMP_SEEN_BASEFILENAME = fNameExt[0];
//			CrawlerConstants.DUMP_SEEN_FILE_EXTENSION = fNameExt[1];
//			
//			seen = new DiskWritingWrappingSeen(
//					new HashSetSeen(),
//					new File(
//							CrawlerConstants.DUMP_SEEN_BASEFILENAME
//									+ "-0"
//									+ (CrawlerConstants.DUMP_SEEN_FILE_EXTENSION
//											.equals("") ? ""
//											: "."
//													+ CrawlerConstants.DUMP_SEEN_FILE_EXTENSION)));
//			_log.info("Seen is a "
//					+ DiskWritingWrappingSeen.class.getSimpleName()
//					+ " with file "
//					+ ((DiskWritingWrappingSeen) seen).getFile());
		} else
			seen = new HashSetSeen();

		c.setErrorHandler(eh);
		c.setOutputCallback(sink);
		c.setLinkFilter(links);
//		if (!cmd.hasOption("ctIgnore"))
//			c.setFetchFilter(ffrdf);
		c.setBlacklistFilter(blacklist);
		if (cmd.hasOption("df")) {
			CrawlerConstants.DUMP_FRONTIER = true;
			CrawlerConstants.DUMP_FRONTIER_FILENAME = cmd.getOptionValue("df");
		}
		
		Redirects redirects;
		
		if (cmd.hasOption("dr"))
			redirects = new DummyRedirects();
		else
			redirects = new HashTableRedirects();

		if (cmd.hasOption("b")) {
			
			if (cmd.hasOption("resumebfc")) {
				String[] filenames = cmd.getOptionValues("resumebfc");
				String seenfilename = filenames[0];
				String redirectsfilename = filenames[1];
				_log.info("resuming breath first crawl with seen: "
						+ seenfilename + " and redirects: " + redirectsfilename
						+ " .");

				System.gc();
				// loading seen to be resumed from
				_log.info("loading seen");
				readFromThisFileIntoThisSeen(seenfilename, seen);
				_log.info("done loading seen");

				System.gc();
				// loading redirects file
				_log.info("loading redirects");
				readFromThisFileIntoThisRedirects(redirectsfilename, redirects);
				System.gc();
				_log.info("done loading redirects");
			}
			
			String[] vals = cmd.getOptionValues("b");

			int depth = Integer.parseInt(vals[0]);
			int maxuris = -1;
			int maxplds = -1;

			if (vals.length > 1) {
				maxuris = Integer.parseInt(vals[1]);
				if (vals.length > 2) {
					maxplds = Integer.parseInt(vals[2]);
				}
			}

			_log.info("breadth-first crawl with " + CrawlerConstants.NB_THREADS + " threads, depth " + depth + " maxuris " + maxuris + " maxplds " + maxplds + " minActivePlds " + cmd.getOptionValue("minpld", "unspecified"));

			c.evaluateBreadthFirst(frontier, seen, redirects, depth, maxuris, maxplds, Integer.parseInt(cmd.getOptionValue("minpld", "-1")), cmd.hasOption("mapseed") );
		} else if (cmd.hasOption("c")) {
			int maxuris = Integer.parseInt(cmd.getOptionValues("c")[0]);

			_log.info("load balanced crawl with " + CrawlerConstants.NB_THREADS + " threads, maxuris " + maxuris);

			c.evaluateLoadBalanced(frontier, seen, maxuris);
		} else if (cmd.hasOption("d")) {
			_log.info("sequential download with " + CrawlerConstants.NB_THREADS + " threads");
			ZipContentHandler zch = new ZipContentHandler(new File(cmd.getOptionValue("d")));
			c.setContentHandler(zch);

			c.evaluateSequential(frontier, seen);

			try {
				zch.close();
			} catch (IOException e) {
				_log.severe(e.getMessage());
			}
		}

		for (Iterator<ObjectThrowable> it = eh.iterator(); it.hasNext() ; ) {
			ObjectThrowable ot = it.next();
			System.err.println(ot.getThrowable().getMessage() + " " + ot.getObject());
		}

		System.err.println(eh);

		c.close();

		long time1 = System.currentTimeMillis();

		if (os != null) {
			try {
				os.close();
			} catch (IOException e) {
				_log.warning("could not close output stream: " + e.getMessage());
			}
		}

		if (rcb != null) {
			rcb.endDocument();
		}

//		if (cbData != null) {
//			cbData.endDocument();
//		}

		if (cbHeader != null) { 
			cbHeader.endDocument();
		}

		System.err.println("time elapsed " + (time1-time) + " ms " + (float)eh.lookups()/((time1-time)/1000.0) + " lookups/sec");
	}

	static void readFromThisFileIntoThisSeen(String seenfilename, Seen seen)
			throws FileNotFoundException, IOException {
		File inSeenFile = new File(seenfilename);

		InputStream is = inSeenFile.getAbsolutePath().endsWith(".gz") ? new GZIPInputStream(
				new FileInputStream(inSeenFile)) : new FileInputStream(
				inSeenFile);

		BufferedReader br = new BufferedReader(new InputStreamReader(is));
		
		NxParser nxp = new NxParser(br);

		int i = 0;

		for (Node[] nx : nxp) {
			if (nx.length > 1)
				throw new RuntimeException("Seen input had more than 1 field");
			try {
				seen.add(new URI(nx[0].toString()));
				++i;
			} catch (URISyntaxException e) {
				e.printStackTrace();
				_log.info("Dropping from seen: " + nx[0].toN3());
			}
		}

		_log.info("Read " + i + " URIs from " + seenfilename + " into Seen.");

		br.close();

	}

	static void readFromThisFileIntoThisRedirects(String redirectsfilename,
			Redirects redirects) throws FileNotFoundException, IOException {
		File inRedirectsFile = new File(redirectsfilename);

		InputStream is = inRedirectsFile.getAbsolutePath().endsWith(".gz") ? new GZIPInputStream(
				new FileInputStream(inRedirectsFile)) : new FileInputStream(
				inRedirectsFile);

		int i = 0;

		NxParser nxp = new NxParser(is);
		Resource from, to;
		for (Node[] nx : nxp) {
			if (nx.length > 2)
				throw new RuntimeException(
						"Redirects input had more than 2 fields");

			if (nx[0] instanceof Resource && nx[1] instanceof Resource) {
				from = (Resource) nx[0];
				to = (Resource) nx[1];

				if (from != null && to != null) {
					try {
						redirects.put(from.toURI(), to.toURI());
					} catch (URISyntaxException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					++i;
				} else
					_log.info("Dropping from redirects because of URI problems: "
							+ from + " to " + to);
			} else
				_log.info("Not all resources: redirect " + nx[0] + " to "
						+ nx[1]);

		}
		is.close();

		_log.info("Read " + i + " pairs from " + redirectsfilename
				+ " into Redirects.");

	}

	/**
	 * 
	 * @param q - queue
	 * @param seedList
	 * @throws IOException 
	 */
	static Iterable<URI> prepareSeedsIterable(File seedList) throws IOException {
		List<URI> seeds = new LinkedList<URI>();
		
		final Iterator<URI> it;

		if (seedList.getPath().endsWith(".nx.gz")) {
			InputStream is = new GZIPInputStream(new FileInputStream(seedList));
			it = new PleaseCloseTheDoorWhenYouLeaveIterator<URI>(
					new Node2uriConvertingIterator(new NxParser(is), 0), is);
		} else if (seedList.getPath().endsWith(".gz")) {
			BufferedReader br = new BufferedReader(new InputStreamReader(
					new GZIPInputStream(new FileInputStream(seedList))));
			it = new PleaseCloseTheDoorWhenYouLeaveIterator<URI>(
					new Util.StringToURIiterable(
							new Util.LineByLineIterable(br)).iterator(), br);
		} else if (seedList.getPath().endsWith(".nx")) {
			FileReader fr = new FileReader(seedList);
			it = new PleaseCloseTheDoorWhenYouLeaveIterator<URI>(
					new Node2uriConvertingIterator(new NxParser(fr), 0), fr);
		} else {
			FileReader fr = new FileReader(seedList);
			it = new PleaseCloseTheDoorWhenYouLeaveIterator<URI>(
					new Util.StringToURIiterable(new Util.LineByLineIterable(
							new BufferedReader(fr))).iterator(), fr);
		}
		
		return new Iterable<URI>() {
			public Iterator<URI> iterator() {
				return it;
			}
		};

//		int i = 0;
//
//		while (it.hasNext()) {
//			seeds.add(it.next());
//			++i;
//		}
//
//		_log.info("read " + i + " proper URIs from seed file");
//
//		return seeds;
	}
	
	
}
