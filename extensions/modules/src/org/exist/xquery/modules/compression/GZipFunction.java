/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-08 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * $Id$
 */
package org.exist.xquery.modules.compression;

import org.apache.commons.io.output.ByteArrayOutputStream;

import java.io.IOException;
import java.util.zip.GZIPOutputStream;

import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Base64Binary;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * Compression into a GZip file
 * 
 * @author Adam Retter <adam@exist-db.org>
 * @version 1.0
 */
public class GZipFunction extends BasicFunction {

	public final static FunctionSignature signatures[] = { new FunctionSignature(
			new QName("gzip", CompressionModule.NAMESPACE_URI,
					CompressionModule.PREFIX),
			"GZip's the data provided in $a.",
			new SequenceType[] { new SequenceType(Type.BASE64_BINARY,
					Cardinality.EXACTLY_ONE) }, new SequenceType(
					Type.BASE64_BINARY, Cardinality.ZERO_OR_ONE)) };

	public GZipFunction(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	public Sequence eval(Sequence[] args, Sequence contextSequence)
			throws XPathException {

		// is there some data to GZip?
		if (args[0].isEmpty()) {
			return Sequence.EMPTY_SEQUENCE;
		}

		Base64Binary bin = (Base64Binary) args[0].itemAt(0);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();

		// gzip the data
		try {
			GZIPOutputStream gzos = new GZIPOutputStream(baos);
			gzos.write(bin.getBinaryData());
			gzos.close();
			return new Base64Binary(baos.toByteArray());
		} catch (IOException ioe) {
			throw new XPathException(this, ioe.getMessage());
		}
	}
}