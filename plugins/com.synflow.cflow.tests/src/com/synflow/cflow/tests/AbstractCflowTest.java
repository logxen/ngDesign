/*******************************************************************************
 * Copyright (c) 2012-2014 Synflow SAS.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Matthieu Wipliez - initial API and implementation and/or initial documentation
 *******************************************************************************/
package com.synflow.cflow.tests;

import static com.synflow.core.ISynflowConstants.FILE_EXT_IR;
import static com.synflow.core.ISynflowConstants.FOLDER_IR;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.URIMappingRegistryImpl;
import org.eclipse.emf.ecore.xml.namespace.XMLNamespacePackage;
import org.eclipse.xtext.generator.IGenerator;
import org.eclipse.xtext.generator.JavaIoFileSystemAccess;
import org.eclipse.xtext.junit4.AbstractXtextTests;
import org.eclipse.xtext.junit4.InjectWith;
import org.eclipse.xtext.junit4.XtextRunner;
import org.eclipse.xtext.junit4.validation.ValidatorTester;
import org.eclipse.xtext.nodemodel.ICompositeNode;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.resource.XtextResourceSet;
import org.eclipse.xtext.validation.AbstractValidationDiagnostic;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.gson.JsonPrimitive;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.synflow.cflow.CflowInjectorProvider;
import com.synflow.cflow.CflowStandaloneSetup;
import com.synflow.cflow.UriComputer;
import com.synflow.cflow.cflow.Module;
import com.synflow.cflow.cflow.NamedEntity;
import com.synflow.cflow.validation.CflowJavaValidator;
import com.synflow.core.SynflowCore;
import com.synflow.models.dpn.Actor;
import com.synflow.models.dpn.DpnPackage;
import com.synflow.models.dpn.Entity;
import com.synflow.models.ir.impl.IrResourceFactoryImpl;
import com.synflow.models.ir.util.IrUtil;

/**
 * This abstract class defines methods to use to test actor code generation.
 * 
 * @author Matthieu Wipliez
 * 
 * @param <T>
 *            type of the top-level object of the AST
 */
@InjectWith(CflowInjectorProvider.class)
@RunWith(XtextRunner.class)
public abstract class AbstractCflowTest extends AbstractXtextTests {

	private static final String CFLOW_PLUGIN_ID = "com.synflow.cflow";

	private static int failed, total;

	public static final String OUTPUT_NAME = "SynflowTest";

	private static void ignore(Object o) {
	}

	@BeforeClass
	public static void init() {
		Map<String, Object> extToFactoryMap = Resource.Factory.Registry.INSTANCE
				.getExtensionToFactoryMap();
		Object instance = extToFactoryMap.get(FILE_EXT_IR);
		if (instance == null) {
			instance = new IrResourceFactoryImpl();
			extToFactoryMap.put(FILE_EXT_IR, instance);
		}

		// initialize packages required for correct operation
		// just referencing a package causes it to initialize itself
		ignore(XMLNamespacePackage.eINSTANCE);
		ignore(DpnPackage.eINSTANCE);

		// maps plugin URIs to absolute file URIs
		map(CFLOW_PLUGIN_ID, "../" + CFLOW_PLUGIN_ID);
		map(SynflowCore.PLUGIN_ID, "../../fragments/com.synflow.libraries");
	}

	private static void map(String id, String relativePath) {
		// from platform:/plugin/id URI
		String name = "/" + id + "/";
		URI from = URI.createPlatformPluginURI(name, true);

		// to file:/ URI absolute path
		Path path = Paths.get(relativePath).toAbsolutePath();
		URI uri = URI.createFileURI(path.normalize().toString() + "/");

		// register mapping
		URIMappingRegistryImpl.INSTANCE.put(from, uri);
	}

	protected static void printFailure(String name, Throwable t) {
		System.err.println("test " + name + " failed");
		t.printStackTrace();
		System.err.flush();
	}

	@AfterClass
	public static void printStats() {
		float total = AbstractCflowTest.total;
		int percent = (int) (100.0f * (total - failed) / total);
		System.out.println();
		System.out
				.println("*******************************************************************************");
		System.out.println(percent + "% tests passed (" + failed + " failed)");
		System.out
				.println("*******************************************************************************");
	}

	protected static void testEnded(int failed, int length) {
		AbstractCflowTest.failed += failed;
		AbstractCflowTest.total += length;

		if (failed != 0) {
			float total = length;
			int percent = (int) (100.0f * (total - failed) / total);
			throw new RuntimeException(percent + "% tests passed (" + failed + " failed)");
		}
	}

	@Inject
	protected JavaIoFileSystemAccess access;

	protected String outputPath;

	@Inject
	private Provider<XtextResourceSet> provider;

	protected XtextResourceSet resourceSet;

	protected ValidatorTester<CflowJavaValidator> tester;

