/*
 * Copyright 2012 The Error Prone Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.errorprone.bugpatterns;

import static com.google.errorprone.BugPattern.SeverityLevel.ERROR;
import static com.google.errorprone.matchers.Matchers.allOf;
import static com.google.errorprone.matchers.Matchers.anyOf;
import static com.google.errorprone.matchers.Matchers.argument;
import static com.google.errorprone.matchers.method.MethodMatchers.instanceMethod;
import static com.google.errorprone.matchers.method.MethodMatchers.staticMethod;

import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker.MethodInvocationTreeMatcher;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** @author mdempsky@google.com (Matthew Dempsky) */
@BugPattern(
    name = "InvalidPatternSyntax",
    summary = "Invalid syntax used for a regular expression",
    severity = ERROR)
public class InvalidPatternSyntax extends BugChecker implements MethodInvocationTreeMatcher {

  private static final String MESSAGE_BASE = "Invalid syntax used for a regular expression: ";

  /* Match string literals that are not valid syntax for regular expressions. */
  private static final Matcher<ExpressionTree> BAD_REGEX_LITERAL =
      new Matcher<ExpressionTree>() {
        @Override
        public boolean matches(ExpressionTree tree, VisitorState state) {
          String value = ASTHelpers.constValue(tree, String.class);
          return value != null && !isValidSyntax(value);
        }

        private boolean isValidSyntax(String regex) {
          // Actually valid, but useless.
          if (".".equals(regex)) {
            return false;
          }
          try {
            Pattern.compile(regex);
            return true;
          } catch (PatternSyntaxException e) {
            return false;
          }
        }
      };

  /*
   * Match invocations to regex-accepting methods with bad string literals.
   *
   * <p>We deliberately omit Pattern.compile itself, as most of its users appear to be either
   * passing e.g. LITERAL flags, deliberately testing the regex compiler, or deliberately
   * using "." as the "vacuously true regex."
   */
  private static final Matcher<MethodInvocationTree> BAD_REGEX_USAGE =
      allOf(
          anyOf(
              instanceMethod()
                  .onExactClass("java.lang.String")
                  .namedAnyOf("matches", "split")
                  .withParameters("java.lang.String"),
              instanceMethod()
                  .onExactClass("java.lang.String")
                  .named("split")
                  .withParameters("java.lang.String", "int"),
              instanceMethod()
                  .onExactClass("java.lang.String")
                  .namedAnyOf("replaceFirst", "replaceAll")
                  .withParameters("java.lang.String", "java.lang.String"),
              staticMethod().onClass("java.util.regex.Pattern").named("matches"),
              staticMethod().onClass("com.google.common.base.Splitter").named("onPattern")),
          argument(0, BAD_REGEX_LITERAL));

  @Override
  public Description matchMethodInvocation(
      MethodInvocationTree methodInvocationTree, VisitorState state) {
    if (!BAD_REGEX_USAGE.matches(methodInvocationTree, state)) {
      return Description.NO_MATCH;
    }

    // TODO: Suggest fixes for more situations.
    Description.Builder descriptionBuilder = buildDescription(methodInvocationTree);
    ExpressionTree arg = methodInvocationTree.getArguments().get(0);
    String value = ASTHelpers.constValue(arg, String.class);
    String reasonInvalid = "";

    if (".".equals(value)) {
      descriptionBuilder.addFix(SuggestedFix.replace(arg, "\"\\\\.\""));
      reasonInvalid = "\".\" is a valid but useless regex";
    } else {
      try {
        Pattern.compile(value);
      } catch (PatternSyntaxException e) {
        reasonInvalid = e.getMessage();
      }
    }

    descriptionBuilder.setMessage(MESSAGE_BASE + reasonInvalid);
    return descriptionBuilder.build();
  }
}
