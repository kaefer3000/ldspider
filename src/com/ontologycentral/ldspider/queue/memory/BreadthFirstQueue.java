package com.ontologycentral.ldspider.queue.memory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;

import com.ontologycentral.ldspider.CrawlerConstants;
import com.ontologycentral.ldspider.frontier.Frontier;
import com.ontologycentral.ldspider.queue.Redirects;
import com.ontologycentral.ldspider.queue.SpiderQueue;
import com.ontologycentral.ldspider.tld.TldManager;

public class BreadthFirstQueue extends SpiderQueue {
	Logger _log = Logger.getLogger(this.getClass().getName());

	Set<URI> _seen;
	
	Redirects _redirs;

	Map<String, Queue<URI>> _queues;
	Queue<String> _current;

	long _time;
	
	int _maxuris;

	public BreadthFirstQueue(TldManager tldm, int maxuris) {
		super(tldm);

		_maxuris = maxuris;

		_seen = Collections.synchronizedSet(new HashSet<URI>());
		_redirs = new Redirects();
		
		_current = new ConcurrentLinkedQueue<String>();
	}
	
	/**
	 * Put URIs from frontier to queue
	 * 
	 * @param maxuris - cut off number of uris per pld
	 */
	public synchronized void schedule(Frontier f) {	
		_log.info("start scheduling...");

		long time = System.currentTimeMillis();
		
		super.schedule(f);

		_queues = Collections.synchronizedMap(new HashMap<String, Queue<URI>>());

		Iterator<URI> it = f.iterator();
		while (it.hasNext()) {
			URI u = it.next();
			if (!checkSeen(u)) {
				addDirectly(u);
			}
			it.remove();
		}

		for (String pld : _queues.keySet()) {
			Queue<URI> q = _queues.get(pld);
			if (q.size() > _maxuris) {
				int n = 0;
				Queue<URI> nq = new ConcurrentLinkedQueue<URI>();
				for (URI u: q) {
					nq.add(u);
					n++;
					if (n >= _maxuris) {
						break;
					}
				}
				q = nq;
				
				_queues.put(pld, q);
			}
		}
		
		_current.addAll(_queues.keySet());
		
		_time = System.currentTimeMillis();
		
		_log.info("scheduling done in " + (_time - time) + " ms");
	}
	
	/**
	 * Poll a URI, one PLD after another.
	 * If queue turnaround is smaller than DELAY, wait for DELAY ms to
	 * avoid overloading servers.
	 * 
	 * @return URI
	 */
	public synchronized URI poll() {
		if (_current == null) {
			return null;
		}
		
		URI next = null;

		int empty = 0;

		do {	
			if (_current.isEmpty()) {
				// queue is empty, done for this round
				if (size() == 0) {
					return null;
				}
				
				long time1 = System.currentTimeMillis();
				
				if ((time1 - _time) < CrawlerConstants.MIN_DELAY) {
					try {
						_log.info("delaying queue " + CrawlerConstants.MIN_DELAY + " ms ...");
						Thread.sleep(CrawlerConstants.MIN_DELAY);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
				
				_log.info("queue turnaround in " + (time1-_time) + " ms");

				_time = System.currentTimeMillis();
				
				_current.addAll(_queues.keySet());
			}

			String pld = _current.poll();
			Queue<URI> q = _queues.get(pld);
			
			if (q != null && !q.isEmpty()) {
				next = q.poll();

//					// we have to do a in-queue check for seen if we put redirects directly back into the queue
//					if (checkSeen(to) == true) {
//						_log.info("redirect to " + to + " already seen");
//						next = null;
//					} else {	
//						next = to;
//					}
//				}
//				
//				// argh - if uri comes from file and a redirect, seen does not catch it
//				if (checkSeen(next) == true) {
//					_log.info("uri " + next + " already seen");
//					next = null;
//				}
				
				setSeen(next);
			} else {
				empty++;
			}
		} while (next == null && empty < _queues.size());

		return next;
	}
	
	/**
	 * Set the redirect.
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
		_redirsRound.add(to);
//			// fetch again, this time redirects are taken into account
//			addDirectly(from);
	}
	
	/**
	 * Add URI directly to queues.
	 * 
	 * @param u
	 */
	synchronized void addDirectly(URI u) {
		try {
			u = Frontier.normalise(u);
		} catch (URISyntaxException e) {
			_log.info(u +  " not parsable, skipping " + u);
			return;
		}

		String pld = _tldm.getPLD(u);
		if (pld != null) {	
			Queue<URI> q = _queues.get(pld);
			if (q == null) {
				q = new ConcurrentLinkedQueue<URI>();
				_queues.put(pld, q);
				_current.add(pld);
			}
			q.add(u);
		}
	}

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
	
	boolean checkSeen(URI u) {
		if (u == null) {
			throw new NullPointerException("u cannot be null");
		}
		
		return _seen.contains(u);
	}
	
	void setSeen(URI u) {
		if (u != null) {
			_seen.add(u);
			_seenRound.add(u);
		}
	}
	
	public int size() {
		int size = 0;
		
		for (Queue<URI> q : _queues.values()) {
			size += q.size();
		}
		
		return size;
	}
	
	public String toString() {
		StringBuilder sb = new StringBuilder();
		
		for (String pld : _queues.keySet()) {
			Queue<URI> q = _queues.get(pld);
			sb.append(pld);
			sb.append(": ");
			sb.append(q.size());
			sb.append("\n");
		}
		
		return sb.toString();
	}
}