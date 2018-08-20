package com.ontologycentral.ldspider.queue;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;

import org.semanticweb.yars.tld.TldManager;

import com.ontologycentral.ldspider.CrawlerConstants;
import com.ontologycentral.ldspider.frontier.Frontier;
import com.ontologycentral.ldspider.seen.Seen;

public class LoadBalancingQueue extends RedirectsFavouringSpiderQueue {
	private static final long serialVersionUID = 1L;

	private static final  Logger _log = Logger.getLogger(LoadBalancingQueue.class.getName());


	Map<String, Queue<URI>> _queues;
	Queue<String> _current;

	long _mindelay, _maxdelay;
	long _mintime, _maxtime;
	
	int _depth = 0;
	
	static Queue<String> POISON = new ConcurrentLinkedQueue<String>();
	
	public LoadBalancingQueue(TldManager tldm, Redirects r, Seen seen) {
		super(tldm, r, seen);

		_current = new ConcurrentLinkedQueue<String>();
		
		_mindelay = CrawlerConstants.MIN_DELAY;
		_maxdelay = CrawlerConstants.MAX_DELAY;
	}
	
	public void setMinDelay(int delay) {
		_mindelay = delay;
	}
	
	public void setMaxDelay(int delay) {
		_maxdelay = delay;
	}
	
	/**
	 * Put URIs from frontier to queue
	 * 
	 * cut off number of uris per pld
	 */
	public synchronized void schedule(Frontier f) {	
		_log.info("start scheduling depth " + _depth++ + "...");

		long time = System.currentTimeMillis();

//		super.schedule(f);

		_queues = Collections.synchronizedMap(new HashMap<String, Queue<URI>>());
		
		Iterator<URI> it = f.iterator();
		
		while (it.hasNext()) {
			URI u = it.next();
			if (!checkSeen(u)) {
				add(u);
			}
			it.remove();
			//f.remove(u);
		}
	
		_current.addAll(getSortedQueuePlds());
		
		_mintime = _maxtime = System.currentTimeMillis();
		
		_log.info("scheduling depth " + _depth + " with " + size() + " uris and " + getSortedQueuePlds().size() + " plds done in " + (_mintime - time) + " ms");
	}
	
//	/**
//	 * Add URI to frontier
//	 * 
//	 * @param u
//	 */
//	public boolean addFrontier(URI u) {
//		if (super.addFrontier(u) == true) {
//			_frontier.add(u);
//			return true;
//		}
//		
//		return false;
//	}
	
	/**
	 * Add URI directly to queues.
	 * 
	 * @param u
	 */
	public synchronized void add(URI u, boolean uriHasBeenProcessed) {
		if (!uriHasBeenProcessed) {
			try {
				u = Frontier.normalise(u);
			} catch (URISyntaxException e) {
				_log.info(u + " not parsable, skipping " + u);
				return;
			}
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
	 * Poll a URI, one PLD after another.
	 * If queue turnaround is smaller than DELAY, wait for DELAY ms to
	 * avoid overloading servers.
	 * 
	 * @return URI
	 */
	protected synchronized URI pollInternal() {
		if (_current == null) {
			return null;
		}
		
		long time = System.currentTimeMillis();

		URI next = null;

		int empty = 0;

		do {
			long time1 = System.currentTimeMillis();

			if (_current.isEmpty()) {
				// queue is empty, done for this round
				if (size() == 0) {
					_log.info("queue size is 0: " + toString());
					return null;
				}
				if (_current == POISON) {
					return null;
				}
		
				if ((time1 - _mintime) < _mindelay) {
					_log.info("fetching plds too fast, rescheduling, remaining uris in queue " + size());
					_log.info(toString());
					_current = POISON;
					return null;
				}
				
				_log.info("queue turnaround in " + (time1-_mintime) + " ms");

				_mintime = _maxtime = System.currentTimeMillis();
				
				_current.addAll(getSortedQueuePlds());
			} else if ((time1 - _maxtime) > _maxdelay) {
				_log.info("skipped to start of queue in " + (time1-_maxtime) + " ms, queue size " + size());

				_maxtime = System.currentTimeMillis();
				
				_current = new ConcurrentLinkedQueue<String>();
				_current.addAll(getSortedQueuePlds());				
			}

			String pld = _current.poll();
			Queue<URI> q = _queues.get(pld);
			
			if (q != null && !q.isEmpty()) {
				next = q.poll();

				setSeen(next);
			} else {
				empty++;
			}
		} while (next == null && empty < _queues.size());
		
		long time1 = System.currentTimeMillis();
		
		_log.info("poll for " + next + " done in " + (time1 - time) + " ms");

		return next;
	}
	
	List<String> getSortedQueuePlds() {
		List<String> li = new ArrayList<String>();
		
		li.addAll(_queues.keySet());
		
		Collections.sort(li, new PldCountComparator(_queues));
		
		return li;
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
		
		sb.append("no of plds ");
		sb.append(_queues.size());
		sb.append("\n");
		
		for (String pld : getSortedQueuePlds()) {
			Queue<URI> q = _queues.get(pld);
			sb.append(pld);
			sb.append(": ");
			sb.append(q.size());
			sb.append("\n");
		}
		
		return sb.toString();
	}
}

class PldCountComparator implements Comparator<String> {
	Map<String, Queue<URI>> _map;
	
	public PldCountComparator(Map<String, Queue<URI>> map) {
		_map = map;
	}
	
	public int compare(String arg0, String arg1) {
		return _map.get(arg1).size() - _map.get(arg0).size();
	}
}
