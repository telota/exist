/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
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
/*
 *  Some modifications Copyright (C) 2004 Luigi P. Bai
 *  finder@users.sf.net
 *  Licensed as above under the LGPL.
 *  
 */
package org.exist.xupdate;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.TreeMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.log4j.Logger;
import org.exist.dom.DocumentSet;
import org.exist.dom.NodeListImpl;
import org.exist.dom.XMLUtil;
import org.exist.storage.DBBroker;
import org.exist.util.Configuration;
import org.exist.util.FastStringBuffer;
import org.exist.xquery.PathExpr;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.parser.XQueryLexer;
import org.exist.xquery.parser.XQueryParser;
import org.exist.xquery.parser.XQueryTreeParser;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.Type;
import org.w3c.dom.Attr;
import org.w3c.dom.Comment;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.ProcessingInstruction;
import org.w3c.dom.Text;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.LexicalHandler;

import antlr.RecognitionException;
import antlr.TokenStreamException;
import antlr.collections.AST;

/**
 * XUpdateProcessor.java
 * 
 * @author Wolfgang Meier
 * 
 */
public class XUpdateProcessor implements ContentHandler, LexicalHandler {

	public static final String MODIFICATIONS = "modifications";
	
	// Modifications
	public static final String INSERT_AFTER = "insert-after";
	public static final String INSERT_BEFORE = "insert-before";
	public static final String REPLACE = "replace";
	public static final String RENAME = "rename";
	public static final String REMOVE = "remove";
	public static final String APPEND = "append";
	public static final String UPDATE = "update";
	
	// node constructors
	public static final String COMMENT = "comment";
	public static final String PROCESSING_INSTRUCTION = "processing-instruction";
	public static final String TEXT = "text";
	public static final String ATTRIBUTE = "attribute";
	public static final String ELEMENT = "element";
	
	public static final String VALUE_OF = "value-of";
	public static final String VARIABLE = "variable";
	public static final String IF = "if";
	
	public final static String XUPDATE_NS = "http://www.xmldb.org/xupdate";

	private final static Logger LOG = Logger.getLogger(XUpdateProcessor.class);

	private NodeListImpl contents = null;
	
	private boolean inModification = false;
	private boolean inAttribute = false;
    private boolean preserveWhitespace = false;
    private boolean preserveWhitespaceTemp = false;
    private Stack spaceStack = null;
	
	private Modification modification = null;
	private DocumentBuilder builder;
	private Document doc;
	
	private Stack stack = new Stack();
	private Node currentNode = null;
	private DBBroker broker;
	private DocumentSet documentSet;
	
	private List modifications = new ArrayList();
	
	private FastStringBuffer charBuf = new FastStringBuffer(6, 15, 5);
	
	private Map variables = new TreeMap();
	private Map namespaces = new HashMap(10);
	private Stack conditionals = new Stack();

	/**
	 * Constructor for XUpdateProcessor.
	 */
	public XUpdateProcessor(DBBroker broker, DocumentSet docs)
		throws ParserConfigurationException {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		factory.setValidating(false);
		this.builder = factory.newDocumentBuilder();
		this.broker = broker;
		this.documentSet = docs;
		namespaces.put("xml", "http://www.w3.org/XML/1998/namespace");
		//TODO : move this to a dedicated configure() method.
		if (broker != null) {
			Configuration config = broker.getConfiguration();
			Boolean temp;
			if ((temp = (Boolean) config.getProperty("indexer.preserve-whitespace-mixed-content"))
				!= null)
				preserveWhitespaceTemp = temp.booleanValue();
		}
	}

	public XUpdateProcessor() throws ParserConfigurationException {
	    this(null, null);
	}
	
	public void setBroker(DBBroker broker) {
	    this.broker = broker;
	}
	
	public void setDocumentSet(DocumentSet docs) {
	    this.documentSet = docs;
	}
	
