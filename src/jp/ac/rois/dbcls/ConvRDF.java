/*
 * This code is derived from arq.examples.riot.ExRIOT_6, which is 
 * distributed under the Apache License, Version 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Database Center for Life Science (DBCLS) has developed this code
 * and releases it under MIT style license. 
 */

package jp.ac.rois.dbcls;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.RDFParserBuilder;
import org.apache.jena.riot.RiotException;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.riot.RiotNotFoundException;
import org.apache.jena.riot.RiotParseException;
import org.apache.jena.riot.lang.PipedRDFIterator;
import org.apache.jena.riot.lang.PipedRDFStream;
import org.apache.jena.riot.lang.PipedTriplesStream;
import org.apache.jena.riot.system.ErrorHandlerFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.graph.Graph;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.jena.atlas.logging.LogCtl;
import org.apache.jena.graph.Factory;
import org.apache.jena.riot.RDFLanguages;

public class ConvRDF {

	static { LogCtl.setCmdLogging(); }

	private static void issuer(BufferedInputStream reader, Lang lang) {
		final int interval = 10000;
		final int buffersize = 100000;
		final int pollTimeout = 300; // Poll timeout in milliseconds
		final int maxPolls = 1000;   // Max poll attempts

		PipedRDFIterator<Triple> iter = new PipedRDFIterator<Triple>(buffersize, false, pollTimeout, maxPolls);
		final PipedRDFStream<Triple> inputStream = new PipedTriplesStream(iter);

		ExecutorService executor = Executors.newSingleThreadExecutor();

		Runnable parser = new Runnable() {
			@Override
			public void run() {
				RDFParser parser_object = RDFParserBuilder
				.create()
				.errorHandler(ErrorHandlerFactory.errorHandlerDetailed())
				.source(reader)
				.checking(true)
				.lang(lang)
				.build();
				try{
					parser_object.parse(inputStream);
				}
				catch (RiotParseException e){
					String location = "";
					if(e.getLine() >= 0 && e.getCol() >= 0)
						location = " at the line: " + e.getLine() + " and the column: " + e.getCol();
					System.err.println("Parse error"
							+ location
							+ " in \""
							+ reader
							+ "\", and cannot parse this file anymore. Reason: "
							+ e.getMessage());
					inputStream.finish();
				}
				catch (RiotNotFoundException e){
					System.err.println("Format error for the file \"" + reader + "\": " + e.getMessage());
					inputStream.finish();
				}
			}
		};
		executor.submit(parser);

		int i = 0;
		Graph g = Factory.createDefaultGraph();
		try{
			while (iter.hasNext()) {
				Triple next = iter.next();
				g.add(next);
				i++;
				if(i % interval == 0){
					RDFDataMgr.write(System.out, g, RDFFormat.NTRIPLES_UTF8);
					g = Factory.createDefaultGraph();
				}
			}
			if(i % interval > 0){
				RDFDataMgr.write(System.out, g, RDFFormat.NTRIPLES_UTF8);
			}
		}
		catch (RiotException e){
			System.err.println("Riot Exception: " + e.getMessage());
		}
		executor.shutdown();
	}

	private static void issuer(String filename){
		Lang lang = RDFLanguages.filenameToLang(filename);
		try {
			BufferedInputStream bis = new BufferedInputStream(new FileInputStream(filename));
			issuer(bis, lang);
		} catch (FileNotFoundException e) {
			System.err.println("File not found:" + e.getMessage());
		}	
	}

	private static void procTarGz(String file) throws IOException {
		TarArchiveInputStream tarInput = new TarArchiveInputStream(new GzipCompressorInputStream(new FileInputStream(file)));
		TarArchiveEntry currentEntry = tarInput.getNextTarEntry();
		BufferedInputStream bis = null;
		while (currentEntry != null) {
		    Lang lang = RDFLanguages.filenameToLang(currentEntry.getName());
		    bis = new BufferedInputStream(tarInput);
		    issuer(bis, lang);
		    currentEntry = tarInput.getNextTarEntry();
		}
		tarInput.close();
	}
	
	public static void main(String[] args) {

		int idx = 0;
		if(args.length == 0){
			System.out.println("Please specify the filename to be converted.");
			return;
		} else {
			File file = new File(args[idx]);
			if(!file.exists() || !file.canRead()){
				System.out.println("Can't read " + file);
				return;
			}
			if(file.isFile()){
				if( file.getName().endsWith(".tar.gz") || file.getName().endsWith(".taz") ) {
					try {
						procTarGz(args[idx]);
					} catch (IOException e) {
						System.err.println("Something wrong in processing a tar file:" + e.getMessage());
					}
				}else {
					issuer(args[idx]);
				}
			}else if(file.isDirectory()){
				File[] fileList = file.listFiles();
				for (File f: fileList){
					if(f.getName().startsWith("."))
						continue;
					issuer(f.getPath());
				}
			}
		}

	}

}