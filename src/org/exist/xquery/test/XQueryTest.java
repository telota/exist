package org.exist.xquery.test;

import java.io.File;

import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xmldb.EXistResource;
import org.w3c.dom.Node;
import org.w3c.dom.Element;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceIterator;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XPathQueryService;

import org.custommonkey.xmlunit.*;

public class XQueryTest extends XMLTestCase {

	private static final String NUMBERS_XML = "numbers.xml";
	private static final String MODULE1_NAME = "module1.xqm";
	private static final String MODULE2_NAME = "module2.xqm";
	private static final String FATHER_MODULE_NAME = "father.xqm";
	private static final String CHILD1_MODULE_NAME = "child1.xqm";
	private static final String CHILD2_MODULE_NAME = "child2.xqm";
	private final static String URI = "xmldb:exist:///db";

	private final static String numbers =
		"<test>"
			+ "<item id='1'><price>5.6</price><stock>22</stock></item>"
			+ "<item id='2'><price>7.4</price><stock>43</stock></item>"
			+ "<item id='3'><price>18.4</price><stock>5</stock></item>"
			+ "<item id='4'><price>65.54</price><stock>16</stock></item>"
			+ "</test>";

	private final static String module1 =
		"module namespace blah=\"blah\";\n"
		+ "declare variable $blah:param {\"value-1\"};";

	private final static String module2 =
		"module namespace foo=\"\";\n"
		+ "declare variable $foo:bar {\"bar\"};";
	
	private final static String fatherModule =
		"module namespace foo=\"foo\";\n"
		+ "import module namespace foo1=\"foo1\" at \"" + URI + "/test/" + CHILD1_MODULE_NAME + "\";\n"
		+ "import module namespace foo2=\"foo2\" at \"" + URI + "/test/" + CHILD2_MODULE_NAME + "\";\n"
		+ "declare variable $foo:bar { \"bar\" };\n "
	    + "declare variable $foo:bar1 { $foo1:bar };\n"
	    + "declare variable $foo:bar2 { $foo2:bar };\n";

	private final static String child1Module =
		"module namespace foo=\"foo1\";\n"
		+ "declare variable $foo:bar {\"bar1\"};";	

	private final static String child2Module =
		"module namespace foo=\"foo2\";\n"
		+ "declare variable $foo:bar {\"bar2\"};";	
	
	private Collection testCollection;
	private static String attributeXML;
	private static int stringSize;
	private static int nbElem;
	private String file_name = "detail_xml.xml";
	private String xml;
	private Database database;

	public XQueryTest(String arg0) {
		super(arg0);
	}

	protected void setUp() {
		try {
			// initialize driver
			Class cl = Class.forName("org.exist.xmldb.DatabaseImpl");
			database = (Database) cl.newInstance();
			database.setProperty("create-database", "true");
			DatabaseManager.registerDatabase(database);
			
			Collection root =
				DatabaseManager.getCollection(
					"xmldb:exist:///db",
					"admin",
					null);
			CollectionManagementService service =
				(CollectionManagementService) root.getService(
					"CollectionManagementService",
					"1.0");
			testCollection = service.createCollection("test");
			assertNotNull(testCollection);

		} catch (ClassNotFoundException e) {
		} catch (InstantiationException e) {
		} catch (IllegalAccessException e) {
		} catch (XMLDBException e) {
			e.printStackTrace();
		}
	}

	/*
	 * @see TestCase#tearDown()
	 */
	protected void tearDown() throws Exception {
		// testCollection.removeResource( testCollection .getResource(file_name));
		
		DatabaseManager.deregisterDatabase(database);
		DatabaseInstanceManager dim =
			(DatabaseInstanceManager) testCollection.getService(
				"DatabaseInstanceManager", "1.0");
		dim.shutdown();
		System.out.println("tearDown PASSED");
	}
	
	public void testFor() {
		ResourceSet result;
		String query;
		XMLResource resu;
		try {
			XPathQueryService service = 
				storeXMLStringAndGetQueryService(NUMBERS_XML, numbers);

			System.out.println("testFor 1: ========" );
			query = "for $f in /*/item return $f";
			result = service.queryResource(NUMBERS_XML, query );
			printResult(result);
			assertEquals( "XQuery: " + query, 4, result.getSize() );

			System.out.println("testFor 2: ========" );
			query = "for $f in /*/item  order by $f ascending  return $f";
			result = service.queryResource(NUMBERS_XML, query );
			printResult(result);
			resu = (XMLResource) result.getResource(0);
			assertEquals( "XQuery: " + query, "3", ((Element)resu.getContentAsDOM()).getAttribute("id") );

			System.out.println("testFor 3: ========" );
			query = "for $f in /*/item  order by $f descending  return $f";
			result = service.queryResource(NUMBERS_XML, query );
			printResult(result);
			resu = (XMLResource) result.getResource(0);
			assertEquals( "XQuery: " + query, "2", ((Element)resu.getContentAsDOM()).getAttribute("id") );

			System.out.println("testFor 4: ========" );
			query = "for $f in /*/item  order by xs:double($f/price) descending  return $f";
			result = service.queryResource(NUMBERS_XML, query );
			printResult(result);
			resu = (XMLResource) result.getResource(0);
			assertEquals( "XQuery: " + query, "4", ((Element)resu.getContentAsDOM()).getAttribute("id") );
		} catch (XMLDBException e) {
			System.out.println("testFor(): XMLDBException: "+e);
			fail(e.getMessage());
		}
	}
	
