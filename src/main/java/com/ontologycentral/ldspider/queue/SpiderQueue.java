package com.ontologycentral.ldspider.queue;

import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.logging.Logger;

import org.semanticweb.yars.tld.TldManager;
import org.semanticweb.yars.util.LRUMapCache;

import com.ontologycentral.ldspider.CrawlerConstants;
import com.ontologycentral.ldspider.frontier.Frontier;
import com.ontologycentral.ldspider.seen.Seen;

public abstract class SpiderQueue implements Serializable{
	private static final long serialVersionUID = 1L;
	private static final Logger _log = Logger.getLogger(SpiderQueue.class.getName());

	public abstract URI poll();
	public abstract int size();
	
	protected Seen _seen;
	
	LRUMapCache<URI, Integer> _redirsCache = new LRUMapCache<URI, Integer>(2 * CrawlerConstants.NB_THREADS);

	protected TldManager _tldm;
	protected Redirects _redirs;
	
	public SpiderQueue(TldManager tldm, Redirects redirs, Seen seen) {
		_tldm = tldm;
		_redirs = redirs;
		_seen = seen;
	}
	
	/**
	 * Schedule URIs in Frontier (i.e. put URIs in Frontier into the queue for the next round)
	 */
	public abstract void schedule(Frontier f);
	
	/**
	 * Set a redirect (303)
	 * @param from
	 * @param to
	 * @param status
	 */
	public void setRedirect(URI from, URI to, int status) {
		try {
			to = Frontier.normalise(to);
		} catch (URISyntaxException e) {
			_log.info(to +  " not parsable, skipping " + to);
			return;
		}
		
		if (from.equals(to)) {
			_log.info("redirected to same uri " + from);
			return;
		}
		
		_redirs.put(from, to);
		
		Integer i = null;
		if ((i = _redirsCache.get(from)) != null) {
			_redirsCache.remove(from);
			_redirsCache.put(to, i = Integer.valueOf(i.intValue() + 1));
		} else {
			_redirsCache.put(to, i = Integer.valueOf(0));
		}
		
		if (i.intValue() >= CrawlerConstants.MAX_REDIRECTS) {
			_log.info("Too many redirects on path to: " + to + " ; previous on path: " + from + " .");
			return;
		}
		
		if (checkSeen(to) == false) {
			_log.info("adding " + to + " directly to queue");
			addRedirect(to);
		}
	}

	/** Add URI to queue.
	 * 
	 * @param u the URI to add
	 */
	void add(URI u) {
		add(u, false);
	}

	/**
	 * Add URI to queue.
	 * 
	 * @param u the URI
	 * @param uriHasAlreadyBeenProcessed
	 *            if the URI has already been frontier.normalise()d or
	 *            frontier.process()ed.
	 */
	abstract void add(URI u, boolean uriHasAlreadyBeenProcessed);

	/**
	 * On redirection, this method is called with the end of the redirect.
	 * @param u the end of the redirect
	 */
	abstract void addRedirect(URI u);

	/**
	 * Return redirected URI (if there's a redirect)
	 * otherwise return original URI.
	 * 
	 * @param from
	 * @return
	 */
	URI obtainRedirect(URI from) {
		URI to = _redirs.getRedirect(from);
		if (from != to) {
			_log.info("redir from " + from + " to " + to);
			return to;
		}
		
		return from;
	}
	
	public Redirects getRedirects() {
		return _redirs;
	}

	public void setRedirects(Redirects redirs) {
		_redirs = redirs;		
	}

	/**
	 * Set the URI supplied as seen, i.e. already visited.
	 * @param u the URI
	 */
	public void addSeen(URI u) {
		if (u != null)
			_seen.add(u);
	}
	
	/**
	 * Checks if a given URI has been seen.
	 * @param u the URI
	 * @return true if it already has been visited, false otherwise
	 */
	public boolean checkSeen(URI u) {
		if (u == null) {
			throw new NullPointerException("u cannot be null");
		}
		
		return _seen.hasBeenSeen(u);
	}
	
	/**
	 * See {@link #addSeen(URI)}.
	 * @param u
	 */
	void setSeen(URI u) {
		addSeen(u);
	}

	/**
	 * Setter for the Seen instance to use. 
	 * @param seen
	 */
	public void setSeen(Seen seen) {
		_seen = seen;
	}

	/**
	 * Getter for the Seen instance of this queue.
	 * @return _seen
	 */
	public Seen getSeen() {
		return _seen;
	}
}
