package net.semanticmetadata.lire.solr;

import net.semanticmetadata.lire.imageanalysis.features.GlobalFeature;
import net.semanticmetadata.lire.imageanalysis.features.global.*;
import net.semanticmetadata.lire.imageanalysis.features.global.joint.JointHistogram;
import net.semanticmetadata.lire.imageanalysis.features.global.spatialpyramid.SPCEDD;
import net.semanticmetadata.lire.solr.features.DoubleFeatureCosineDistance;
import net.semanticmetadata.lire.solr.features.ShortFeatureCosineDistance;

import java.util.HashMap;
import java.util.Iterator;
import java.util.function.Supplier;

/**
 * This file is part of LIRE Solr, a Java library for content based image retrieval.
 *
 * @author Mathias Lux, mathias@juggle.at, 28.11.2014
 */
public class FeatureRegistry {
    /**
     * Naming conventions for code: 2 letters for global features. More for local ones.
     */
    private static HashMap<String, Supplier<GlobalFeature>> codeToSupplier = new HashMap<>();
    /**
     * Caching the entries for fast retrieval or Strings without generating new objects.
     */
    private static HashMap<String, Supplier<GlobalFeature>> hashFieldSupplier = new HashMap<>();
    private static HashMap<String, Supplier<GlobalFeature>> featureFieldSupplier = new HashMap<>();
    private static HashMap<String, String> hashFieldToFeatureField = new HashMap<>();
    private static HashMap<Class<? extends GlobalFeature>, String> classToCode = new HashMap<>();


    // Constants.
    public static final String featureFieldPostfix = "_hi";   // contains the histogram
    public static final String hashFieldPostfix = "_ha";      // contains the hash
    public static final String metricSpacesFieldPostfix = "_ms";      // contains the hash

    static {
        // initial adding of the supported features:
        // classical features from the first implementation
        codeToSupplier.put("cl", ColorLayout::new);
        codeToSupplier.put("eh", EdgeHistogram::new);
        codeToSupplier.put("jc", JCD::new);
        codeToSupplier.put("oh", OpponentHistogram::new);
        codeToSupplier.put("ph", PHOG::new);

        // additional global features
        codeToSupplier.put("ac", AutoColorCorrelogram::new);
        codeToSupplier.put("ad", ACCID::new);
        codeToSupplier.put("ce", CEDD::new);
        codeToSupplier.put("fc", FCTH::new);
        codeToSupplier.put("fo", FuzzyOpponentHistogram::new);
        codeToSupplier.put("jh", JointHistogram::new);
        codeToSupplier.put("sc", ScalableColor::new);
        codeToSupplier.put("pc", SPCEDD::new);
        // GenericFeatures filled with whatever one prefers.
        codeToSupplier.put("df", DoubleFeatureCosineDistance::new);
        codeToSupplier.put("if", GenericGlobalIntFeature::new);
        codeToSupplier.put("sf", ShortFeatureCosineDistance::new);

        // local feature based histograms.
        // codeToClass.put("sim_ce", GenericByteLireFeature::new); // SIMPLE CEDD ... just to give a hint how it might look like.

        // add your features here if you want more.
        // ....

        // -----< caches to be filled >----------------

        for (String code : codeToSupplier.keySet()) {
            hashFieldSupplier.put(code + hashFieldPostfix, codeToSupplier.get(code));
            featureFieldSupplier.put(code + featureFieldPostfix, codeToSupplier.get(code));
            hashFieldToFeatureField.put(code + hashFieldPostfix, code + featureFieldPostfix);
            classToCode.put(codeToSupplier.get(code).get().getClass(), code);
        }
    }

    /**
     * Used to retrieve a registered class for a given hash field name.
     * @param hashFieldName the name of the hash field
     * @return the class for the given field or null if not registered.
     */
    public static Supplier<GlobalFeature> getFeatureSupplierForHashField(String hashFieldName) {
        return hashFieldSupplier.get(hashFieldName);
    }


    /**
     * Used to retrieve a registered class for a given field name in SOLR for the feature.
     * @param featureFieldName the name of the field containing the histogram
     * @return the class for the given field or null if not registered.
     */
    public static Supplier<GlobalFeature> getFeatureSupplierForFeatureField(String featureFieldName) {
        return featureFieldSupplier.get(featureFieldName);
    }

    /**
     * Returns the feature's histogram field for a given hash field.
     * @param hashFieldName the name of the hash field
     * @return the name or null if the feature is not registered.
     */
    public static String getFeatureFieldName(String hashFieldName) {
        return hashFieldToFeatureField.get(hashFieldName);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Registered features:\n");
        sb.append("code\thash field\tfeature field\tclass\n");
        for (Iterator<String> iterator = codeToSupplier.keySet().iterator(); iterator.hasNext(); ) {
            String code = iterator.next();
            sb.append(code);
            sb.append('\t');
            sb.append(code + hashFieldPostfix);
            sb.append('\t');
            sb.append(code+featureFieldPostfix);
            sb.append('\t');
            sb.append(codeToSupplier.get(code).getClass().getName());
            sb.append('\n');
        }
        return sb.toString();
    }

    public static String getCodeForClass(Class<? extends GlobalFeature> featureClass) {
        return classToCode.get(featureClass);
    }

    public static Supplier<GlobalFeature> getFeatureSupplierForCode(String code) {
        return codeToSupplier.get(code);
    }

    public static String codeToHashField(String code) {
        return code + hashFieldPostfix;
    }

    public static String codeToMetricSpacesField(String code) {
        return code + metricSpacesFieldPostfix;
    }

    public static String codeToFeatureField(String code) {
        return code + featureFieldPostfix;
    }
}