	public void testVariable() {
		ResourceSet result;
		String query;
		XMLResource resu;
		boolean exceptionThrown;
		String message;				
		try {
			XPathQueryService service =
				(XPathQueryService) testCollection.getService(
					"XPathQueryService",
					"1.0");

            System.out.println("testVariable 1: ========" );
            query = "xquery version \"1.0\";\n"                 
                + "declare namespace param=\"param\";\n"
                + "declare variable $param:a {\"a\"};\n"
                + "declare function param:a() {$param:a};\n"
                + "let $param:a := \"b\" \n"
                + "return ($param:a, $param:a)";            
            result = service.query(query);
            printResult(result);
            assertEquals( "XQuery: " + query, 2, result.getSize() );
            assertEquals( "XQuery: " + query, "b", ((XMLResource)result.getResource(0)).getContent());
            assertEquals( "XQuery: " + query, "b", ((XMLResource)result.getResource(1)).getContent());
            
            System.out.println("testVariable 2: ========" );
			query = "xquery version \"1.0\";\n" 				
				+ "declare namespace param=\"param\";\n"
				+ "declare variable $param:a {\"a\"};\n"
				+ "declare function param:a() {$param:a};\n"
				+ "let $param:a := \"b\" \n"
				+ "return param:a(), param:a()";				
			result = service.query(query);
			printResult(result);
			assertEquals( "XQuery: " + query, 2, result.getSize() );
			assertEquals( "XQuery: " + query, "a", ((XMLResource)result.getResource(0)).getContent());
			assertEquals( "XQuery: " + query, "a", ((XMLResource)result.getResource(1)).getContent());
			
            System.out.println("testVariable 3: ========" );
			query = "declare variable $foo {\"foo1\"};\n"				
				+ "let $foo := \"foo2\" \n"
				+ "for $bar in (1 to 1) \n"
				+ "  let $foo := \"foo3\" \n"
				+ "  return $foo";				
			result = service.query(query);
			printResult(result);
			assertEquals( "XQuery: " + query, 1, result.getSize() );
			assertEquals( "XQuery: " + query, "foo3", ((XMLResource)result.getResource(0)).getContent());
			
			try {
				message = "";
				System.out.println("testVariable 4 ========" );
				query = "xquery version \"1.0\";\n" 				
					+ "declare variable $a {\"1st instance\"};\n"
					+ "declare variable $a {\"2nd instance\"};\n"
					+ "$a";
				result = service.query(query);
			} catch (XMLDBException e) {
				message = e.getMessage();
			}
			assertTrue(message.indexOf("XQST0049") > -1);
						
			System.out.println("testVariable 5: ========" );
			query = "xquery version \"1.0\";\n" 				
				+ "declare namespace param=\"param\";\n"				
				+ "declare function param:f() { $param:a };\n"
				+ "declare variable $param:a {\"a\"};\n"
				+ "param:f()";				
			result = service.query(query);
			printResult(result);
			assertEquals( "XQuery: " + query, 1, result.getSize() );
			assertEquals( "XQuery: " + query, "a", ((XMLResource)result.getResource(0)).getContent());
			
		} catch (XMLDBException e) {
			System.out.println("testVariable : XMLDBException: "+e);
			fail(e.getMessage());
		}
	}			
	
