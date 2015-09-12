/**
 * Copyright (C) 2015 opendevz (opendevz@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.nibeans.processor;

import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic.Kind;

/**
 * Tracks issues with bean definitions during processing.
 * 
 * @author Tareq Sharafy (tareq.sharafy@sap.com)
 *
 */
public class IssueTracker {
	private final IssueScope rootIssueScope = new IssueScope(null);
	private final Stack<IssueScope> issueScopeStack = new Stack<>();
	private final PrintStream errorOut = System.err;
	private final Messager messager;
	private final Kind messageKind;

	public IssueTracker(Messager messager, boolean isStrict) {
		issueScopeStack.push(rootIssueScope);
		this.messager = messager;
		messageKind = isStrict ? Kind.ERROR : Kind.WARNING;
	}

	public void enterScope(Element scopeElement) {
		Map<Element, IssueScope> parentScopes = issueScopeStack.peek().childScopes;
		IssueScope newScope = parentScopes.get(scopeElement);
		if (newScope == null) {
			newScope = new IssueScope(scopeElement);
			parentScopes.put(scopeElement, newScope);
		}
		issueScopeStack.push(newScope);
	}

	public void leaveScope() {
		IssueScope closedScope = issueScopeStack.pop();
		// Remove if there are no issue
		if (closedScope.issues.isEmpty() && closedScope.childScopes.isEmpty()) {
			issueScopeStack.peek().childScopes.remove(closedScope.element);

		}
	}

	public void addIssue(String format, Object... args) {
		String msg = String.format(format, args);
		issueScopeStack.peek().issues.add(msg);
	}

	public void printIssues() {
		int totalIssueCount = rootIssueScope.getTotalIssueCount();
		if (totalIssueCount == 0) {
			return;
		}
		errorOut.print("some issues were detected while generating bean classes [");
		errorOut.print(totalIssueCount);
		errorOut.println(" issues]");
		printScopeIssues(rootIssueScope, 0, errorOut);
	}

	private void printScopeIssues(IssueScope issueScope, int indent, PrintStream errorOut) {
		// Print the scope element
		if (issueScope.element != null) {
			indent(indent, errorOut);
			errorOut.print("in ");
			errorOut.println(issueScope.element);
		}
		int subIndent = indent + 1;
		for (String issueMsg : issueScope.issues) {
			indent(subIndent, errorOut);
			errorOut.print("# ");
			errorOut.println(issueMsg);
			messager.printMessage(messageKind, issueMsg, issueScope.element);
		}
		for (IssueScope childScope : issueScope.childScopes.values()) {
			printScopeIssues(childScope, subIndent, errorOut);
		}
	}

	private void indent(int n, PrintStream errorOut) {
		for (int i = 0; i < n; ++i) {
			errorOut.print(" ");
		}
	}

	private static class IssueScope {
		public final Element element;
		public final List<String> issues = new LinkedList<>();
		public final Map<Element, IssueScope> childScopes = new LinkedHashMap<>();

		public IssueScope(Element element) {
			this.element = element;
		}

		public int getTotalIssueCount() {
			int c = issues.size();
			for (IssueScope childScope : childScopes.values()) {
				c += childScope.getTotalIssueCount();
			}
			return c;
		}
	}

}