	/**
	 * Parse the input source into a set of modifications.
	 * 
	 * @param is
	 * @return an array of type Modification
	 * @throws ParserConfigurationException
	 * @throws IOException
	 * @throws SAXException
	 */
	public Modification[] parse(InputSource is)
		throws ParserConfigurationException, IOException, SAXException {
		XMLReader reader = broker.getBrokerPool().getParserPool().borrowXMLReader();
		try {
			reader.setProperty("http://xml.org/sax/properties/lexical-handler", this);
            reader.setFeature("http://xml.org/sax/features/namespaces", true);
            reader.setFeature("http://xml.org/sax/features/namespace-prefixes", false);
			reader.setContentHandler(this);
			
			reader.parse(is);
			Modification mods[] = new Modification[modifications.size()];
			return (Modification[]) modifications.toArray(mods);
		} finally {
			broker.getBrokerPool().getParserPool().returnXMLReader(reader);
		}
	}

	/**
	 * @see org.xml.sax.ContentHandler#setDocumentLocator(org.xml.sax.Locator)
	 */
	public void setDocumentLocator(Locator locator) {
	}

	/**
	 * @see org.xml.sax.ContentHandler#startDocument()
	 */
	public void startDocument() throws SAXException {
        // The default...
        this.preserveWhitespace = preserveWhitespaceTemp;
        this.spaceStack = new Stack();
        this.spaceStack.push("default");
	}

	/**
	 * @see org.xml.sax.ContentHandler#endDocument()
	 */
	public void endDocument() throws SAXException {
	}

	/**
	 * @see org.xml.sax.ContentHandler#startPrefixMapping(java.lang.String, java.lang.String)
	 */
	public void startPrefixMapping(String prefix, String uri)
		throws SAXException {
		namespaces.put(prefix, uri);
	}

	/**
	 * @see org.xml.sax.ContentHandler#endPrefixMapping(java.lang.String)
	 */
	public void endPrefixMapping(String prefix) throws SAXException {
		namespaces.remove(prefix);
	}