	public void testTypedVariables() {
		ResourceSet result;
		String query;
		boolean exceptionThrown;
		String message;		
		try {
			XPathQueryService service = 
				storeXMLStringAndGetQueryService(NUMBERS_XML, numbers);

			System.out.println("testTypedVariables 1: ========" );
			query = "let $v as element()* := ( <assign/> , <assign/> )\n" 
				+ "let $w := <r>{ $v }</r>\n"
				+ "let $x as element()* := $w/assign\n"
				+ "return $x";
			result = service.query(query);				
			assertEquals( "XQuery: " + query, 2, result.getSize() );
			assertEquals( "XQuery: " + query, Node.ELEMENT_NODE, ((XMLResource)result.getResource(0)).getContentAsDOM().getNodeType());
			assertEquals( "XQuery: " + query, "assign", ((XMLResource)result.getResource(0)).getContentAsDOM().getNodeName());

			System.out.println("testTypedVariables 2: ========" );
			query = "let $v as node()* := ()\n" 
			+ "return $v";
			result = service.query(query);		
			assertEquals( "XQuery: " + query, 0, result.getSize() );
			
			System.out.println("testTypedVariables 3: ========" );
			query = "let $v as item()* := ()\n" 
			+ "return $v";
			result = service.query(query);		
			assertEquals( "XQuery: " + query, 0, result.getSize() );			

			System.out.println("testTypedVariables 4: ========" );
			query = "let $v as empty() := ()\n" 
			+ "return $v";
			result = service.query(query);		
			assertEquals( "XQuery: " + query, 0, result.getSize() );			
			
			System.out.println("testTypedVariables 5: ========" );
			query = "let $v as item() := ()\n" 
			+ "return $v";			
			try {
				exceptionThrown = false;
				result = service.query(query);						
			} catch (XMLDBException e) {
				exceptionThrown = true;
				message = e.getMessage();
			}
			assertTrue("XQuery: " + query, exceptionThrown);
			
			System.out.println("testTypedVariables 6: ========" );
			query = "let $v as item()* := ( <a/> , 1 )\n" 
				+ "return $v";
			result = service.query(query);		
			assertEquals( "XQuery: " + query, 2, result.getSize() );	
			assertEquals( "XQuery: " + query, Node.ELEMENT_NODE, ((XMLResource)result.getResource(0)).getContentAsDOM().getNodeType());
			assertEquals( "XQuery: " + query, "a", ((XMLResource)result.getResource(0)).getContentAsDOM().getNodeName());			
			assertEquals( "XQuery: " + query, "1", ((XMLResource)result.getResource(1)).getContent());		
			
			System.out.println("testTypedVariables 7: ========" );
			query = "let $v as node()* := ( <a/> , 1 )\n" 
				+ "return $v";			
			try {
				exceptionThrown = false;
				result = service.query(query);		
			} catch (XMLDBException e) {
				exceptionThrown = true;
				message = e.getMessage();
			}
			assertTrue(exceptionThrown);	
			
			System.out.println("testTypedVariables 8: ========" );
			query = "let $v as item()* := ( <a/> , 1 )\n" 
				+ "let $w as element()* := $v\n"
				+ "return $w";		
			try {
				exceptionThrown = false;
				result = service.query(query);		
				result = service.query(query);		
			} catch (XMLDBException e) {
				exceptionThrown = true;
				message = e.getMessage();
			}
			assertTrue(exceptionThrown);	
			
			System.out.println("testTypedVariables 9: ========" );
			query = "declare variable $v as element()* {( <assign/> , <assign/> ) };\n" 
				+ "declare variable $w { <r>{ $v }</r> };\n"
				+ "declare variable $x as element()* { $w/assign };\n"
				+ "$x";
			result = service.query(query);				
			assertEquals( "XQuery: " + query, 2, result.getSize() );
			assertEquals( "XQuery: " + query, Node.ELEMENT_NODE, ((XMLResource)result.getResource(0)).getContentAsDOM().getNodeType());
			assertEquals( "XQuery: " + query, "assign", ((XMLResource)result.getResource(0)).getContentAsDOM().getNodeName());
			
			System.out.println("testTypedVariables 10: ========" );
			query = "declare variable $v as node()* { () };\n" 
			+ "$v";
			result = service.query(query);		
			assertEquals( "XQuery: " + query, 0, result.getSize() );
			
			System.out.println("testTypedVariables 11: ========" );
			query = "declare variable $v as item()* { () };\n" 
			+ "$v";
			result = service.query(query);		
			assertEquals( "XQuery: " + query, 0, result.getSize() );			

			System.out.println("testTypedVariables 12: ========" );
			query = "declare variable $v as empty() { () };\n" 
			+ "$v";
			result = service.query(query);		
			assertEquals( "XQuery: " + query, 0, result.getSize() );			
			
			System.out.println("testTypedVariables 13: ========" );
			query = "declare variable $v as item() { () };\n" 
			+ "$v";			
			try {
				exceptionThrown = false;
				result = service.query(query);						
			} catch (XMLDBException e) {
				exceptionThrown = true;
				message = e.getMessage();
			}
			assertTrue("XQuery: " + query, exceptionThrown);
			
			System.out.println("testTypedVariables 14: ========" );
			query = "declare variable $v as item()* { ( <a/> , 1 ) }; \n" 
				+ "$v";
			result = service.query(query);		
			assertEquals( "XQuery: " + query, 2, result.getSize() );	
			assertEquals( "XQuery: " + query, Node.ELEMENT_NODE, ((XMLResource)result.getResource(0)).getContentAsDOM().getNodeType());
			assertEquals( "XQuery: " + query, "a", ((XMLResource)result.getResource(0)).getContentAsDOM().getNodeName());			
			assertEquals( "XQuery: " + query, "1", ((XMLResource)result.getResource(1)).getContent());		
			
			System.out.println("testTypedVariables 15: ========" );
			query = "declare variable $v as node()* { ( <a/> , 1 ) };\n" 
				+ "$v";			
			try {
				exceptionThrown = false;
				result = service.query(query);		
			} catch (XMLDBException e) {
				exceptionThrown = true;
				message = e.getMessage();
			}
			assertTrue(exceptionThrown);	
			
			System.out.println("testTypedVariables 16: ========" );
			query = "declare variable $v as item()* { ( <a/> , 1 ) };\n" 
				+ "declare variable $w as element()* { $v };\n"
				+ "$w";		
			try {
				exceptionThrown = false;
				result = service.query(query);		
				result = service.query(query);		
			} catch (XMLDBException e) {
				exceptionThrown = true;
				message = e.getMessage();
			}
			assertTrue(exceptionThrown);				

		
		} catch (XMLDBException e) {
			System.out.println("testTypedVariables : XMLDBException: "+e);
			fail(e.getMessage());
		}
	}	
	