	/**
	 * Asserts that the resource from which the given object was loaded has no parse errors, and no
	 * semantic errors.
	 * 
	 * @param object
	 *            an object (top-level node of the AST)
	 */
	protected final void assertOk(EObject object) {
		assertNotNull(object);
		EList<org.eclipse.emf.ecore.resource.Resource.Diagnostic> errors = object.eResource()
				.getErrors();
		assertTrue("object has errors: " + errors, errors.isEmpty());

		Diagnostic diagnostic = tester.validate(object).getDiagnostic();
		boolean hasErrors = false;
		for (Diagnostic child : diagnostic.getChildren()) {
			if (child.getSeverity() == Diagnostic.ERROR) {
				hasErrors = true;
				if (child instanceof AbstractValidationDiagnostic) {
					AbstractValidationDiagnostic validationDiag = (AbstractValidationDiagnostic) child;
					EObject source = validationDiag.getSourceEObject();
					if (source != null) {
						ICompositeNode node = NodeModelUtils.getNode(source);
						System.err.println("line " + node.getStartLine());
					}
				}
				System.err.println(child.getMessage());
			}
		}
		assertFalse(hasErrors);
	}

	/**
	 * Checks that the actor has the properties specified in its test case.
	 * 
	 * @param actor
	 *            an actor
	 */
	protected final void checkProperties(Actor actor) {
		JsonPrimitive primitive = actor.getProperties().getAsJsonPrimitive("num_states");
		if (primitive != null) {
			int numberOfStates = primitive.getAsInt();
			int actual = actor.hasFsm() ? actor.getFsm().getStates().size() : 1;
			assertEquals(actor.getName(), numberOfStates, actual);
		}

		primitive = actor.getProperties().getAsJsonPrimitive("num_transitions");
		if (primitive != null) {
			int numberOfTransitions = primitive.getAsInt();
			int actual;
			if (actor.hasFsm()) {
				actual = actor.getFsm().getEdges().size();
			} else {
				actual = actor.getActionsOutsideFsm().size();
			}
			assertEquals(actor.getName(), numberOfTransitions, actual);
		}
	}

	/**
	 * Parses, validates, and generates code for the entity defined in the file whose name is given.
	 * 
	 * @param name
	 *            name of a .cf file that contains an entity
	 * @return an IR actor if the file could be parsed, validated, and translated to IR, otherwise
	 *         <code>null</code>
	 */
	protected Iterable<Entity> compileFile(String name) {
		final Module module = getModule(name);
		assertOk(module);

		// runs generator
		IGenerator generator = getInjector().getInstance(IGenerator.class);
		Resource resource = module.eResource();
		generator.doGenerate(resource, access);

		// clears loaded resources to force reloading them from disk
		resourceSet.getResources().clear();

		return Iterables.transform(module.getEntities(), new Function<NamedEntity, Entity>() {
			@Override
			public Entity apply(NamedEntity input) {
				return getEntity(module.getPackage() + "." + input.getName());
			}
		});
	}

	private Entity getEntity(String name) {
		// get URI of .ir generated file
		String path = IrUtil.getFile(name) + "." + FILE_EXT_IR;
		URI uriIr = access.getURI(path);

		// load actor
		Resource irResource = resourceSet.getResource(uriIr, true);
		EObject eObject = irResource.getContents().get(0);
		assertNotNull(eObject);
		return (Entity) eObject;
	}

	/**
	 * Returns the Module of the task with the given name.
	 * 
	 * @param name
	 *            name
	 * @return a Module
	 */
	protected Module getModule(String name) {
		String path = Paths.get("tests", name).toAbsolutePath().toString();
		URI uri = URI.createFileURI(path);
		Resource resource = resourceSet.getResource(uri, true);
		return (Module) resource.getContents().get(0);
	}

	@Override
	public void setUp() throws Exception {
		super.setUp();
		with(CflowStandaloneSetup.class);
		CflowJavaValidator validator = get(CflowJavaValidator.class);
		tester = new ValidatorTester<CflowJavaValidator>(validator, getInjector());

		// set output path
		String tmpDir = System.getProperty("java.io.tmpdir");
		outputPath = Paths.get(tmpDir, OUTPUT_NAME).toString();
		String absoluteOutput = outputPath + "/" + FOLDER_IR;
		access.setOutputPath(absoluteOutput);

		// register URI mapping from source to generated
		String path = Paths.get("tests").toAbsolutePath().toString();
		URI uri = URI.createFileURI(path + "/");
		UriComputer.INSTANCE.getURIMap().put(uri, URI.createFileURI(absoluteOutput + "/"));

		// creates the resource set to use for this test
		resourceSet = provider.get();
		resourceSet.addLoadOption(XtextResource.OPTION_RESOLVE_ALL, Boolean.TRUE);
	}

}