	/**
	 * @see org.xml.sax.ContentHandler#startElement(java.lang.String, java.lang.String, java.lang.String, org.xml.sax.Attributes)
	 */
	public void startElement(
		String namespaceURI,
		String localName,
		String qName,
		Attributes atts)
		throws SAXException {
		// save accumulated character content
		if (inModification && charBuf.length() > 0) {
//            String normalized = charBuf.toString();
			final String normalized = preserveWhitespace ? charBuf.toString() :
				charBuf.getNormalizedString(FastStringBuffer.SUPPRESS_BOTH);

			if (normalized.length() > 0) {
				Text text = doc.createTextNode(charBuf.toString());
				if (stack.isEmpty()) {
					//LOG.debug("appending text to fragment: " + text.getData());
					contents.add(text);
				} else {
					Element last = (Element) stack.peek();
					last.appendChild(text);
				}
			}
			charBuf.setLength(0);
		}
		if (namespaceURI.equals(XUPDATE_NS)) {
			String select = null;
			if (localName.equals(MODIFICATIONS)) {
				startModifications(atts);
				return;
			} else if (localName.equals(VARIABLE)) {
				// variable declaration
				startVariableDecl(atts);
				return;
			} else if (IF.equals(localName)) {
				if (inModification)
					throw new SAXException("xupdate:if is not allowed inside a modification");
				select = atts.getValue("test");
				Conditional cond = new Conditional(broker, documentSet, select, namespaces, variables);
				conditionals.push(cond);
				return;
			} else if (VALUE_OF.equals(localName)) {
				if(!inModification)
					throw new SAXException("xupdate:value-of is not allowed outside a modification");
				
			} else if (APPEND.equals(localName)
				|| INSERT_BEFORE.equals(localName)
				|| INSERT_AFTER.equals(localName)
				|| REMOVE.equals(localName)
				|| RENAME.equals(localName)
				|| UPDATE.equals(localName)
				|| REPLACE.equals(localName)) {
				if (inModification)
					throw new SAXException("nested modifications are not allowed");
				select = atts.getValue("select");
				if (select == null)
					throw new SAXException(
						localName + " requires a select attribute");
				doc = builder.newDocument();
				contents = new NodeListImpl();
				inModification = true;
			} else if (
				(ELEMENT.equals(localName)
					|| ATTRIBUTE.equals(localName)
					|| TEXT.equals(localName)
					|| PROCESSING_INSTRUCTION.equals(localName)
					|| COMMENT.equals(localName))) {
				if(!inModification)
					throw new SAXException(
							"creation elements are only allowed inside "
							+ "a modification");
				charBuf.setLength(0);
			} else
				throw new SAXException("Unknown XUpdate element: " + qName);

			// start a new modification section
			if (APPEND.equals(localName)) {
			    String child = atts.getValue("child");
				modification = new Append(broker, documentSet, select, child, namespaces, variables);
			} else if (UPDATE.equals(localName))
				modification = new Update(broker, documentSet, select, namespaces, variables);
			else if (INSERT_BEFORE.equals(localName))
				modification =
					new Insert(broker, documentSet, select, Insert.INSERT_BEFORE, namespaces, variables);
			else if (INSERT_AFTER.equals(localName))
				modification =
					new Insert(broker, documentSet, select, Insert.INSERT_AFTER, namespaces, variables);
			else if (REMOVE.equals(localName))
				modification = new Remove(broker, documentSet, select, namespaces, variables);
			else if (RENAME.equals(localName))
				modification = new Rename(broker, documentSet, select, namespaces, variables);
			else if (REPLACE.equals(localName))
				modification = new Replace(broker, documentSet, select, namespaces, variables);

			// process commands for node creation
			else if (ELEMENT.equals(localName)) {
				String name = atts.getValue("name");
				if (name == null)
					throw new SAXException("element requires a name attribute");
				int p = name.indexOf(':');
				String namespace = null;
				String prefix = "";
				if (p > -1) {
					prefix = name.substring(0, p);
					if (name.length() == p + 1)
						throw new SAXException(
							"illegal prefix in qname: " + name);
					name = name.substring(p + 1);
					namespace = atts.getValue("namespace");
					if(namespace == null)
						namespace = (String) namespaces.get(prefix);
					if (namespace == null) {
						throw new SAXException(
							"no namespace defined for prefix " + prefix);
					}
				}
				Element elem;
				if (namespace != null && namespace.length() > 0)
				{
					elem = doc.createElementNS(namespace, name);
				elem.setPrefix(prefix);
				}
				else
					elem = doc.createElement(name);
			
				if (stack.isEmpty()) {
					contents.add(elem);
				} else {
					Element last = (Element) stack.peek();
					last.appendChild(elem);
				}
				this.setWhitespaceHandling((Element) stack.push(elem));
			} else if (ATTRIBUTE.equals(localName)) {
				String name = atts.getValue("name");
				if (name == null)
					throw new SAXException("attribute requires a name attribute");
				int p = name.indexOf(':');
				String namespace = null;
				if (p > -1) {
					String prefix = name.substring(0, p);
					if (name.length() == p + 1)
						throw new SAXException(
							"illegal prefix in qname: " + name);
					namespace = atts.getValue("namespace");
					if(namespace == null)
						namespace = (String) namespaces.get(prefix);
					if (namespace == null)
						throw new SAXException(
							"no namespace defined for prefix " + prefix);
				}
				Attr attrib = namespace != null && namespace.length() > 0 ?
								doc.createAttributeNS(namespace, name) :
								doc.createAttribute(name);
				if (stack.isEmpty()) {
					for(int i = 0; i < contents.getLength(); i++) {
						Node n = contents.item(i);
						String ns = n.getNamespaceURI();
						if(ns == null) ns = "";
						if(n.getNodeType() == Node.ATTRIBUTE_NODE &&
								n.getLocalName().equals(name) &&
								ns.equals(namespace))
							throw new SAXException("The attribute " + attrib.getNodeName() + " cannot be specified twice");
					}
					contents.add(attrib);
				} else {
					Element last = (Element) stack.peek();
					if(namespace != null && last.hasAttributeNS(namespace, name) ||
					   namespace == null && last.hasAttribute(name))
						throw new SAXException("The attribute " + attrib.getNodeName() + " cannot be specified " +
								"twice on the same element");
					if (namespace != null)
					last.setAttributeNodeNS(attrib);
					else
					  last.setAttributeNode(attrib);
				}
				inAttribute = true;
				currentNode = attrib;

				// process value-of
			} else if (VALUE_OF.equals(localName)) {
				select = atts.getValue("select");
				if (select == null)
					throw new SAXException("value-of requires a select attribute");
				Sequence seq = processQuery(select);
				LOG.debug("Found " + seq.getLength() + " items for value-of");
				Item item;
				for (SequenceIterator i = seq.iterate(); i.hasNext();) {
					item = i.nextItem();
					if(Type.subTypeOf(item.getType(), Type.NODE)) { 
						Node node = XMLUtil.copyNode(doc, ((NodeValue)item).getNode());
						if (stack.isEmpty())
							contents.add(node);
						else {
							Element last = (Element) stack.peek();
							last.appendChild(node);
						}
					} else {
						try {
							String value = item.getStringValue();
							characters(value.toCharArray(), 0, value.length());
						} catch(XPathException e) {
							throw new SAXException(e.getMessage(), e);
						}
					}
				}
			}
		} else if (inModification) {
			Element elem = namespaceURI != null && namespaceURI.length() > 0 ?
									doc.createElementNS(namespaceURI, qName) :
									doc.createElement(qName);
			Attr a;
			for (int i = 0; i < atts.getLength(); i++) {
                String name = atts.getQName(i);
                String nsURI = atts.getURI(i);
                if (name.startsWith("xmlns")) {
                    // Why are these showing up? They are supposed to be stripped out?
                } else {
                    a = nsURI != null ?
                          doc.createAttributeNS(nsURI, name) :
                            doc.createAttribute(name);
                    a.setValue(atts.getValue(i));
                    if (nsURI != null)
                    elem.setAttributeNodeNS(a);
                    else
                      elem.setAttributeNode(a);
                }
			}
			if (stack.isEmpty()) {
				contents.add(elem);
			} else {
				Element last = (Element) stack.peek();
				last.appendChild(elem);
			}
            this.setWhitespaceHandling((Element) stack.push(elem));
		}
	}