	public void testPrecedence() {
		ResourceSet result;
		String query;
		boolean exceptionThrown;
		String message;		
		try {
			XPathQueryService service = 
				storeXMLStringAndGetQueryService(NUMBERS_XML, numbers);
	
			System.out.println("testPrecedence 1: ========" );
			query = "xquery version \"1.0\";\n" 
				+ "declare namespace blah=\"blah\";\n"
				+ "declare variable $blah:param  {\"value-1\"};\n"
				+ "let $blah:param := \"value-2\"\n"
				+ "(:: FLWOR expressions have a higher precedence than the comma operator ::)\n"
				+ "return $blah:param, $blah:param ";
			result = service.query(query);				
			assertEquals( "XQuery: " + query, 2, result.getSize() );
			assertEquals( "XQuery: " + query, "value-2", ((XMLResource)result.getResource(0)).getContent());
			assertEquals( "XQuery: " + query, "value-1", ((XMLResource)result.getResource(1)).getContent());
		
		} catch (XMLDBException e) {
			System.out.println("testTypedVariables : XMLDBException: "+e);
			fail(e.getMessage());
		}
	}	
	
	public void bugtestNamespace() {
		Resource doc;
		ResourceSet result;
		String query;
		XMLResource resu;
		boolean exceptionThrown;
		String message;				
		try {
			doc = testCollection.createResource(MODULE1_NAME, "BinaryResource");
			doc.setContent(module1);	
			((EXistResource) doc).setMimeType("application/xquery");
			testCollection.storeResource(doc);

			doc = testCollection.createResource(MODULE2_NAME, "BinaryResource");
			doc.setContent(module2);	
			((EXistResource) doc).setMimeType("application/xquery");
			testCollection.storeResource(doc);			
			
			XPathQueryService service =
				(XPathQueryService) testCollection.getService(
					"XPathQueryService",
					"1.0");
			
//			TODO : this should not work (empty namespace)
			System.out.println("testNamespace 1: ========" );
			query = "xquery version \"1.0\";\n" 				
				+ "(:: empty namespace ::)\n"
				+ "declare namespace blah=\"\";\n"					
				+ "\"OK\"";
			try {
				exceptionThrown = false;			
				result = service.query(query);
				printResult(result);
			} catch (XMLDBException e) {
				exceptionThrown = true;
				message = e.getMessage();
			}
			//assertTrue(exceptionThrown);
			
			System.out.println("testNamespace 2: ========" );
			query = "xquery version \"1.0\";\n" 
				+ "import module namespace blah=\"blah\" at \"" + URI + "/test/" + MODULE1_NAME + "\";\n"
				+ "(:: redefine existing prefix ::)\n"
				+ "declare namespace blah=\"blah\";\n"		
				+ "$blah:param";
			try {
				message = "";			
				result = service.query(query);
			} catch (XMLDBException e) {
				message = e.getMessage();
			}
			assertTrue(message.indexOf("XQST0033") > -1);

			System.out.println("testNamespace 3: ========" );
			query = "xquery version \"1.0\";\n" 
				+ "import module namespace blah=\"blah\" at \"" + URI + "/test/" + MODULE1_NAME + "\";\n"
				+ "(:: redefine existing prefix ::)\n"
				+ "declare namespace blah=\"bla\";\n"
				+ "declare variable $blah:param  {\"value-2\"};\n"			
				+ "$blah:param";
			try {
				message = "";			
				result = service.query(query);
			} catch (XMLDBException e) {
				message = e.getMessage();
			}
			assertTrue(message.indexOf("XQST0033") >  -1);
			
//			TODO : this should work (emty namespace allowed)
			try {
				System.out.println("testNamespace 4: ========" );
				query = "xquery version \"1.0\";\n" 
					+ "import module namespace foo=\"\" at \"" + URI + "/test/" + MODULE2_NAME + "\";\n"
					+ "$foo:bar";			
				result = service.query(query);	
				printResult(result);	
				assertEquals( "XQuery: " + query, 1, result.getSize() );
				assertEquals( "XQuery: " + query, "bar", ((XMLResource)result.getResource(0)).getContent());
			} catch (XMLDBException e) {
				message = e.getMessage();
			}				
			
//			TODO : this should work (emty namespace allowed)		
			try {
				System.out.println("testNamespace 5: ========" );
				query = "xquery version \"1.0\";\n" 
					+ "import module namespace foo=\"\" at \"" + URI + "/test/" + MODULE2_NAME + "\";\n"
					+ "$bar";			
				result = service.query(query);	
				printResult(result);	
				assertEquals( "XQuery: " + query, 1, result.getSize() );
				assertEquals( "XQuery: " + query, "bar", ((XMLResource)result.getResource(0)).getContent());			
			} catch (XMLDBException e) {
				message = e.getMessage();
			}				
			
			//Interesting one : let's see with XQuery gurus :-)
			//declare namespace fn="";
			//fn:current-time()
			/*
			 If the URILiteral part of a namespace declaration is a zero-length string, 
			 any existing namespace binding for the given prefix is removed from 
			 the statically known namespaces. This feature provides a way 
			 to remove predeclared namespace prefixes such as local.
			 */
			
		} catch (XMLDBException e) {
			System.out.println("testNamespace : XMLDBException: "+e);
			fail(e.getMessage());
		}			
	}

