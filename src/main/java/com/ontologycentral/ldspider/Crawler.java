package com.ontologycentral.ldspider;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.semanticweb.yars.nx.parser.Callback;
import org.semanticweb.yars.tld.TldManager;

import com.ontologycentral.ldspider.frontier.Frontier;
import com.ontologycentral.ldspider.hooks.content.ContentHandler;
import com.ontologycentral.ldspider.hooks.content.ContentHandlerRdfXml;
import com.ontologycentral.ldspider.hooks.error.ErrorHandler;
import com.ontologycentral.ldspider.hooks.error.ErrorHandlerDummy;
import com.ontologycentral.ldspider.hooks.fetch.FetchFilter;
import com.ontologycentral.ldspider.hooks.fetch.FetchFilterAllow;
import com.ontologycentral.ldspider.hooks.links.LinkFilter;
import com.ontologycentral.ldspider.hooks.links.LinkFilterDefault;
import com.ontologycentral.ldspider.hooks.sink.LastReporter;
import com.ontologycentral.ldspider.hooks.sink.Sink;
import com.ontologycentral.ldspider.hooks.sink.SinkCallback;
import com.ontologycentral.ldspider.hooks.sink.SinkDummy;
import com.ontologycentral.ldspider.hooks.sink.SpyingSinkCallback;
import com.ontologycentral.ldspider.hooks.sink.TakingHopsIntoAccount;
import com.ontologycentral.ldspider.http.ConnectionManager;
import com.ontologycentral.ldspider.http.LookupThread;
import com.ontologycentral.ldspider.http.robot.Robots;
import com.ontologycentral.ldspider.queue.BreadthFirstQueue;
import com.ontologycentral.ldspider.queue.DiskBreadthFirstQueue;
import com.ontologycentral.ldspider.queue.DummyRedirects;
import com.ontologycentral.ldspider.queue.LoadBalancingQueue;
import com.ontologycentral.ldspider.queue.Redirects;
import com.ontologycentral.ldspider.queue.SpiderQueue;
import com.ontologycentral.ldspider.seen.Seen;

public class Crawler {
	Logger _log = Logger.getLogger(this.getClass().getName());

	ContentHandler _contentHandler;
	Sink _output;
	LinkFilter _links;
	ErrorHandler _eh;
	FetchFilter _ff, _blacklist;
	ConnectionManager _cm;
	
	Class<? extends Redirects> _redirsClass;
	
	Robots _robots;
//	Sitemaps _sitemaps;
	
	TldManager _tldm;

	SpiderQueue _queue = null;
	
	int _threads;
	
	/**
	 * The Crawling mode.
	 * Defines whether ABox and/or TBox links are followed and whether an extra TBox round is done.
	 */
	public enum Mode
	{
		/** Only crawl ABox statements */
		ABOX_ONLY(true, false, false),
		/** Only crawl TBox statements */
		TBOX_ONLY(false, true, false),
		/** Crawl ABox and TBox statements */
		ABOX_AND_TBOX(true, true, false),
		/** Crawl ABox and TBox statements and do an extra round to get the TBox of the statements retrieved in the final round */
		ABOX_AND_TBOX_EXTRAROUND(true, true, true);
		
		private boolean aBox;
		private boolean tBox;
		private boolean extraRound;
	
	    private Mode(boolean aBox, boolean tBox, boolean extraRound) {
	    	this.aBox = aBox;
	    	this.tBox = tBox;
	    	this.extraRound = extraRound;
		}

		public boolean followABox() {
			return aBox;
		}

		public boolean followTBox() {
			return tBox;
		}

		public boolean doExtraRound() {
			return extraRound;
		}
	}
	
	public Crawler() {
		this(CrawlerConstants.DEFAULT_NB_THREADS);
	}
	public Crawler(int threads) {
		this(threads,null,null,null,null);
	}
	
	/**
	 * 
	 * @param threads
	 * @param proxyHost - the proxy host or <code>null</code> to use System.getProperties().get("http.proxyHost")
	 * @param proxyPort - the proxy port or <code>null</code> to use System.getProperties().get("http.proxyPort")
	*/
	public Crawler(int threads,String proxyHost, String proxyPort){
		this(threads,proxyHost,proxyPort,null,null);
	}
	