	private void startVariableDecl(Attributes atts) throws SAXException {
		String select = atts.getValue("select");
		if (select == null)
			throw new SAXException("variable declaration requires a select attribute");
		String name = atts.getValue("name");
		if (name == null)
			throw new SAXException("variable declarations requires a name attribute");
		createVariable(name, select);
	}

	private void startModifications(Attributes atts) throws SAXException {
		String version = atts.getValue("version");
		if (version == null)
			throw new SAXException(
				"version attribute is required for "
					+ "element modifications");
		if (!version.equals("1.0"))
			throw new SAXException(
				"Version "
					+ version
					+ " of XUpdate "
					+ "not supported.");
	}

	/**
	 * @see org.xml.sax.ContentHandler#endElement(java.lang.String, java.lang.String, java.lang.String)
	 */
	public void endElement(String namespaceURI, String localName, String qName)
		throws SAXException {
		if (inModification && charBuf.length() > 0) {
			final String normalized = preserveWhitespace ? charBuf.toString() :
				charBuf.getNormalizedString(FastStringBuffer.SUPPRESS_BOTH);
			if (normalized.length() > 0) {
				Text text = doc.createTextNode(charBuf.toString());
				if (stack.isEmpty()) {
					contents.add(text);
				} else {
					Element last = (Element) stack.peek();
					last.appendChild(text);
				}
			}
			charBuf.setLength(0);
		}
		if (XUPDATE_NS.equals(namespaceURI)) {
			if (IF.equals(localName)) {
				Conditional cond = (Conditional) conditionals.pop();
				modifications.add(cond);
			} else if (localName.equals(ELEMENT)) {
				this.resetWhitespaceHandling((Element) stack.pop());
			} else if (localName.equals(ATTRIBUTE)) {
				inAttribute = false;
			} else if (localName.equals(APPEND)
				|| localName.equals(UPDATE)
				|| localName.equals(REMOVE)
				|| localName.equals(RENAME)
				|| localName.equals(REPLACE)
				|| localName.equals(INSERT_BEFORE)
				|| localName.equals(INSERT_AFTER)) {
				inModification = false;
				modification.setContent(contents);
				if(!conditionals.isEmpty()) {
					Conditional cond = (Conditional) conditionals.peek();
					cond.addModification(modification);
				} else {
					modifications.add(modification);
				}
				modification = null;
			}
		} else if (inModification) {
            this.resetWhitespaceHandling((Element) stack.pop());
        }
	}

