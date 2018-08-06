package com.ontologycentral.ldspider.hooks.links;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import org.semanticweb.yars.nx.Node;

import com.ontologycentral.ldspider.frontier.Frontier;

/**
 * Add only uris with matching host to queue
 * Should be pld-level not host-level.
 * 
 * @author aharth
 *
 */
public class LinkFilterDomain extends LinkFilterDefault {
	final Logger _log = Logger.getLogger(this.getClass().getSimpleName());

	Set<String> _hosts;
	
	public LinkFilterDomain(Frontier f) {
		super(f);
		_hosts = new HashSet<String>();
	}
	
	public void addHost(String pld) {
		_hosts.add(pld);
	}

	public void startDocument() {
		;
	}
	
	public void endDocument() {
		;
	}
	
	/**
	 * Adds a new uri to the frontier.
	 */
	@Override
	protected void addUri(Node[] nx, int i) {
		try {
			URI u = new URI(nx[i].toString());
			if (_hosts.contains(u.getHost())) {
				_f.add(u);
				_log.fine("adding " + u + " to frontier");
			}
		} catch (URISyntaxException e) {
			_eh.handleError(null, e);
		}
	}
}
