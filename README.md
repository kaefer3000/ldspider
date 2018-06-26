# AGLDWG LDSpider
This is a fork of the [ldspider](https://github.com/ldspider/ldspider) Linked Data internet bot that is being tuned to crawl the 'Australian' Linked Data Web.

The following information, other than **Contacts** is from the original ldspider codebase.

## Contacts
**Grant Burgess**  
*Lead Developer*  
Griffith University Industrial Placement Student at CSIRO Land & Water  
<grantrburgess@outlook.com>

**Nicholas Car**  
*Product Owner*  
Senior Experimental Scientist  
CSIRO Land & Water  
<nicholas.car@csiro.au>


## Introduction ##

The **LDSpider** project provides a web crawling framework for the Linked Data web.

Requirements and challenges for crawling the Linked Data web are different from regular web crawling, thus the LDSpider project offers a web crawler adapted to traverse and harvest content from the Linked Data web.

Due to [Google's change to Google code](http://google-opensource.blogspot.de/2013/05/a-change-to-google-code-download-service.html), the downloads page cannot be maintained any more, so you have to browse the repository for both code and jars. Note that you can use maven with the google code repository. The groupId is `com.ontologycentral` and the artifactId `ldspider`.

The project is a co-operation between [Andreas Harth](http://harth.org/andreas/) at [AIFB](http://www.aifb.kit.edu/) and [Juergen Umbrich](http://umbrich.net) at [DERI](http://www.deri.ie/). [Aidan Hogan](http://sw.deri.org/~aidanh/), Tobias Kaefer and [Robert Isele](http://www.wiwiss.fu-berlin.de/en/institute/pwo/bizer/team/IseleRobert.html) are contributing.

Cite as
```
@inproceedings{ldspider,
author = { Robert Isele and J\"{u}rgen Umbrich and Chris Bizer and Andreas Harth},
title = { {LDSpider}: An open-source crawling framework for the Web of Linked Data} ,
year = { 2010 },
booktitle = { Proceedings of 9th International Semantic Web Conference (ISWC 2010) Posters and Demos},
url = { http://iswc2010.semanticweb.org/pdf/495.pdf }
}
```

## Features ##
  * **Content Handlers for different formats**:
    * Includes handlers to read RDF/XML, N-TRIPLES and N-QUADS;
    * [Any23](http://any23.apache.org/) handlers for other RDF serialisations, e.g. RDFa
    * Simple interface design to implement own handlers (e.g. to handle additional formats).
  * **Different crawling strategies**
    * Breadth-first crawl;
    * Depth-first crawl;
    * optionally crawl schema information (TBox).
  * **Crawling scope**
    * crawl can easily be restricted to specific pages e.g. pages with a specific domain prefix.
  * **Output formats** - The crawled data can be written in various ways:
    * The output can be written to files in different formats, such as RDF/XML or N-QUADS
    * The crawler can write all statements to a Triple Store using SPARQL/Update. Optionally uses named graphs to structure the written statements by their source page.
    * Optionally, the output include provenance information.

### Getting Started ###
**LDSpider** can be used in two ways:
  * Through a command line application. [Getting started (CLI)](https://github.com/ldspider/ldspider/wiki/GettingStartedCommandLine)
  * Through a flexible API, which provides various [Hooks](https://github.com/ldspider/ldspider/wiki/Hooks) to extend the behavior of the crawler. [Getting started (API)](https://github.com/ldspider/ldspider/wiki/GettingStartedAPI)

### Community ###
Sign up to the [LDSpider mailing list](http://groups.google.com/group/ldspider/) via the [web interface](http://groups.google.com/group/ldspider/subscribe/) or by emailing [mailto:ldspider+subscribe@googlegroups.com](mailto:ldspider+subscribe@googlegroups.com)

### Acknowledgements ###

![YourKit logo](https://www.yourkit.com/images/yklogo.png) YourKit supports open source projects with its full-featured Java Profiler.
YourKit, LLC is the creator of [YourKit Java Profiler](https://www.yourkit.com/java/profiler/index.jsp) and [YourKit .NET Profiler](https://www.yourkit.com/.net/profiler/index.jsp), innovative and intelligent tools for profiling Java and .NET applications. 

<!-- &lt;wiki:gadget url="http://www.ohloh.net/p/gwt/widgets/project\_users.xml" height="100" border="0" /&gt; -->

