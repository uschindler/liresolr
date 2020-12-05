package net.semanticmetadata.lire.solr;

import net.semanticmetadata.lire.indexers.hashing.BitSampling;
import net.semanticmetadata.lire.indexers.hashing.MetricSpaces;

import java.io.IOException;
import java.util.zip.GZIPInputStream;

/**
 * Combining init and management code for the MetricSpaces indexing methods.
 *
 * @author Mathias Lux, 19.12.2016.
 */
public class HashingMetricSpacesManager {
    /**
     * Pre-load the static members of MetricSpaces to make sure hash generation is on time.
     * @throws InstantiationException 
     * @throws IllegalAccessException 
     * @throws ClassNotFoundException 
     */
    public static void init() throws IOException, ReflectiveOperationException {
        ClassLoader classloader = HashingMetricSpacesManager.class.getClassLoader();
        MetricSpaces.loadReferencePoints(new GZIPInputStream(classloader.getResourceAsStream("metricspaces/logos-ca-ee_CEDD.msd.gz")));
        MetricSpaces.loadReferencePoints(new GZIPInputStream(classloader.getResourceAsStream("metricspaces/logos-ca-ee_ColorLayout.msd.gz")));
        MetricSpaces.loadReferencePoints(new GZIPInputStream(classloader.getResourceAsStream("metricspaces/logos-ca-ee_EdgeHistogram.msd.gz")));
        MetricSpaces.loadReferencePoints(new GZIPInputStream(classloader.getResourceAsStream("metricspaces/logos-ca-ee_FCTH.msd.gz")));
        MetricSpaces.loadReferencePoints(new GZIPInputStream(classloader.getResourceAsStream("metricspaces/logos-ca-ee_OpponentHistogram.msd.gz")));
        MetricSpaces.loadReferencePoints(new GZIPInputStream(classloader.getResourceAsStream("metricspaces/logos-ca-ee_PHOG.msd.gz")));
        MetricSpaces.loadReferencePoints(new GZIPInputStream(classloader.getResourceAsStream("metricspaces/jpg_us_filter_JCD.msd.gz")));
        BitSampling.readHashFunctions(classloader.getResourceAsStream("lsh/LshBitSampling_2048.obj")); // load BitSampling data from disk.
    }
}
