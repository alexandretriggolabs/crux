/*
 * Copyright 2009 Sysmap Solutions Software e Consultoria Ltda.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package br.com.sysmap.crux.core.rebind;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import br.com.sysmap.crux.core.client.Crux;
import br.com.sysmap.crux.core.client.controller.Controller;
import br.com.sysmap.crux.core.client.controller.Global;
import br.com.sysmap.crux.core.client.controller.document.invoke.CrossDocument;
import br.com.sysmap.crux.core.client.event.ControllerInvoker;
import br.com.sysmap.crux.core.client.event.CrossDocumentInvoker;
import br.com.sysmap.crux.core.client.event.EventProcessor;
import br.com.sysmap.crux.core.rebind.controller.ControllerProxyCreator;
import br.com.sysmap.crux.core.rebind.module.Modules;
import br.com.sysmap.crux.core.rebind.screen.Screen;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.RunAsyncCallback;
import com.google.gwt.core.ext.GeneratorContext;
import com.google.gwt.core.ext.TreeLogger;
import com.google.gwt.core.ext.typeinfo.JClassType;
import com.google.gwt.user.rebind.ClassSourceFileComposerFactory;
import com.google.gwt.user.rebind.SourceWriter;

/**
 * Generates a RegisteredControllers class.
 * 
 * 
 * @author Thiago da Rosa de Bustamante
 *
 */
public class RegisteredControllersGenerator extends AbstractRegisteredElementsGenerator
{
	
	/**
	 * Generate the class
	 */
	protected void generateClass(TreeLogger logger, GeneratorContext context, JClassType classType, List<Screen> screens) 
	{
		String packageName = classType.getPackage().getName();
		String className = classType.getSimpleSourceName();
		String implClassName = className + "Impl";

		PrintWriter printWriter = context.tryCreate(logger, packageName, implClassName);
		// if printWriter is null, source code has ALREADY been generated, return
		if (printWriter == null) return;

		ClassSourceFileComposerFactory composer = new ClassSourceFileComposerFactory(packageName, implClassName);
		composer.addImport(GWT.class.getName());
		composer.addImport(br.com.sysmap.crux.core.client.screen.Screen.class.getName());
		composer.addImport(RunAsyncCallback.class.getName());
		composer.addImport(EventProcessor.class.getName());
		composer.addImport(Crux.class.getName());
		composer.addImport(ControllerInvoker.class.getName());
		composer.addImport(CrossDocumentInvoker.class.getName());
		
		composer.addImplementedInterface("br.com.sysmap.crux.core.client.event.RegisteredControllers");
		
		SourceWriter sourceWriter = composer.createSourceWriter(context, printWriter);
		sourceWriter.println("private java.util.Map<String, ControllerInvoker> controllers = new java.util.HashMap<String, ControllerInvoker>();");

		Map<String, String> controllerClassNames = new HashMap<String, String>();
		Map<String, String> crossDocsClassNames = new HashMap<String, String>();
		for (Screen screen : screens)
		{
			generateControllersForScreen(logger, sourceWriter, screen, controllerClassNames, crossDocsClassNames, packageName+"."+implClassName, context);
		}

		generateConstructor(sourceWriter, implClassName, controllerClassNames);
		generateValidateControllerMethod(sourceWriter);
		generateControllerInvokeMethod(sourceWriter, controllerClassNames);
		generateCrossDocInvokeMethod(sourceWriter, crossDocsClassNames);
		generateRegisterControllerMethod(sourceWriter); 
		
		
		sourceWriter.outdent();
		sourceWriter.println("}");

		context.commit(logger, printWriter);
	}


	/**
	 * @param sourceWriter
	 */
	private void generateValidateControllerMethod(SourceWriter sourceWriter)
	{
		sourceWriter.println("public boolean __validateController(String controllerId){");
		sourceWriter.println("String[] controllers = Screen.getControllers();");
		sourceWriter.println("for (String c: controllers){");
		sourceWriter.println("if (c.equals(controllerId)){");
		sourceWriter.println("return true;");
		sourceWriter.println("}");
		sourceWriter.println("}");
		sourceWriter.println("return false;");
		sourceWriter.println("}");
	}
	