	public void testModule() {
		Resource doc;
		ResourceSet result;
		String query;
		XMLResource resu;
		boolean exceptionThrown;
		String message;				
		try {
			doc = testCollection.createResource(MODULE1_NAME, "BinaryResource");
			doc.setContent(module1);	
			((EXistResource) doc).setMimeType("application/xquery");
			testCollection.storeResource(doc);
			
			doc = testCollection.createResource(FATHER_MODULE_NAME, "BinaryResource");
			doc.setContent(fatherModule);	
			((EXistResource) doc).setMimeType("application/xquery");
			testCollection.storeResource(doc);

			doc = testCollection.createResource(CHILD1_MODULE_NAME, "BinaryResource");
			doc.setContent(child1Module);	
			((EXistResource) doc).setMimeType("application/xquery");
			testCollection.storeResource(doc);	

			doc = testCollection.createResource(CHILD2_MODULE_NAME, "BinaryResource");
			doc.setContent(child2Module);	
			((EXistResource) doc).setMimeType("application/xquery");
			testCollection.storeResource(doc);			
			
			XPathQueryService service =
				(XPathQueryService) testCollection.getService(
					"XPathQueryService",
					"1.0");
			
			System.out.println("testModule 1: ========" );
			query = "xquery version \"1.0\";\n" 
				+ "import module namespace blah=\"blah\" at \"" + URI + "/test/" + MODULE1_NAME + "\";\n"
				+ "$blah:param";			
			result = service.query(query);	
			printResult(result);	
			assertEquals( "XQuery: " + query, 1, result.getSize() );
			assertEquals( "XQuery: " + query, "value-1", ((XMLResource)result.getResource(0)).getContent());
			
			System.out.println("testModule 2: ========" );
			query = "xquery version \"1.0\";\n" 
				+ "import module namespace blah=\"blah\" at \"" + URI + "/test/" + MODULE1_NAME + "\";\n"
				+ "(:: redefine variable ::)\n"
				+ "declare variable $blah:param  {\"value-2\"};\n"			
				+ "$blah:param";
			try {
				exceptionThrown = false;			
				result = service.query(query);	
				printResult(result);	
			} catch (XMLDBException e) {
				exceptionThrown = true;
				message = e.getMessage();				
			}			
			assertTrue(exceptionThrown);
			
			System.out.println("testModule 3: ========" );
			query = "xquery version \"1.0\";\n" 
				+ "import module namespace blah=\"blah\" at \"" + URI + "/test/" + MODULE1_NAME + "\";\n"
				+ "declare namespace blah2=\"blah\";\n"		
				+ "$blah2:param";
			result = service.query(query);
			printResult(result);
			assertEquals( "XQuery: " + query, 1, result.getSize() );
			assertEquals( "XQuery: " + query, "value-1", ((XMLResource)result.getResource(0)).getContent());	
			
			System.out.println("testModule 4: ========" );
			query = "xquery version \"1.0\";\n" 
				+ "import module namespace blah=\"bla\" at \"" + URI + "/test/" + MODULE1_NAME + "\";\n"					
				+ "$blah:param";
			try {
				exceptionThrown = false;			
				result = service.query(query);	
				printResult(result);	
			} catch (XMLDBException e) {
				exceptionThrown = true;
				message = e.getMessage();				
			}			
			assertTrue(exceptionThrown);
			
			System.out.println("testModule 5: ========" );
			query = "xquery version \"1.0\";\n" 
				+ "import module namespace foo=\"foo\" at \"" + URI + "/test/" + FATHER_MODULE_NAME + "\";\n"					
				+ "$foo:bar, $foo:bar1, $foo:bar2";					
			result = service.query(query);	
			printResult(result);	
			assertEquals( "XQuery: " + query, 3, result.getSize() );
			assertEquals( "XQuery: " + query, "bar", ((XMLResource)result.getResource(0)).getContent());		
			assertEquals( "XQuery: " + query, "bar1", ((XMLResource)result.getResource(1)).getContent());		
			assertEquals( "XQuery: " + query, "bar2", ((XMLResource)result.getResource(2)).getContent());		
			
//			Non-heritance check
			System.out.println("testModule 6: ========" );
			query = "xquery version \"1.0\";\n" 
				+ "import module namespace foo=\"foo\" at \"" + URI + "/test/" + FATHER_MODULE_NAME + "\";\n"
				+ "declare namespace foo1=\"foo1\"; \n"
				+ "$foo1:bar";
			try {
				exceptionThrown = false;			
				result = service.query(query);	
				printResult(result);	
			} catch (XMLDBException e) {
				exceptionThrown = true;
				message = e.getMessage();				
			}			
			assertTrue(exceptionThrown);
			
//			Non-heritance check
			System.out.println("testModule 7: ========" );
			query = "xquery version \"1.0\";\n" 
				+ "import module namespace foo=\"foo\" at \"" + URI + "/test/" + FATHER_MODULE_NAME + "\";\n"	
				+ "declare namespace foo2=\"foo2\"; \n"
				+ "$foo2:bar";
			try {
				exceptionThrown = false;			
				result = service.query(query);	
				printResult(result);	
			} catch (XMLDBException e) {
				exceptionThrown = true;
				message = e.getMessage();				
			}			
			assertTrue(exceptionThrown);			
			
		} catch (XMLDBException e) {
			System.out.println("testModule : XMLDBException: "+e);
			fail(e.getMessage());
		}
	}	
	