	/**
	 * @see org.xml.sax.ContentHandler#characters(char, int, int)
	 */
	public void characters(char[] ch, int start, int length)
		throws SAXException {
		if (inModification) {
			if (inAttribute) {
			    Attr attr = (Attr)currentNode;
			    String val = attr.getValue();
			    if(val == null)
			        val = new String(ch, start, length);
			    else
			        val += new String(ch, start, length);
				attr.setValue(val);
			} else {
				charBuf.append(ch, start, length);
			}
		}
	}

	/**
	 * @see org.xml.sax.ContentHandler#ignorableWhitespace(char, int, int)
	 */
	public void ignorableWhitespace(char[] ch, int start, int length)
		throws SAXException {
        if (this.preserveWhitespace) {
            if (this.inModification) {
                if (this.inAttribute) {
                    Attr attr = (Attr) this.currentNode;
                    String val = attr.getValue();
                    if(val == null)
                        val = new String(ch, start, length);
                    else
                        val += new String(ch, start, length);
                    attr.setValue(val);
                } else {
                    this.charBuf.append(ch, start, length);
                }
            }
        }
	}
    
    private void setWhitespaceHandling(Element e) {
        String wsSetting = e.getAttributeNS("http://www.w3.org/XML/1998/namespace", "space");
        if ("preserve".equals(wsSetting)) {
            this.spaceStack.push(wsSetting);
            this.preserveWhitespace = true;
        } else if ("default".equals(wsSetting)) {
            this.spaceStack.push(wsSetting);
            this.preserveWhitespace = preserveWhitespaceTemp;
        }
        // Otherwise, don't change what's currently in effect!
    }
    
    private void resetWhitespaceHandling(Element e) {
        String wsSetting = e.getAttributeNS("http://www.w3.org/XML/1998/namespace", "space");
        if ("preserve".equals(wsSetting) || "default".equals(wsSetting)) {
            // Since an opinion was expressed, restore what was previously set:
            this.spaceStack.pop();
            if (0 == this.spaceStack.size()) {
                // This is the default...
                this.preserveWhitespace = preserveWhitespaceTemp;
            } else {
                this.preserveWhitespace = ("preserve".equals(this.spaceStack.peek()));
            }
        }
    }

	/**
	 * @see org.xml.sax.ContentHandler#processingInstruction(java.lang.String, java.lang.String)
	 */
	public void processingInstruction(String target, String data)
		throws SAXException {
		if (inModification && charBuf.length() > 0) {
			final String normalized =
				charBuf.getNormalizedString(FastStringBuffer.SUPPRESS_BOTH);
			if (normalized.length() > 0) {
				Text text = doc.createTextNode(normalized);
				if (stack.isEmpty()) {
					LOG.debug("appending text to fragment: " + text.getData());
					contents.add(text);
				} else {
					Element last = (Element) stack.peek();
					last.appendChild(text);
				}
			}
			charBuf.setLength(0);
		}
		if (inModification) {
			ProcessingInstruction pi =
				doc.createProcessingInstruction(target, data);
			if (stack.isEmpty()) {
				contents.add(pi);
			} else {
				Element last = (Element) stack.peek();
				last.appendChild(pi);
			}
		}
	}

	/**
	 * @see org.xml.sax.ContentHandler#skippedEntity(java.lang.String)
	 */
	public void skippedEntity(String name) throws SAXException {
	}

