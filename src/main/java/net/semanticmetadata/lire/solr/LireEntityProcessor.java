package net.semanticmetadata.lire.solr;

import net.semanticmetadata.lire.imageanalysis.features.GlobalFeature;
import net.semanticmetadata.lire.imageanalysis.features.global.*;
import net.semanticmetadata.lire.indexers.hashing.BitSampling;
import net.semanticmetadata.lire.solr.indexing.ParallelSolrIndexer;
import org.apache.commons.codec.binary.Base64;
import org.apache.solr.handler.dataimport.Context;
import org.apache.solr.handler.dataimport.DataSource;
import org.apache.solr.handler.dataimport.EntityProcessorBase;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static org.apache.solr.handler.dataimport.DataImportHandlerException.SEVERE;
import static org.apache.solr.handler.dataimport.DataImportHandlerException.wrapAndThrow;
import static org.apache.solr.handler.dataimport.XPathEntityProcessor.URL;

/**
 * An entity processor like the one for Tika to support data base imports and alike
 * Special thanks to Giuseppe Becchi, who triggered the development, tested it and
 * found the location the critical bug
 *
 * @author Mathias Lux, mathias@juggle.at on 17.12.13.
 */
public class LireEntityProcessor extends EntityProcessorBase {
    protected boolean done = false;
    protected GlobalFeature[] listOfFeatures = new GlobalFeature[]{
            new ColorLayout(), new PHOG(), new EdgeHistogram(), new JCD(), new OpponentHistogram()
    };
    int count = 0;

    protected void firstInit(Context context) {
        super.firstInit(context);
        done = false;
    }

    /**
     * @return a row where the key is the name of the field and value can be any Object or a Collection of objects. Return
     * null to signal end of rows
     */
    public Map<String, Object> nextRow() {
        if (done) {
            done = false;
            return null;
        }
        Map<String, Object> row = new HashMap<String, Object>();
        @SuppressWarnings("unchecked") DataSource<InputStream> dataSource = context.getDataSource();
        // System.out.println("\n**** " + context.getResolvedEntityAttribute(URL));
        InputStream is = dataSource.getData(context.getResolvedEntityAttribute(URL));
        row.put("id", context.getResolvedEntityAttribute(URL));
        // here we have to open the stream and extract the features. Then we put them into the row object.
        // basically I hope that the entity processor is called for each entity anew, otherwise this approach won't work.
        try {
            BufferedImage img = ImageIO.read(is);
            row.put("id", context.getResolvedEntityAttribute(URL));
            for (int i = 0; i < listOfFeatures.length; i++) {
                GlobalFeature feature = listOfFeatures[i];
                feature.extract(img);
                String code = FeatureRegistry.getCodeForClass(feature.getClass());
                String histogramField = code.concat(FeatureRegistry.featureFieldPostfix);
                String hashesField = code.concat(FeatureRegistry.hashFieldPostfix);
                row.put(histogramField, Base64.encodeBase64String(feature.getByteArrayRepresentation()));
                row.put(hashesField, ParallelSolrIndexer.arrayToString(BitSampling.generateHashes(((GlobalFeature) feature).getFeatureVector())));
            }
        } catch (IOException e) {
            wrapAndThrow(SEVERE, e, "Error loading image or extracting features.");
        }
        // if (count++>10)
        done = true;
        // dataSource.close();
        return row;
    }
}
