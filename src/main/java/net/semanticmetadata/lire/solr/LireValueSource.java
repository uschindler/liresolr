/*
 * This file is part of the LIRE project: http://www.semanticmetadata.net/lire
 * LIRE is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * LIRE is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LIRE; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 * We kindly ask you to refer the any or one of the following publications in
 * any publication mentioning or employing Lire:
 *
 * Lux Mathias, Savvas A. Chatzichristofis. Lire: Lucene Image Retrieval –
 * An Extensible Java CBIR Library. In proceedings of the 16th ACM International
 * Conference on Multimedia, pp. 1085-1088, Vancouver, Canada, 2008
 * URL: http://doi.acm.org/10.1145/1459359.1459577
 *
 * Lux Mathias. Content Based Image Retrieval with LIRE. In proceedings of the
 * 19th ACM International Conference on Multimedia, pp. 735-738, Scottsdale,
 * Arizona, USA, 2011
 * URL: http://dl.acm.org/citation.cfm?id=2072432
 *
 * Mathias Lux, Oge Marques. Visual Information Retrieval using Java and LIRE
 * Morgan & Claypool, 2013
 * URL: http://www.morganclaypool.com/doi/abs/10.2200/S00468ED1V01Y201301ICR025
 *
 * Copyright statement:
 * --------------------
 * (c) 2002-2013 by Mathias Lux (mathias@juggle.at)
 *     http://www.semanticmetadata.net/lire, http://www.lire-project.net
 */

package net.semanticmetadata.lire.solr;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.index.DocValues;
import org.apache.lucene.index.DocValuesType;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.SortedSetDocValues;
import org.apache.lucene.queries.function.FunctionValues;
import org.apache.lucene.queries.function.ValueSource;
import org.apache.lucene.queries.function.docvalues.DoubleDocValues;
import org.apache.lucene.queries.function.valuesource.DoubleConstValueSource;
import org.apache.lucene.util.ArrayUtil;
import org.apache.lucene.util.BytesRef;
import org.apache.solr.common.util.Base64;

import net.semanticmetadata.lire.imageanalysis.features.GlobalFeature;

/**
 * A query function for sorting results based on the LIRE CBIR functions.
 * Implementation based partially on the outdated guide given on http://www.supermind.org/blog/756,
 * comments on the mailing list provided from Chris Hostetter, and the 4.4 Solr and Lucene source.
 *
 * @author Mathias Lux, 17.09.13 12:26, Uwe Schindler (Solr 7/8 fixes; multiValue support)
 */
public final class LireValueSource extends ValueSource {
    final String field;
    final AggregationFunction aggFunc;
    final double maxDistance;
    private final byte[] hist;
    final GlobalFeature feature;
    final Supplier<GlobalFeature> featureProvider;
    private final String desc;
    
    /**
     * A function to combine the distances of multiple images into one value
     * @author Uwe Schindler
     */
    public static enum AggregationFunction {
      AVG {
        @Override
        public double combine(double[] values, int count) {
          double v = 0;
          for (int i = 0; i < count; i++) {
            v += values[i];
          }
          return v / count;
        }
      },
      
      MIN {
        @Override
        public double combine(double[] values, int count) {
          double v = values[0];
          for (int i = 1; i < count; i++) {
            v = Math.min(values[i], v);
          }
          return v;
        }
      },
      
      MAX {
        @Override
        public double combine(double[] values, int count) {
          double v = values[0];
          for (int i = 1; i < count; i++) {
            v = Math.max(values[i], v);
          }
          return v;
        }
      };
      
      public abstract double combine(double[] values, int count);
    }

    /**
     * @param featureField the field of the feature used for sorting.
     * @param hist the histogram in bytes.
     * @param maxDistance  the distance value returned if there is no distance calculation possible.
     */
    public LireValueSource(String featureField, byte[] hist, AggregationFunction aggFunc, double maxDistance) {
        Objects.requireNonNull(featureField, "featureField");
        Objects.requireNonNull(hist, "hist");
        Objects.requireNonNull(aggFunc, "aggFunc");
        
        this.field = featureField;
        this.hist = hist;
        this.aggFunc = aggFunc;
        this.maxDistance = maxDistance;

        // get the feature from the feature registry.
        this.featureProvider = FeatureRegistry.getFeatureSupplierForFeatureField(field);
        if (this.featureProvider == null) {
            throw new IllegalArgumentException("Feature for '" + field + "' is not registered.");
        }

        this.feature = featureProvider.get();
        // debug ...
        // System.out.println("Setting " + feature.getClass().getName() + " to " + Base64.byteArrayToBase64(hist, 0, hist.length));
        feature.setByteArrayRepresentation(hist);
        
        // calculate description upfront, so caching is faster:
        desc = "lirefunc(" + field + ",'" + Base64.byteArrayToBase64(hist) + "'," + aggFunc + "," + maxDistance + ")";
    }
    
