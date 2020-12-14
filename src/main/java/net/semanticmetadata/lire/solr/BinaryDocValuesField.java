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
import java.nio.ByteBuffer;
import java.util.Map;

import org.apache.lucene.document.Field;
import org.apache.lucene.document.SortedSetDocValuesField;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.search.SortField;
import org.apache.lucene.util.BytesRef;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrException.ErrorCode;
import org.apache.solr.common.util.Base64;
import org.apache.solr.response.TextResponseWriter;
import org.apache.solr.schema.FieldType;
import org.apache.solr.schema.IndexSchema;
import org.apache.solr.schema.SchemaField;
import org.apache.solr.uninverting.UninvertingReader;

/**
 * Base64 -&gt; DocValues implementation used for the Solr Plugin. Using this field one can index byte[] values by
 * sending them to Solr base64 encoded. In case of the LIRE plugin, the fields get read linearly, so they need to be
 * extremely fast, which is the case with the DocValues.
 * @author Mathias Lux, mathias@juggle.at, 12.08.2013; Uwe Schindler (additions for Multivalued and more strict schema handling)
 */
public class BinaryDocValuesField extends FieldType {
  
    @Override
    protected void init(IndexSchema schema, Map<String,String> args) {
      super.init(schema, args);
      if ((trueProperties & (FieldType.STORED|FieldType.INDEXED)) != 0) {
        throw new SolrException(ErrorCode.SERVER_ERROR, "Field type " + this + " cannot be indexed or stored; to emulate stored fields use useDocValuesAsStored");
      }
      if ((falseProperties & FieldType.DOC_VALUES) != 0) {
        throw new SolrException(ErrorCode.SERVER_ERROR, "Field type " + this + " needs docValues enabled");
      }
      // set correct defaults for this field type:
      properties &= ~(FieldType.STORED | FieldType.INDEXED);
      properties |= FieldType.DOC_VALUES;
    }
    
    @Override
    protected void checkSupportsDocValues() {
      // we support docValues
    }

    private static String toBase64String(BytesRef bytes) {
        return Base64.byteArrayToBase64(bytes.bytes, bytes.offset, bytes.length);
    }

    @Override
    public void write(TextResponseWriter writer, String name, IndexableField f) throws IOException {
        writer.writeStr(name, toBase64String(toObject(f)), false);
    }

    @Override
    public SortField getSortField(SchemaField field, boolean top) {
        throw new UnsupportedOperationException("Cannot sort on a Binary field");
    }

    @Override
    public String toExternal(IndexableField f) {
        return toBase64String(toObject(f));
    }

    @Override
    public BytesRef toObject(IndexableField f) {
        final BytesRef b = f.binaryValue();
        return b == null ? null : BytesRef.deepCopyOf(b);
    }


    @Override
    public BytesRef toObject(SchemaField sf, BytesRef b) {
        return b == null ? null : BytesRef.deepCopyOf(b);
    }

    @Override
    public UninvertingReader.Type getUninversionType(SchemaField sf) {
        throw new AssertionError("Should never be called, as docvalues are always available");
    }

    @Override
    public IndexableField createField(SchemaField field, Object val /*, float boost*/) {
        if (val == null) {
          return null;
        }
        
        final BytesRef ref;
        if (val instanceof byte[]) {
            final byte[] b = (byte[]) val;
            ref = new BytesRef(b, 0, b.length);
        } else if (val instanceof ByteBuffer && ((ByteBuffer)val).hasArray()) {
            final ByteBuffer byteBuf = (ByteBuffer) val;
            ref = new BytesRef(byteBuf.array(), byteBuf.position(), byteBuf.limit() - byteBuf.position());
        } else {
            String strVal = val.toString();
            //the string has to be a base64 encoded string
            final byte[] b = Base64.base64ToByteArray(strVal);
            ref = new BytesRef(b, 0, b.length);
        }

        final Field f;
        if (field.multiValued()) {
          f = new SortedSetDocValuesField(field.getName(), ref);
        } else {
          f = new org.apache.lucene.document.BinaryDocValuesField(field.getName(), ref);
        }
        return f;
    }

    @Override
    public void checkSchemaField(SchemaField field) {
      if (field.stored() || field.indexed()) {
        throw new SolrException(ErrorCode.SERVER_ERROR, "Field type " + this + " cannot be indexed or stored; to emulate stored fields use useDocValuesAsStored");
      }
      if (!field.hasDocValues()) {
        throw new SolrException(ErrorCode.SERVER_ERROR, "Field type " + this + " needs docValues enabled");
      }
      super.checkSchemaField(field);
    }
}