	private void createVariable(String name, String select)
		throws SAXException {
		LOG.debug("creating variable " + name + " as " + select);
		Sequence result = processQuery(select);
		LOG.debug("found " + result.getLength() + " for variable " + name);
		variables.put(name, result);
	}
	
	private Sequence processQuery(String select) throws SAXException {
		try {
			XQueryContext context = new XQueryContext(broker);
			context.setStaticallyKnownDocuments(documentSet);
			Map.Entry entry;
			for (Iterator i = namespaces.entrySet().iterator(); i.hasNext();) {
				entry = (Map.Entry) i.next();
				context.declareNamespace(
					(String) entry.getKey(),
					(String) entry.getValue());
			}
			for (Iterator i = variables.entrySet().iterator(); i.hasNext(); ) {
				entry = (Map.Entry) i.next();
				context.declareVariable(entry.getKey().toString(), entry.getValue());
			}
			XQueryLexer lexer = new XQueryLexer(context, new StringReader(select));
			XQueryParser parser = new XQueryParser(lexer);
			XQueryTreeParser treeParser = new XQueryTreeParser(context);
			parser.xpath();
			if (parser.foundErrors()) {
				throw new SAXException(parser.getErrorMessage());
			}

			AST ast = parser.getAST();
			LOG.debug("generated AST: " + ast.toStringTree());

			PathExpr expr = new PathExpr(context);
			treeParser.xpath(ast, expr);
			if (treeParser.foundErrors()) {
				throw new SAXException(treeParser.getErrorMessage());
			}
			expr.analyze(null, 0);
			Sequence seq = expr.eval(null, null);
			return seq;
		} catch (RecognitionException e) {
			LOG.warn("error while creating variable", e);
			throw new SAXException(e);
		} catch (TokenStreamException e) {
			LOG.warn("error while creating variable", e);
			throw new SAXException(e);
		} catch (XPathException e) {
			throw new SAXException(e);
		}
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ext.LexicalHandler#comment(char[], int, int)
	 */
	public void comment(char[] ch, int start, int length) throws SAXException {
		if (inModification && charBuf.length() > 0) {
			final String normalized =
				charBuf.getNormalizedString(FastStringBuffer.SUPPRESS_BOTH);
			if (normalized.length() > 0) {
				Text text = doc.createTextNode(normalized);
				if (stack.isEmpty()) {
					//LOG.debug("appending text to fragment: " + text.getData());
					contents.add(text);
				} else {
					Element last = (Element) stack.peek();
					last.appendChild(text);
				}
			}
			charBuf.setLength(0);
		}
		if (inModification) {
			Comment comment = doc.createComment(new String(ch, start, length));
			if (stack.isEmpty()) {
				contents.add(comment);
			} else {
				Element last = (Element) stack.peek();
				last.appendChild(comment);
			}
		}
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ext.LexicalHandler#endCDATA()
	 */
	public void endCDATA() throws SAXException {
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ext.LexicalHandler#endDTD()
	 */
	public void endDTD() throws SAXException {
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ext.LexicalHandler#endEntity(java.lang.String)
	 */
	public void endEntity(String name) throws SAXException {
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ext.LexicalHandler#startCDATA()
	 */
	public void startCDATA() throws SAXException {
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ext.LexicalHandler#startDTD(java.lang.String, java.lang.String, java.lang.String)
	 */
	public void startDTD(String name, String publicId, String systemId)
		throws SAXException {
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ext.LexicalHandler#startEntity(java.lang.String)
	 */
	public void startEntity(String name) throws SAXException {
	}

	public void reset() {
        this.preserveWhitespace = false;
        this.spaceStack = null;
        
	    this.inModification = false;
		this.inAttribute = false;
		this.modification = null;
		this.doc = null;
		this.contents = null;
		this.stack.clear();
		this.currentNode = null;
		this.broker = null;
		this.documentSet = null;
		this.modifications.clear();
		this.charBuf = new FastStringBuffer(6, 15, 5);
		this.variables.clear();
		this.namespaces.clear();
		this.conditionals.clear();
		this.namespaces.put("xml", "http://www.w3.org/XML/1998/namespace");
	}
}