    /**
     * Check also {@link org.apache.lucene.queries.function.valuesource.BytesRefFieldSource}
     */
    @SuppressWarnings("rawtypes")
    @Override
    public FunctionValues getValues(Map context, LeafReaderContext readerContext) throws IOException {
        final FieldInfo fieldInfo = readerContext.reader().getFieldInfos().fieldInfo(field);
        final DocValuesType type = (fieldInfo != null) ? fieldInfo.getDocValuesType() : DocValuesType.NONE;
        switch (type) {
          case BINARY: { // single-valued
            final BinaryDocValues values = DocValues.getBinary(readerContext.reader(), field);

            return new DoubleDocValues(this) {
              private final GlobalFeature tmpFeature = featureProvider.get();
              private double currentValue = maxDistance;
              private int lastDocID, lastCalculatedDocID = -1;

              private double calcCurrentValue() throws IOException {
                final BytesRef bytesRef = values.binaryValue();
                if (bytesRef != null && bytesRef.length > 0) {
                    tmpFeature.setByteArrayRepresentation(
                            bytesRef.bytes,
                            bytesRef.offset,
                            bytesRef.length);
                    return tmpFeature.getDistance(feature);
                } else {
                    return maxDistance; // make sure max distance is returned for those without value
                }
              }
              
              @Override
              public double doubleVal(int doc) throws IOException {
                if (doc < lastDocID) {
                  throw new IllegalArgumentException("docs were sent out-of-order: lastDocID=" + lastDocID + " vs docID=" + doc);
                }
                lastDocID = doc;
                if (lastCalculatedDocID == doc) {
                  return currentValue;
                }
                int curDocID = values.docID();
                if (doc > curDocID) {
                  curDocID = values.advance(doc);
                }
                lastCalculatedDocID = doc;
                if (doc == curDocID) {
                  return currentValue = calcCurrentValue();
                } else {
                  return currentValue = maxDistance;
                }
              }

              @Override
              public boolean exists(int doc) throws IOException {
                return true; // we return a value for every doc!
              }
            };
          }
          
          case SORTED_SET: { // multi-valued
            final SortedSetDocValues values = DocValues.getSortedSet(readerContext.reader(), field);

            return new DoubleDocValues(this) {
              private final GlobalFeature tmpFeature = featureProvider.get();
              private double[] distances = new double[16];
              private double currentValue = maxDistance;
              private int lastDocID, lastCalculatedDocID = -1;

              private double calcCurrentValue() throws IOException {
                int idx = 0;
                for (long ord = values.nextOrd(); ord != SortedSetDocValues.NO_MORE_ORDS; ord = values.nextOrd()) {
                  final BytesRef bytesRef = values.lookupOrd(ord);
                  if (bytesRef.length == 0) continue;
                  tmpFeature.setByteArrayRepresentation(
                      bytesRef.bytes,
                      bytesRef.offset,
                      bytesRef.length);
                  if (idx >= distances.length) {
                    distances = ArrayUtil.grow(distances);
                  }
                  distances[idx] = tmpFeature.getDistance(feature);
                  idx++;
                }
                switch (idx) {
                  case 0:
                    return maxDistance;
                  case 1: // optimization if there's only one value
                    return distances[0];
                  default:
                    return aggFunc.combine(distances, idx);
                }
              }
              
              @Override
              public double doubleVal(int doc) throws IOException {
                if (doc < lastDocID) {
                  throw new IllegalArgumentException("docs were sent out-of-order: lastDocID=" + lastDocID + " vs docID=" + doc);
                }
                lastDocID = doc;
                if (lastCalculatedDocID == doc) {
                  return currentValue;
                }
                int curDocID = values.docID();
                if (doc > curDocID) {
                  curDocID = values.advance(doc);
                }
                lastCalculatedDocID = doc;
                if (doc == curDocID) {
                  return currentValue = calcCurrentValue();
                } else {
                  return currentValue = maxDistance;
                }
              }

              @Override
              public boolean exists(int doc) throws IOException {
                return true; // we return a value for every doc!
              }
            };
          }
          
          default:
            return new DoubleConstValueSource(maxDistance).getValues(context, readerContext);
        }
    }

    @Override
    public int hashCode() {
        return desc.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null) return false;
      if (getClass() != obj.getClass()) return false;
      final LireValueSource other = (LireValueSource) obj;
      if (!Objects.equals(field, other.field)) return false;
      if (!Arrays.equals(hist, other.hist)) return false;
      if (!Objects.equals(aggFunc, other.aggFunc)) return false;
      if (Double.doubleToLongBits(maxDistance) != Double.doubleToLongBits(other.maxDistance)) return false;
      return true;
    }
    
    @Override
    public String description() {
        return desc;
    }
}