	public void testFunctionDoc() {
		ResourceSet result;
		String query;
		boolean exceptionThrown;
		String message;		
		try {
			XPathQueryService service = 
				storeXMLStringAndGetQueryService(NUMBERS_XML, numbers);

			System.out.println("testFunctionDoc 1: ========" );				
			query ="doc(\"/db/test/" + NUMBERS_XML +  "\")";	
			result = service.query(query);
			assertEquals( "XQuery: " + query, 1, result.getSize() );	
			try {				
				Node n = ((XMLResource)result.getResource(0)).getContentAsDOM();	
				DetailedDiff d = new DetailedDiff(compareXML(numbers, n.toString()));
				//ignore eXist namespace's attributes
				//TODO : should be improved !
				assertEquals(1, d.getAllDifferences().size());
			} catch (Exception e) {
				System.out.println("testFunctionDoc : XMLDBException: "+e);
				fail(e.getMessage());
			}
			
			System.out.println("testFunctionDoc 2: ========" );				
			query ="doc(\"http://www.w3.org/RDF/\")";	
			result = service.query(query);
			assertEquals( "XQuery: " + query, 1, result.getSize() );	
			
			System.out.println("testFunctionDoc 3: ========" );				
			query = "let $v := ()\n" 
				+ "return doc($v)";	
			result = service.query(query);
			assertEquals( "XQuery: " + query, 0, result.getSize() );			
			
			System.out.println("testFunctionDoc 4: ========" );		
			query ="doc(\"/db/test/dummy" + NUMBERS_XML +  "\")";	
			try {
				exceptionThrown = false;
				result = service.query(query);		
			} catch (XMLDBException e) {
				exceptionThrown = true;
				message = e.getMessage();
			}
			assertTrue(exceptionThrown);	
			
			System.out.println("testFunctionDoc 5: ========" );		
			query ="doc(\"http://www.w3.org/RDF/dummy\")";	
			try {
				exceptionThrown = false;
				result = service.query(query);		
			} catch (XMLDBException e) {
				exceptionThrown = true;
				message = e.getMessage();
			}
			assertTrue(exceptionThrown);				
			
			System.out.println("testFunctionDoc 6: ========" );				
			query ="doc-available(\"/db/test/" + NUMBERS_XML +  "\")";	
			result = service.query(query);
			assertEquals( "XQuery: " + query, 1, result.getSize() );
			assertEquals( "XQuery: " + query, "true", result.getResource(0).getContent());
			
			System.out.println("testFunctionDoc 7: ========" );				
			query ="doc-available(\"http://www.w3.org/RDF/\")";	
			result = service.query(query);
			assertEquals( "XQuery: " + query, 1, result.getSize() );
			assertEquals( "XQuery: " + query, "true", result.getResource(0).getContent());
			
			System.out.println("testFunctionDoc 8: ========" );				
			query = "let $v := ()\n" 
				+ "return doc-available($v)";	
			result = service.query(query); 
			assertEquals( "XQuery: " + query, 1, result.getSize() );
			assertEquals( "XQuery: " + query, "false", result.getResource(0).getContent());
			
			System.out.println("testFunctionDoc 9: ========" );		
			query ="doc-available(\"/db/test/dummy" + NUMBERS_XML +  "\")";	
			assertEquals( "XQuery: " + query, 1, result.getSize() );
			assertEquals( "XQuery: " + query, "false", result.getResource(0).getContent());	
			
			System.out.println("testFunctionDoc 10: ========" );		
			query ="doc-available(\"http://www.w3.org/RDF/dummy\")";	
			assertEquals( "XQuery: " + query, 1, result.getSize() );
			assertEquals( "XQuery: " + query, "false", result.getResource(0).getContent());			

			System.out.println("testFunctionDoc 11: ========" );
			//A redirected 404
			query ="doc-available(\"http://java.sun.com/404\")";	
			assertEquals( "XQuery: " + query, 1, result.getSize() );
			assertEquals( "XQuery: " + query, "false", result.getResource(0).getContent());			

			
		} catch (XMLDBException e) {
			System.out.println("testFunctionDoc : XMLDBException: "+e);
			fail(e.getMessage());
		}
	}		
	