	/**
	 * 
	 * @param threads
	 * @param proxyHost - the proxy host or <code>null</code> to use System.getProperties().get("http.proxyHost")
	 * @param proxyPort - the proxy port or <code>null</code> to use System.getProperties().get("http.proxyPort")
	 * @param proxyUser - the proxy user or <code>null</code> to use System.getProperties().get("http.proxyUser")
	 * @param proxyPassword - the proxy user password or <code>null</code> to use System.getProperties().get("http.proxyPassword")
	 */
	public Crawler(int threads,String proxyHost, String proxyPort, String proxyUser, String proxyPassword) {
		_threads = threads;
		
		String phost = proxyHost;
		int pport = 0;
		if(proxyPort!=null){
			try{
				pport = Integer.parseInt(proxyPort);
			}catch(NumberFormatException nfe){
				pport = 0;
			}
		}
		String puser = proxyUser;
		String ppassword = proxyPassword;
		
		
		if (phost == null && System.getProperties().get("http.proxyHost") != null) {
			phost = System.getProperties().get("http.proxyHost").toString();
		}
		if (pport==0 && System.getProperties().get("http.proxyPort") != null) {
			pport = Integer.parseInt(System.getProperties().get("http.proxyPort").toString());
		}
		
		if (puser == null && System.getProperties().get("http.proxyUser") != null) {
			puser = System.getProperties().get("http.proxyUser").toString();
		}
		if (ppassword == null && System.getProperties().get("http.proxyPassword") != null) {
			ppassword = System.getProperties().get("http.proxyPassword").toString();
		}
		
		_cm = new ConnectionManager(phost, pport, puser, ppassword, threads
				* CrawlerConstants.MAX_CONNECTIONS_PER_THREAD);
		_cm.setRetries(CrawlerConstants.RETRIES);

		// Always use the local TldManager implementation. Changed for the one
		// from NxParser for two reasons:
		// * I fixed a couple of bugs there
		// * I updated the Public suffix list and made a change to it to support
		//   .asia
		// try {
		// _tldm = new TldManager(_cm);
		// } catch (Exception e) {
		// _log.info("cannot get tld file online " + e.getMessage());
		// try {
		// _tldm = new TldManager();
		// } catch (IOException e1) {
		// _log.info("cannot get tld file locally " + e.getMessage());
		// }
		// }

		try {
			_tldm = new TldManager();
		} catch (IOException e1) {
			_log.info("cannot get tld file locally " + e1.getMessage());
		}
	    
		_eh = new ErrorHandlerDummy();

	    _robots = new Robots(_cm);
	    _robots.setErrorHandler(_eh);
	    
//	    _sitemaps = new Sitemaps(_cm);
//	    _sitemaps.setErrorHandler(_eh);
	    
	    _contentHandler = new ContentHandlerRdfXml();
	    _output = new SinkDummy();
		_ff = new FetchFilterAllow();
		
		_blacklist = new FetchFilterAllow();
	}
	
	public void setContentHandler(ContentHandler h) {
		_contentHandler = h;
	}
	
	public void setFetchFilter(FetchFilter ff) {
		_ff = ff;
	}

	public void setBlacklistFilter(FetchFilter blacklist) {
		_blacklist = blacklist;
	}

	public void setErrorHandler(ErrorHandler eh) {
		_eh = eh;
		
		if (_robots != null) {
			_robots.setErrorHandler(eh);
		}
//		if (_sitemaps != null) {
//			_sitemaps.setErrorHandler(eh);
//		}
		if (_links != null) {
			_links.setErrorHandler(eh);
		}
	}
	
	public void setOutputCallback(Callback cb) {
		setOutputCallback(new SinkCallback(cb));
	}
	
	public void setOutputCallback(Sink sink) {
		if (sink instanceof LastReporter)
			_output = sink;
		else
			_output = new SpyingSinkCallback(sink);
	}
	
	public void setLinkFilter(LinkFilter links) {
		_links = links;
	}
	