	/**
	 * @param sourceWriter
	 * @param implClassName
	 * @param controllerClassNames
	 */
	private void generateConstructor(SourceWriter sourceWriter, String implClassName, Map<String, String> controllerClassNames)
	{
		
		sourceWriter.println("public "+implClassName+"(){");
		for (String controller : controllerClassNames.keySet()) 
		{
			Class<?> controllerClass = ClientControllers.getController(controller);
			if (!isControllerLazy(controllerClass))
			{
				Global globalAnnot = controllerClass.getAnnotation(Global.class);
				if (globalAnnot == null)
				{
					sourceWriter.println("if (__validateController(\""+controller+"\"))");
				}
				sourceWriter.println("controllers.put(\""+controller+"\", new " + controllerClassNames.get(controller) + "());");
			}
		}
		sourceWriter.println("}");
	}

	private void generateRegisterControllerMethod(SourceWriter sourceWriter)
    {
		sourceWriter.println("public void registerController(String controller, ControllerInvoker controllerInvoker){");
		sourceWriter.println("if (!controllers.containsKey(controller)){");
		sourceWriter.println("controllers.put(controller, controllerInvoker);");
		sourceWriter.println("}");
		sourceWriter.println("}");
	}
	
	private void generateCrossDocInvokeMethod(SourceWriter sourceWriter, Map<String, String> crossDocsClassNames)
	{
		sourceWriter.println("public String invokeCrossDocument(String serializedData){");
		sourceWriter.indent();

		sourceWriter.println("if (serializedData != null){");
		sourceWriter.indent();

		sourceWriter.println("int idx = serializedData.indexOf('|');");
		sourceWriter.println("if (idx > 0){");
		sourceWriter.indent();
		
		sourceWriter.println("String controllerName = null;");
		sourceWriter.println("try{");
		sourceWriter.indent();
		
		sourceWriter.println("controllerName = serializedData.substring(0,idx);");
		sourceWriter.println("serializedData = serializedData.substring(idx+1);");
		sourceWriter.println("CrossDocumentInvoker crossDoc = (CrossDocumentInvoker)controllers.get(controllerName);");
		sourceWriter.println("if (crossDoc==null){");
		sourceWriter.indent();
		sourceWriter.println("Crux.getErrorHandler().handleError(Crux.getMessages().eventProcessorClientControllerNotFound(controllerName));");
		sourceWriter.println("return null;");
		sourceWriter.outdent();
		sourceWriter.println("} else {");
		sourceWriter.indent();
		
		sourceWriter.println("return crossDoc.invoke(serializedData);");
		
		sourceWriter.outdent();
		sourceWriter.println("}");
		
		sourceWriter.outdent();
		sourceWriter.println("} catch(ClassCastException ex){");
		sourceWriter.indent();
		sourceWriter.println("Crux.getErrorHandler().handleError(Crux.getMessages().crossDocumentInvalidCrossDocumentController(controllerName));");
		sourceWriter.println("return null;");
		sourceWriter.outdent();
		sourceWriter.println("}");
		
		sourceWriter.outdent();
		sourceWriter.println("}");

		sourceWriter.outdent();
		sourceWriter.println("}");
		
		sourceWriter.println("return null;");
		sourceWriter.outdent();
		sourceWriter.println("}");
	}
	
