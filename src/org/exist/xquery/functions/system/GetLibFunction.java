/*
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
 *  $Id: RenderFunction.java 10610 2009-11-26 09:12:00Z shabanovd $
 */

package org.exist.xquery.functions.system;

import java.io.File;

import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Base64BinaryValueType;
import org.exist.xquery.value.BinaryValueFromFile;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * @author Evgeny Gazdovsky <gazdovsky@gmail.com>
 */
public class GetLibFunction extends LibFunction {
	
	public final static FunctionSignature signatures[] = {

			new FunctionSignature(
					new QName("get-lib", SystemModule.NAMESPACE_URI,
							SystemModule.PREFIX),
					"Return a requested lib file from exist's libs",
					new SequenceType[] {
						new FunctionParameterSequenceType(
								"name", Type.STRING, Cardinality.EXACTLY_ONE, 
								"The name of the lib (jar file) from '$EXIST_HOME', '$EXIST_HOME/lib/core', " +
								"'$EXIST_HOME/lib/optional' and '$EXIST_HOME/lib/user' or " +
								"'WEB-INF/lib' (if eXist is worrking in servlet container environment). " +
								"The name can contain mask kind of 'name-%latest%.jar' for getting latest version of lib"
								)
					},
					new FunctionParameterSequenceType(
							"result", Type.NODE, Cardinality.ZERO_OR_ONE, 
							"Binary content of the lib"
							))
			};


	public GetLibFunction(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

    @Override
	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
		
		Sequence result = Sequence.EMPTY_SEQUENCE;
		
		String name = args[0].itemAt(0).getStringValue();
		
		File lib = getLib(name);
		
		if (lib != null){
			/* try {
				DataInputStream in = new DataInputStream( new FileInputStream(lib));		
				byte[] buffer = new byte[(int)lib.length()];
				in.readFully(buffer);
				result = new Base64Binary(buffer);
			} catch (IOException e) {
				throw( new XPathException(this, e.getMessage()));
			}*/

                        return BinaryValueFromFile.getInstance(context, new Base64BinaryValueType(), lib);

		}
		
		return result;
		
	}

}