	public void evaluateBreadthFirst(Frontier frontier, Seen seen, Redirects redirects, int depth, int maxuris, int maxplds, int minActPlds, boolean minActPldsAlready4Seedlist, Mode crawlingMode) {
		Redirects r = redirects;
		if (_queue != null)
			r = _queue.getRedirects();
		if (_queue == null || !(_queue instanceof BreadthFirstQueue || _queue instanceof DiskBreadthFirstQueue)) {
			if (CrawlerConstants.BREADTHFIRSTQUEUE_ONDISK)
				_queue = new DiskBreadthFirstQueue(_tldm, r, seen, minActPlds, minActPldsAlready4Seedlist);
			else
				_queue = new BreadthFirstQueue(_tldm, r, seen, maxuris, maxplds,
						minActPlds, minActPldsAlready4Seedlist);
		} else {
			Seen tempseen = _queue.getSeen();
			_queue = new BreadthFirstQueue(_tldm, r, seen, maxuris, maxplds, minActPlds, minActPldsAlready4Seedlist);
			_queue.setRedirects(r);
			_queue.setSeen(tempseen);
		}
		
		if (_links == null) {
			_links = new LinkFilterDefault(frontier);
		}
		
		_queue.schedule(frontier);
		
		_links.setFollowABox(crawlingMode.followABox());
		_links.setFollowTBox(crawlingMode.followTBox());
		
		_log.info(_queue.toString());
	
		int rounds = crawlingMode.doExtraRound() ? depth + 1 : depth;
		for (int curRound = 0; (curRound <= rounds)
				&& (CrawlerConstants.URI_LIMIT_ENABLED ? (LookupThread
				.getOverall200FetchesWithNonEmptyRDF() < CrawlerConstants.URI_LIMIT_WITH_NON_EMPTY_RDF)
				: true); curRound++) {
			List<Thread> ts = new ArrayList<Thread>();
	
			//Extra round to get TBox
			if(curRound == depth) {
				_links.setFollowABox(false);
				_links.setFollowTBox(true);
			}
	
			for (int j = 0; j < _threads; j++) {
				LookupThread lt = new LookupThread(_cm, _queue, _contentHandler, _output, _links, _robots, _eh, _ff, _blacklist, j);
				ts.add(lt); //new Thread(lt,"LookupThread-"+j));		
			}
	
			_log.info("Starting threads round " + curRound + " with " + _queue.size() + " uris");
			
			Monitor m = new Monitor(ts, System.err, 1000*10);
			m.start();
	
			for (Thread t : ts) {
				t.start();
			}
	
			for (Thread t : ts) {
				try {
					t.join();
				} catch (InterruptedException e1) {
					_log.info(e1.getMessage());
					//e1.printStackTrace();
				}
			}
			
			m.shutdown();
			
			_log.info("ROUND " + curRound + " DONE with " + _queue.size() + " uris remaining in queue");
			_log.fine("old queue: \n" + _queue.toString());
	
			if (_output instanceof LastReporter)
				_log.info("Last non-empty context of this hop (# " + curRound
						+ " ): " + ((LastReporter) _output).whoWasLast());
			
			if (CrawlerConstants.SPLIT_HOPWISE) {

				for (TakingHopsIntoAccount thia : CrawlerConstants.THOSE_WHO_TAKE_HOPS_INTO_ACCOUNT) {
					try {
						thia.nextHop(curRound + 1);
					} catch (Exception e) {
						e.printStackTrace();
					}

				}
			}

			_queue.schedule(frontier);
	
			_eh.handleNextRound();
	
			_log.fine("new queue: \n" + _queue.toString());
		}
	}
	public void evaluateBreadthFirst(Frontier frontier, Seen seen, Redirects redirects, int depth, int maxuris, int maxplds, int minActPlds, boolean minActPldsAlready4Seedlist) {
		evaluateBreadthFirst(frontier, seen, redirects, depth, maxuris, maxplds, minActPlds, minActPldsAlready4Seedlist, Mode.ABOX_AND_TBOX);
	}

