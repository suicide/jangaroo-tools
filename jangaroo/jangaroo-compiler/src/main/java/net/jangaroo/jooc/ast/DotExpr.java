/*
 * Copyright 2008 CoreMedia AG
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 
 * 
 * Unless required by applicable law or agreed to in writing, 
 * software distributed under the License is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either 
 * express or implied. See the License for the specific language 
 * governing permissions and limitations under the License.
 */

package net.jangaroo.jooc.ast;

import net.jangaroo.jooc.AnalyzeContext;
import net.jangaroo.jooc.JooSymbol;
import net.jangaroo.jooc.Jooc;
import net.jangaroo.jooc.JsWriter;
import net.jangaroo.jooc.Scope;
import net.jangaroo.jooc.ast.AstNode;
import net.jangaroo.jooc.ast.AstVisitor;
import net.jangaroo.jooc.ast.Expr;
import net.jangaroo.jooc.ast.Ide;
import net.jangaroo.jooc.ast.IdeDeclaration;
import net.jangaroo.jooc.ast.PostfixOpExpr;

import java.io.IOException;

/**
 * @author Andreas Gawecki
 * @author Frank Wienberg
 */
public class DotExpr extends PostfixOpExpr {

  private Ide ide;

  public DotExpr(Expr expr, JooSymbol symDot, Ide ide) {
    super(symDot, expr);
    this.setIde(ide);
  }

  @Override
  public void visit(AstVisitor visitor) {
    visitor.visitDotExpr(this);
  }

  public Ide getIde() {
    return ide;
  }

  @Override
  public void scope(final Scope scope) {
    super.scope(scope);
    getIde().scope(scope);
  }

  @Override
  public void analyze(final AstNode parentNode, final AnalyzeContext context) {
    super.analyze(parentNode, context);
    IdeDeclaration qualiferType = getArg().getType();
    if (qualiferType != null) {
      IdeDeclaration memberDeclaration = getArg().getType().resolvePropertyDeclaration(getIde().getName());
      if (memberDeclaration != null && memberDeclaration.isStatic()) {
        throw Jooc.error(getIde().getIde(), "static member used in dynamic context");
      }
      setType(memberDeclaration);
    }

  }

  @Override
  public void generateJsCode(final JsWriter out) throws IOException {
    getArg().generateCode(out);
    Ide.writeMemberAccess(Ide.resolveMember(getArg().getType(), getIde()), getOp(), getIde(), true, out);
  }

  public void setIde(Ide ide) {
    this.ide = ide;
  }
}