	/**
	 * 
	 * @param sourceWriter
	 * @param controllerClassNames
	 */
	private void generateControllerInvokeMethod(SourceWriter sourceWriter, Map<String, String> controllerClassNames)
	{
		sourceWriter.println("public void invokeController(final String controllerName, final String method, final boolean fromOutOfModule, final Object sourceEvent, final EventProcessor eventProcessor){");
		sourceWriter.println("ControllerInvoker controller = controllers.get(controllerName);");
		sourceWriter.println("if (controller != null){");
		sourceWriter.println("try{");
		sourceWriter.println("controller.invoke(method, sourceEvent, fromOutOfModule, eventProcessor);");
		sourceWriter.println("}");
		sourceWriter.println("catch (Exception e)"); 
		sourceWriter.println("{");
		sourceWriter.println("eventProcessor.setException(e);");
		sourceWriter.println("}");
		sourceWriter.println("return;");
		sourceWriter.println("}");

		boolean first = true;
		for (String controller : controllerClassNames.keySet()) 
		{
			Class<?> controllerClass = ClientControllers.getController(controller);
			Controller controllerAnnot = controllerClass.getAnnotation(Controller.class);
			if (isControllerLazy(controllerClass))
			{
				if (!first)
				{
					sourceWriter.print("else ");
				}
				else
				{
					first = false;
				}
				sourceWriter.println("if (\""+controller+"\".equals(controllerName)){");
				if (controllerAnnot != null && Fragments.getFragmentClass(controllerAnnot.fragment()) != null)
				{
					sourceWriter.println("GWT.runAsync("+Fragments.getFragmentClass(controllerAnnot.fragment())+".class, new RunAsyncCallback(){");
					sourceWriter.println("public void onFailure(Throwable reason){");
					sourceWriter.println("Crux.getErrorHandler().handleError(Crux.getMessages().eventProcessorClientControllerCanNotBeLoaded(controller));");
					sourceWriter.println("}");
					sourceWriter.println("public void onSuccess(){");
					sourceWriter.println("if (!controllers.containsKey(\""+controller+"\")){");
					sourceWriter.println("controllers.put(\""+controller+"\", new " + controllerClassNames.get(controller) + "());");
					sourceWriter.println("}");
					sourceWriter.println("invokeController(controllerName, method, fromOutOfModule, sourceEvent, eventProcessor);");
					sourceWriter.println("}");
					sourceWriter.println("});");
				}
				else
				{
					sourceWriter.println("if (!controllers.containsKey(\""+controller+"\")){");
					sourceWriter.println("controllers.put(\""+controller+"\", new " + controllerClassNames.get(controller) + "());");
					sourceWriter.println("}");
					sourceWriter.println("invokeController(controllerName, method, fromOutOfModule, sourceEvent, eventProcessor);");
				}
				sourceWriter.println("}");
			}
		}
		//TODO - Thiago - aparentemente, se o controller nao existir, nao notifica mais isso pro usuario.... n�o lan�ar exce��o... apenas logar no console
		
		sourceWriter.println("}");
	}


	/**
	 * @param controllerClass
	 * @return true if this controller can be loaded in lazy mode
	 */
	private boolean isControllerLazy(Class<?> controllerClass)
    {
		Controller controllerAnnot = controllerClass.getAnnotation(Controller.class);
	    return (controllerAnnot == null || controllerAnnot.lazy()) && !CrossDocument.class.isAssignableFrom(controllerClass);
    }
	
	/**
	 * generate wrapper classes for event handling.
	 * @param logger
	 * @param sourceWriter
	 * @param screen
	 * @param context 
	 */
	private void generateControllersForScreen(TreeLogger logger, SourceWriter sourceWriter, Screen screen, 
			Map<String, String> controllerClassNames, Map<String, String> crossDocsClassNames, String implClassName, GeneratorContext context)
	{
		Iterator<String> controllers = screen.iterateControllers();
		
		while (controllers.hasNext())
		{
			String controller = controllers.next();
			generateControllerBlock(logger, sourceWriter, controller, controllerClassNames, crossDocsClassNames, context);
		}		

		controllers = ClientControllers.iterateGlobalControllers();
		
		while (controllers.hasNext())
		{
			String controller = controllers.next();
			Class<?> controllerClass = ClientControllers.getController(controller);
			if (controllerClass != null)
			{
				String controllerClassName = getClassSourceName(controllerClass).replace('.', '/');
				if (Modules.getInstance().isClassOnModulePath(controllerClassName, screen.getModule()))
				{
					generateControllerBlock(logger, sourceWriter, controller, controllerClassNames, crossDocsClassNames, context);
				}
			}
		}		
	}

	/**
	 * Generate the block to include controller object.
	 * @param logger
	 * @param sourceWriter
	 * @param controllersAdded
	 * @param context 
	 */
	private void generateControllerBlock(TreeLogger logger, SourceWriter sourceWriter, String controller, 
			Map<String, String> controllersAdded, Map<String, String> crossDocsAdded, GeneratorContext context)
	{
		try
		{
			Class<?> controllerClass = ClientControllers.getController(controller);
			if (!controllersAdded.containsKey(controller) && controllerClass!= null)
			{
				String genClass = new ControllerProxyCreator(logger, context, controllerClass).create();
				controllersAdded.put(controller, genClass);
				if (CrossDocument.class.isAssignableFrom(controllerClass))
				{
					crossDocsAdded.put(controller, genClass);
				}
			}
		}
		catch (Throwable e) 
		{
			logger.log(TreeLogger.ERROR, messages.errorGeneratingRegisteredController(controller, e.getLocalizedMessage()), e);
		}
	}
}