	public void evaluateLoadBalanced(Frontier frontier, Seen seen, int maxuris) {
		if (_queue == null || !(_queue instanceof LoadBalancingQueue)) {
			Redirects r = null;
			if (_queue != null)
				r = _queue.getRedirects();
			if (r == null)
				try {
					r = _redirsClass.newInstance();
				} catch (InstantiationException e) {
					_log.info("InstantiationException. Using dummy.");
					r = new DummyRedirects();
				} catch (IllegalAccessException e) {
					_log.info("IllegalAccessException. Using dummy.");
					r = new DummyRedirects();
				} catch (java.lang.NullPointerException e) {
					_log.info("NullPointerException. Using dummy.");
					r = new DummyRedirects();
				}
			_queue = new LoadBalancingQueue(_tldm, r, seen);
		} else {
			Redirects r = _queue.getRedirects();
			seen = _queue.getSeen();
			_queue = new LoadBalancingQueue(_tldm, r, seen);
			_queue.setSeen(seen);
		}

		if (_links == null) {
			_links = new LinkFilterDefault(frontier);
		}
		
		_queue.schedule(frontier);
		
		_log.fine(_queue.toString());

		int i = 0;
		int uris = 0;
		
		while (uris < maxuris && _queue.size() > 0) {
			int size = _queue.size();
			
			List<Thread> ts = new ArrayList<Thread>();

			for (int j = 0; j < _threads; j++) {
				LookupThread lt = new LookupThread(_cm, _queue, _contentHandler, _output, _links, _robots, _eh, _ff, _blacklist, j);
				ts.add(lt); //new Thread(lt,"LookupThread-"+j));		
			}

			_log.info("Starting threads round " + i++ + " with " + _queue.size() + " uris");
			
			for (Thread t : ts) {
				t.start();
			}
			
			Monitor m = new Monitor(ts, System.err, 1000*10);
			m.start();

			for (Thread t : ts) {
				try {
					t.join();
				} catch (InterruptedException e1) {
					_log.info(e1.getMessage());
					//e1.printStackTrace();
				}
			}
			
			m.shutdown();
			
			uris += size - _queue.size();
			
			_log.info("ROUND " + i + " DONE with " + _queue.size() + " uris remaining in queue");
			_log.fine("old queue: \n" + _queue.toString());

			_log.fine("frontier" + frontier);
			
			_queue.schedule(frontier);

			_log.info("new queue: \n" + _queue.toString());
		}
	}
	
	public void evaluateSequential(Frontier frontier, Seen seen) {
		Redirects r = null;
		try {
			r = _redirsClass.newInstance();
		} catch (InstantiationException e) {
			_log.info("InstantiationException. Using dummy.");
			r = new DummyRedirects();
		} catch (IllegalAccessException e) {
			_log.info("IllegalAccessException. Using dummy.");
			r = new DummyRedirects();
		} catch (NullPointerException e) {
			_log.info("NullPointerException. Using dummy.");
			r = new DummyRedirects();
		}

		_queue = new BreadthFirstQueue(_tldm, r, seen, Integer.MAX_VALUE, Integer.MAX_VALUE, -1, false);
		_queue.schedule(frontier);

		
		_log.info(_queue.toString());
		
		int i = 0;
		
		while (_queue.size() > 0 && i <= CrawlerConstants.MAX_REDIRECTS) {
			List<Thread> ts = new ArrayList<Thread>();

			for (int j = 0; j < _threads; j++) {
				LookupThread lt = new LookupThread(_cm, _queue, _contentHandler, _output, _links, _robots, _eh, _ff, _blacklist, j);
				ts.add(lt); //new Thread(lt,"LookupThread-"+j));		
			}

			_log.info("Starting threads round " + i++ + " with " + _queue.size() + " uris");

			for (Thread t : ts) {
				t.start();
			}

			Monitor m = new Monitor(ts, System.err, 1000*10);
			m.start();

			for (Thread t : ts) {
				try {
					t.join();
				} catch (InterruptedException e1) {
					_log.info(e1.getMessage());
					//e1.printStackTrace();
				}
			}
			
			m.shutdown();

			_queue.schedule(frontier);
			
			_log.info("ROUND " + i + " DONE with " + _queue.size() + " uris remaining in queue");

			_log.info("new queue: \n" + _queue.toString());
		}
		
		_log.info("DONE with " + _queue.size() + " uris remaining in queue");
	}
	
	public void run(SpiderQueue queue){
		List<Thread> ts = new ArrayList<Thread>();

		for (int j = 0; j < _threads; j++) {
			LookupThread lt = new LookupThread(_cm, queue, _contentHandler, _output, _links, _robots, _eh, _ff, _blacklist, j);
			ts.add(lt); //new Thread(lt,"LookupThread-"+j));		
		}

		
		for (Thread t : ts) {
			t.start();
		}

		for (Thread t : ts) {
			try {
				t.join();
			} catch (InterruptedException e1) {
				_log.info(e1.getMessage());
				//e1.printStackTrace();
			}
		}
	}
	
	/**
	 * Set the spider queue
	 * @param queue
	 */
	public void setQueue(final SpiderQueue queue){
		_queue = queue;
	}
	/**
	 * 
	 * @return - the current used {@link SpiderQueue}
	 */
	public SpiderQueue getQueue(){
		return _queue;
	}


	public TldManager getTldManager(){
		return _tldm;
	}
	public Class<? extends Redirects> get_redirsClass() {
		return _redirsClass;
	}
	public void setRedirsClass(Class<? extends Redirects> _redirsClass) {
		this._redirsClass = _redirsClass;
	}
	public void close() {
		_cm.shutdown();
		_eh.close();
	}
}