	private String makeString(int n){
		StringBuffer b = new StringBuffer();
		char c = 'a';
		for ( int i=0; i<n; i++ ) {
			b.append(c);
		}
		return b.toString();
	}

	public void testLargeAttributeSimple() {
		ResourceSet result;
		String query;
		XMLResource resu;
		try {
			System.out.println("testLargeAttributeSimple 1: ========" );
			String large = createXMLContentWithLargeString();
			XPathQueryService service = 
				storeXMLStringAndGetQueryService(file_name, xml);
			
			query = "doc('"+ file_name+"') / details/metadata[@docid= '" + large + "' ]";
			result = service.queryResource(file_name, query );
			printResult(result);
			assertEquals( "XQuery: " + query, nbElem, result.getSize() );
		} catch (XMLDBException e) {
			System.out.println("testLargeAttributeSimple(): XMLDBException: "+e);
			fail(e.getMessage());
		}
	}

	public void testLargeAttributeContains() {
		ResourceSet result;
		String query;
		XMLResource resu;
		try {
			System.out.println("testLargeAttributeSimple 1: ========" );
			String large = createXMLContentWithLargeString();
			XPathQueryService service = 
				storeXMLStringAndGetQueryService(file_name, xml);

			query = "doc('"+ file_name+"') / details/metadata[ contains(@docid, 'aa') ]";
			result = service.queryResource(file_name, query );
			assertEquals( "XQuery: " + query, nbElem, result.getSize() );
		} catch (XMLDBException e) {
			System.out.println("testLargeAttributeSimple(): XMLDBException: "+e);
			fail(e.getMessage());
		}
	}

	public void testLargeAttributeKeywordOperator() {
		ResourceSet result;
		String query;
		XMLResource resu;
		try {
			System.out.println("testLargeAttributeSimple 1: ========" );
			String large = createXMLContentWithLargeString();
			XPathQueryService service = 
				storeXMLStringAndGetQueryService(file_name, xml);

			query = "doc('"+ file_name+"') / details/metadata[ @docid &= '" + large + "' ]";
			result = service.queryResource(file_name, query );
			assertEquals( "XQuery: " + query, nbElem, result.getSize() );
		} catch (XMLDBException e) {
			System.out.println("testLargeAttributeSimple(): XMLDBException: "+e);
			fail(e.getMessage());
		}
	}
	
