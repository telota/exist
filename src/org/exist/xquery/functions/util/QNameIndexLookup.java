/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Team
 *
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.xquery.functions.util;

import org.exist.dom.QName;
import org.exist.storage.Indexable;
import org.exist.storage.NativeValueIndexByQName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.RootNode;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.QNameValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * @author J.M. Vanel
 */
public class QNameIndexLookup extends BasicFunction {

	public final static FunctionSignature signature = new FunctionSignature(
			new QName("qname-index-lookup", UtilModule.NAMESPACE_URI,
					UtilModule.PREFIX),
			"Fast retrieval of nodes by node name and content, "
					+ "using the new value index by QName's",
			new SequenceType[] {
					new SequenceType(Type.QNAME, Cardinality.EXACTLY_ONE),
					new SequenceType(Type.ATOMIC, Cardinality.EXACTLY_ONE) },
			new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE));

	public QNameIndexLookup(XQueryContext context) {
		super(context, signature);
	}

	/**
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence)
			throws XPathException {
		QName qname = ((QNameValue) args[0].itemAt(0)).getQName();
		AtomicValue comparisonCriterium = (AtomicValue) args[1].itemAt(0);
		Sequence result = Sequence.EMPTY_SEQUENCE;

		if (comparisonCriterium instanceof Indexable) {
			NativeValueIndexByQName valueIndex = context.getBroker()
					.getQNameValueIndex();
			if (contextSequence == null) {
				RootNode rootNode = new RootNode(context);
				contextSequence = rootNode.eval(null, null);
			}
			result = valueIndex.findByQName(qname, comparisonCriterium,
					contextSequence);
		} else {
			// TODO error message & log : 
			// "The comparison Criterium must be an Indexable: boolean, numeric, string, and not ...
		}
		return result;
	}
}
