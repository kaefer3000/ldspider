package com.ontologycentral.ldspider.hooks.content;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.IOUtils;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.input.BOMInputStream;
import org.semanticweb.yars.nx.Resource;
import org.semanticweb.yars.nx.parser.Callback;
import org.semanticweb.yars.nx.parser.ParseException;
import org.semanticweb.yars.nx.util.NxUtil;
import org.semanticweb.yars2.rdfxml.RDFXMLParser;

/**
 * Handles RDF/XML documents.
 * 
 * @author RobertIsele
 */
public class ContentHandlerRdfXml implements ContentHandler {

	private final Logger _log = Logger.getLogger(this.getClass().getName());
	
	public static final String[] MIMETYPES = { "application/rdf+xml", "application/xml" };
	
	public boolean canHandle(String mime) {
		for (String ct : MIMETYPES) {
			if (mime.contains(ct)) {
				return true;
			}
		}
		return false;
	}

	public boolean handle(URI uri, String mime, InputStream source, Callback callback) {
		try {
//			String result = IOUtils.toString(source, StandardCharsets.UTF_8);	
//			_log.log(Level.INFO, "Stream dump", result);
//			System.out.println(result);
//			System.out.println("End result");			
//			BOMInputStream bis = new BOMInputStream(source);
//			if (bis.hasBOM()) {
//				bis.skip(bis.getBOM().length()-2);
//			}
			RDFXMLParser r = new RDFXMLParser(source, true, true, uri.toString(), callback, new Resource(NxUtil.escapeForNx(uri.toString())));
//			RDFXMLParser r = new RDFXMLParser(source, true, false, uri.toString());
//			while (r.hasNext()) {
//				Node[] nx = r.next();
//				
//				callback.processStatement(nx);
//				
//				_log.info("processing " + Nodes.toN3(nx));
//			}
			//, callback, new Resource(uri.toString()))
			
			return true;
		} catch (ParseException e) {
			_log.log(Level.INFO, "Could not parse document", e);
			return false;
		} catch (IOException e) {
			_log.log(Level.WARNING, "Could not read document", e);
			return false;
//		} catch (NullPointerException e) {
//			_log.log(Level.WARNING, "Could not read document", e);
//			return false;
		}
	}
	
	public String[] getMimeTypes() {
		return MIMETYPES;
	}

}