	/** CAUTION side effect on field xml
	 * @return the large string contained in the atrbute(s)
	 */
	private String createXMLContentWithLargeString() {
		String large = makeString(stringSize);
		String head = "<details format='xml'>";
		String elem = "<metadata docid='" + large + "'></metadata>";
		String tail = "</details>";
		xml = head;
		for ( int i=0; i< nbElem; i++ )
			xml += elem;
		xml += tail;
		System.out.println("XML:\n" + xml);
		return large;
	}

	public void testRetrieveLargeAttribute() throws XMLDBException{
		System.out.println("testRetrieveLargeAttribute 1: ========" );
		XMLResource res = (XMLResource) testCollection.getResource(file_name);
		System.out.println("res.getContent(): " + res.getContent() );
	}
	
	/** This test is obsolete because testLargeAttributeSimple() reproduces the problem without a file,
	 * but I keep it to show how one can test with an XML file. */
	public void obsoleteTestLargeAttributeRealFile() {
		ResourceSet result;
		String query;
		XMLResource resu;
		try {
			System.out.println("testLargeAttributeRealFile 1: ========" );
			String large;
			large = "challengesininformationretrievalandlanguagemodelingreportofaworkshopheldatthecenterforintelligentinformationretrievaluniversityofmassachusettsamherstseptember2002-extdocid-howardturtlemarksandersonnorbertfuhralansmeatonjayaslamdragomirradevwesselkraaijellenvoorheesamitsinghaldonnaharmanjaypontejamiecallannicholasbelkinjohnlaffertylizliddyronirosenfeldvictorlavrenkodavidjharperrichschwartzjohnpragerchengxiangzhaijinxixusalimroukosstephenrobertsonandrewmccallumbrucecroftrmanmathasuedumaisdjoerdhiemstraeduardhovyralphweischedelthomashofmannjamesallanchrisbuckleyphilipresnikdavidlewis2003";
			if (attributeXML != null)
				large = attributeXML;
			String xml = "<details format='xml'><metadata docid='" + large +
				"'></metadata></details>";
			final String FILE_NAME = "detail_xml.xml";
			XPathQueryService service = 
				storeXMLStringAndGetQueryService(FILE_NAME);

			query = "doc('"+ FILE_NAME+"') / details/metadata[@docid= '" + large + "' ]"; // fails !!!
			// query = "doc('"+ FILE_NAME+"') / details/metadata[ docid= '" + large + "' ]"; // test passes!
			result = service.queryResource(FILE_NAME, query );
			printResult(result);
			assertEquals( "XQuery: " + query, 2, result.getSize() );
		} catch (XMLDBException e) {
			System.out.println("testLargeAttributeRealFile(): XMLDBException: "+e);
			fail(e.getMessage());
		}
	}
	
	/**
	 * @return
	 * @throws XMLDBException
	 */
	private XPathQueryService storeXMLStringAndGetQueryService(String documentName,
			String content) throws XMLDBException {
		XMLResource doc =
			(XMLResource) testCollection.createResource(
					documentName, "XMLResource" );
		doc.setContent(content);
		testCollection.storeResource(doc);
		XPathQueryService service =
			(XPathQueryService) testCollection.getService(
				"XPathQueryService",
				"1.0");
		return service;
	}

	/**
	 * @return
	 * @throws XMLDBException
	 */
	private XPathQueryService storeXMLStringAndGetQueryService(String documentName
			) throws XMLDBException {
		XMLResource doc =
			(XMLResource) testCollection.createResource(
					documentName, "XMLResource" );
		doc.setContent(new File(documentName));
		testCollection.storeResource(doc);
		XPathQueryService service =
			(XPathQueryService) testCollection.getService(
				"XPathQueryService",
				"1.0");
		return service;
	}

	/**
	 * @param result
	 * @throws XMLDBException
	 */
	private void printResult(ResourceSet result) throws XMLDBException {
		for (ResourceIterator i = result.getIterator();
			i.hasMoreResources(); ) {
			Resource r = i.nextResource();
			System.out.println(r.getContent());
		}
	}

	public static void main(String[] args) {
		if ( args.length > 0 ) {
			attributeXML = args[0];
		}
		stringSize = 513;
		if ( args.length > 1 ) {
			stringSize = Integer.parseInt( args[1] );
		}
		nbElem = 2;
		if ( args.length > 2 ) {
			nbElem = Integer.parseInt( args[2] );
		}

		junit.textui.TestRunner.run(XQueryTest.class);
	}
}
