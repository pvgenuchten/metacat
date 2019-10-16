package edu.ucsb.nceas.metacat.index;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.*;
import org.dataone.cn.indexer.parser.IDocumentSubprocessor;
import org.dataone.cn.indexer.parser.ScienceMetadataDocumentSubprocessor;
import org.dataone.cn.indexer.solrhttp.SolrDoc;
import org.dataone.cn.indexer.solrhttp.SolrElementField;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class PortalSubprocessorTest {

    /**
     * Attempt to parse a 'portal' document using the indexer. See 'application-context-portals.xml'
     * for details on the indexer beans that are configured for parsing these type of documents.
     * @throws Exception something bad happened
     */
    @Test
    public void testProcessDocument() throws Exception {
        String id = "urn:uuid:349aa330-4645-4dab-a02d-3bf950cf708i";

        ArrayList<String> portalFiles = new ArrayList<String>();
        portalFiles.add("src/test/resources/portal-example-full.xml");
        portalFiles.add("src/test/resources/portal-example-seriesId.xml");
        portalFiles.add("src/test/resources/portal-example-sasap.xml");

        ArrayList<String> collectionQueryResultFiles = new ArrayList<String>();
        collectionQueryResultFiles.add("src/test/resources/collectionQuery-result-example-full.txt");
        collectionQueryResultFiles.add("src/test/resources/collectionQuery-result-example-seriesId.txt");
        collectionQueryResultFiles.add("src/test/resources/collectionQuery-result-example-sasap.txt");

        ArrayList<String> portalNames = new ArrayList<String>();
        portalNames.add("My Portal");
        portalNames.add("Another test portal");
        portalNames.add("Lauren's test project - updated");

        for(int i=0; i < portalFiles.size(); i++) {
            String collectionQuery = null;
            InputStream is = getPortalDoc(portalFiles.get(i));
            List<SolrElementField> sysSolrFields = new ArrayList<SolrElementField>();
            SolrDoc indexDocument = new SolrDoc(sysSolrFields);
            Map<String, SolrDoc> docs = new HashMap<String, SolrDoc>();
            docs.put(id, indexDocument);
            IDocumentSubprocessor processor = getPortalSubprocessor();

            String queryResult = null;
            try {
                // Read in the query string that the processor should create. This is read in
                // from disk so that we don't have to bother with special character escaping.
                File file = new File(collectionQueryResultFiles.get(i));
                collectionQuery = FileUtils.readFileToString(file);
                docs = processor.processDocument(id, docs, is);
                // Extract the processed document we just created
                SolrDoc myDoc = docs.get("urn:uuid:349aa330-4645-4dab-a02d-3bf950cf708i");
                // Extract fields and check the values
                String title = myDoc.getField("title").getValue();
                String queryStr = myDoc.getField("collectionQuery").getValue();
                queryStr = queryStr.trim();
                System.out.println("query field value:  " + "\"" + queryStr + "\"");

                // Did the index sub processor correctly extract the 'title' field from the portal document?
                assertTrue("The portalSubprocessor correctly build the document with the correct value in the title field.", title.equalsIgnoreCase(portalNames.get(i)));
                // Did the index sub processor correctly extract the 'collectionQuery' field from the portal document?
                assertTrue("The portalSubprocessor correctly built the document with the correct value in the \"collectionQuery\" field.", queryStr.equalsIgnoreCase(collectionQuery.trim()));

            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
    }

    /*
     * Get the document format of the test resource map file
     */
    private InputStream getPortalDoc(String filename) throws Exception{
        File file = new File(filename);
        InputStream is = new FileInputStream(file);
        return is;
    }

    /*
     * Get the ResourceMapSubprocessor
     */
    private IDocumentSubprocessor getPortalSubprocessor() throws Exception {
        ScienceMetadataDocumentSubprocessor portalSubprocessor = null;
        SolrIndex solrIndex = SolrIndexIT.generateSolrIndex();
        List<IDocumentSubprocessor> processors = solrIndex.getSubprocessors();
        for(IDocumentSubprocessor processor : processors) {
            if(processor.canProcess("https://purl.dataone.org/portals-1.0.0")) {
                //System.out.println("found processor..." + processor.toString());
                return processor;
            }
        }
        return null;
    }
}