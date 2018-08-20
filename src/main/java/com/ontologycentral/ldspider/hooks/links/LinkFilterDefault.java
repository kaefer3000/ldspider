package com.ontologycentral.ldspider.hooks.links;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.logging.Logger;

import org.semanticweb.yars.nx.Node;
import org.semanticweb.yars.nx.Nodes;
import org.semanticweb.yars.nx.Resource;
import org.semanticweb.yars.nx.namespace.RDF;

import com.ontologycentral.ldspider.CrawlerConstants;
import com.ontologycentral.ldspider.frontier.Frontier;
import com.ontologycentral.ldspider.hooks.error.ErrorHandler;

/**
 * Default Link Filter
 * Configurable to follow all ABox and/or TBox links.
 * 
 * @author aharth
 * @author RobertIsele
 *
 */
public class LinkFilterDefault implements LinkFilter {
	private final Logger _log = Logger.getLogger(this.getClass().getSimpleName());

	protected final Frontier _f;
	protected ErrorHandler _eh;
	
	protected boolean _followABox;
	protected boolean _followTBox;
		
	public LinkFilterDefault(Frontier f) {
		_f = f;
		_followABox = true;
		_followTBox = true;
	}
	
	public void setErrorHandler(ErrorHandler eh) {
		_eh = eh;
	}
	
	public void setFollowABox(boolean follow) {
		_followABox = follow;
	}

	public void setFollowTBox(boolean follow) {
		_followTBox = follow;
	}

	public void startDocument() {
		;
	}
	
	public void endDocument() {
		_log.info("document done");
	}

	public synchronized void processStatement(Node[] nx) {
		_log.fine("seeing " + Nodes.toN3(nx));
		for (int i = 0; i < Math.min(nx.length, 3); i++) {
			if (nx[i] instanceof Resource) {
				addUri(nx, i);
				//Subject
				if(i == 0 && _followABox) {
					if(_followABox) addABox(nx, i);
				}
				//Predicate
				else if(i == 1) {
					if(_followTBox) addTBox(nx, i);
				}
				//Object (TBox)
				else if(i == 2 && nx[1].equals(RDF.TYPE)) {
					if(_followTBox) addTBox(nx, i);
				}
				//Object (ABox)
				else if(i == 2) {
					if(_followABox) addABox(nx, i);
				}
			}
		}
	}
	
	/**
	 * Adds a new ABox node.
	 * Override this in sub classes to modify ABox handling.
	 * 
	 */
	protected synchronized void addABox(Node[] nx, int i) {
		addUri(nx, i);
	}
	
	/**
	 * Adds a new TBox node.
	 * Override this in sub classes to modify TBox handling.
	 * 
	 */
	protected synchronized void addTBox(Node[] nx, int i) {
		addUri(nx, i);
	}
	
	/**
	 * Adds a new uri to the frontier.
	 */
	protected synchronized void addUri(Node[] nx, int i) {
		try {
			_log.fine("adduri " + nx[i].toString());
			try {
				URI u = new URI(nx[i].toString());

				// @@@ HACK to throw out non-RDF sites early
				boolean add = true;
				for (String s : CrawlerConstants.SITES_NO_RDF) {
					if (u.getHost() != null && u.getHost().contains(s)) {
						add = false;
					}
				}
				if (add) {
					_f.add(u);
					// saves normalisations:
					boolean presumablyDiscardedWhileAdding = false;
					try {
						Frontier.normalise(u);
					} catch (URISyntaxException e) {
						presumablyDiscardedWhileAdding = true;
					}
					if (!presumablyDiscardedWhileAdding) {
						_log.fine("adding " + nx[i].toString() + " to frontier");
						_eh.handleLink(nx[nx.length - 1], nx[i]);
					}
				}
			} catch (URISyntaxException e) {
				if (_eh != null) {
					try {
						_eh.handleError(new URI(nx[nx.length-1].toString()), e);
					} catch (URISyntaxException e1) {
						System.err.println(e1.getMessage());
						e1.printStackTrace();
					}
				}
			}
		} catch (NullPointerException e) {
			System.err.println(e.getMessage());
			e.printStackTrace();
		}
	}
}
